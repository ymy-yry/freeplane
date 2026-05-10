package org.freeplane.plugin.ai.service.scheduling;

import org.freeplane.core.util.LogUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Build task scheduler - primary scheduler responsible for task queuing and selection logic.
 */
public class BuildTaskScheduler {
    // singleton instance
    private static volatile BuildTaskScheduler instance;

    // task queue - LinkedBlockingQueue instead of PriorityBlockingQueue
    // because we need dynamic TopP-based task selection
    private final BlockingQueue<BuildTask> taskQueue;
    // currently running tasks
    private final Set<BuildTask> runningTasks;
    // executor thread pool (exposed internally for BuildTaskExecutor reuse)
    private final ExecutorService executorService;
    // scheduler thread pool
    private final ScheduledExecutorService schedulerService;
    // scheduling configuration
    private final SchedulingConfig config;
    // task executor
    private final BuildTaskExecutor taskExecutor;
    // random number generator
    private final Random random;
    // task timeout in seconds: AI operations take longer, default 120 s
    private static final long TASK_TIMEOUT_SECONDS = 120;

    // running state
    private volatile boolean running;
    // task counter
    private final AtomicLong taskCounter;

    /**
     * Private constructor - initializes scheduler components.
     */
    private BuildTaskScheduler() {
        this.taskQueue = new LinkedBlockingQueue<>(100);
        this.runningTasks = ConcurrentHashMap.newKeySet();
        this.executorService = Executors.newFixedThreadPool(2);
        this.schedulerService = Executors.newScheduledThreadPool(1);
        this.config = SchedulingConfig.getInstance();
        this.taskExecutor = BuildTaskExecutor.getInstance();
        this.running = false;
        this.taskCounter = new AtomicLong(0);

        long seed = config.getSeed() != null ? config.getSeed() : System.nanoTime();
        this.random = new Random(seed);
    }

    /**
     * Returns the scheduler singleton instance.
     * @return scheduler instance
     */
    public static BuildTaskScheduler getInstance() {
        if (instance == null) {
            synchronized (BuildTaskScheduler.class) {
                if (instance == null) {
                    instance = new BuildTaskScheduler();
                }
            }
        }
        return instance;
    }

    /**
     * Resets the scheduler singleton instance.
     */
    public static void resetInstance() {
        synchronized (BuildTaskScheduler.class) {
            if (instance != null) {
                instance.stop();
                instance = null;
            }
        }
    }

    /**
     * Starts the scheduler.
     */
    public void start() {
        if (!running) {
            running = true;
            schedulerService.scheduleWithFixedDelay(this::processTasks, 0, 100, TimeUnit.MILLISECONDS);
            LogUtils.info("BuildTaskScheduler: started, config: temperature=" + config.getTemperature() + ", topP=" + config.getTopP());
        }
    }

    /**
     * Stops the scheduler.
     */
    public void stop() {
        running = false;
        schedulerService.shutdown();
        executorService.shutdown();
        try {
            if (!schedulerService.awaitTermination(5, TimeUnit.SECONDS)) {
                schedulerService.shutdownNow();
            }
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            schedulerService.shutdownNow();
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LogUtils.info("BuildTaskScheduler: stopped");
    }

    /**
     * Submits a task to the scheduler.
     * @param action action type
     * @param request request parameters
     * @return the task object; if the queue is full, returns a task with CANCELLED status
     */
    public BuildTask submitTask(String action, Map<String, Object> request) {
        BuildTask task = new BuildTask(action, request, taskCounter.incrementAndGet());
        boolean offered = taskQueue.offer(task);
        if (offered) {
            LogUtils.info("BuildTaskScheduler: task submitted: " + task.getId() + ", action: " + action + ", queueSize: " + taskQueue.size());
        } else {
            // queue full: mark as CANCELLED and surface error via future instead of silently dropping
            task.setStatus(BuildTask.TaskStatus.CANCELLED);
            task.getFuture().complete(org.freeplane.plugin.ai.service.AIServiceResponse.error("Scheduler queue is full, please retry later"));
            LogUtils.warn("BuildTaskScheduler: task submission failed, queue is full");
        }
        return task;
    }

    /**
     * Processes the task queue - selects a task using the scheduling algorithm.
     */
    private void processTasks() {
        if (!running) {
            return;
        }

        if (runningTasks.size() >= 2) {
            return;
        }

        if (taskQueue.isEmpty()) {
            return;
        }

        // select the next task using the scheduling algorithm
        BuildTask nextTask = selectNextTask();
        if (nextTask == null) {
            return;
        }

        if (runningTasks.contains(nextTask)) {
            return;
        }

        runningTasks.add(nextTask);

        executorService.submit(() -> {
            try {
                nextTask.setStatus(BuildTask.TaskStatus.RUNNING);
                LogUtils.info("BuildTaskScheduler: executing task: " + nextTask.getId());

                taskExecutor.executeTask(nextTask);

                nextTask.setStatus(BuildTask.TaskStatus.COMPLETED);
                LogUtils.info("BuildTaskScheduler: task completed: " + nextTask.getId());
            } catch (Exception e) {
                nextTask.setStatus(BuildTask.TaskStatus.FAILED);
                if (!nextTask.getFuture().isDone()) {
                    nextTask.getFuture().complete(
                        org.freeplane.plugin.ai.service.AIServiceResponse.error("Task execution failed: " + e.getMessage()));
                }
                LogUtils.warn("BuildTaskScheduler: task failed: " + nextTask.getId(), e);
            } finally {
                runningTasks.remove(nextTask);
            }
        });

        // timeout watchdog: forcefully terminate the task if future is not done within TASK_TIMEOUT_SECONDS
        schedulerService.schedule(() -> {
            if (!nextTask.getFuture().isDone()) {
                nextTask.setStatus(BuildTask.TaskStatus.FAILED);
                nextTask.getFuture().complete(
                    org.freeplane.plugin.ai.service.AIServiceResponse.error("Task timed out (" + TASK_TIMEOUT_SECONDS + "s)"));
                runningTasks.remove(nextTask);
                LogUtils.warn("BuildTaskScheduler: task timed out and was forcefully terminated: " + nextTask.getId());
            }
        }, TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Selects the next task to execute using the scheduling algorithm.
     * @return the selected task, or null if no tasks are available
     */
    private BuildTask selectNextTask() {
        if (taskQueue.isEmpty()) {
            return null;
        }

        // drain all pending tasks from the queue
        List<BuildTask> pendingTasks = new ArrayList<>();
        int drainCount = taskQueue.drainTo(pendingTasks);
        
        if (pendingTasks.isEmpty()) {
            return null;
        }

        // select a task using the TopP selection algorithm
        BuildTask selectedTask = selectTaskByTopP(pendingTasks);

        // put unselected tasks back into the queue
        if (selectedTask != null) {
            pendingTasks.remove(selectedTask);
            for (BuildTask task : pendingTasks) {
                taskQueue.offer(task);
            }
        }

        return selectedTask;
    }

    /**
     * Returns the internal executor thread pool (for reuse by BuildTaskExecutor).
     * @return executor thread pool
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Returns the current queue size.
     * @return queue size
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * Returns the number of currently running tasks.
     * @return running task count
     */
    public int getRunningTaskCount() {
        return runningTasks.size();
    }

    /**
     * Returns the list of pending tasks.
     * @return pending task list
     */
    public List<BuildTask> getPendingTasks() {
        return new ArrayList<>(taskQueue);
    }

    /**
     * Returns the list of currently running tasks.
     * @return running task list
     */
    public List<BuildTask> getRunningTasks() {
        return new ArrayList<>(runningTasks);
    }

    /**
     * Clears the task queue.
     */
    public void clearQueue() {
        taskQueue.clear();
        LogUtils.info("BuildTaskScheduler: queue cleared");
    }

    /**
     * Calculates the task priority score - no debug logging for performance.
     * @param task the task object
     * @return priority score
     */
    private double calculateTaskPriority(BuildTask task) {
        double temperature = config.getTemperature();

        double baseScore = task.getPriority();
        double ageScore = Math.min(task.getWaitTime() / 1000.0, 5.0);

        double randomFactor = 0;
        if (temperature > 0) {
            randomFactor = (random.nextDouble() - 0.5) * 2 * temperature;
        }

        return baseScore + ageScore + randomFactor;
    }

    /**
     * Selects a task using TopP sampling - each task's priority is computed once to ensure
     * consistency between sorting and sampling.
     * @param tasks list of candidate tasks
     * @return the selected task
     */
    public BuildTask selectTaskByTopP(List<BuildTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }

        double topP = config.getTopP();

        // compute each task's priority score once to avoid inconsistent random values across calls
        Map<BuildTask, Double> scoreCache = new java.util.IdentityHashMap<>();
        for (BuildTask task : tasks) {
            scoreCache.put(task, calculateTaskPriority(task));
        }

        // sort by cached score descending (comparator uses fixed scores, satisfies transitivity)
        tasks.sort((t1, t2) -> Double.compare(scoreCache.get(t2), scoreCache.get(t1)));

        if (topP >= 1.0 || tasks.size() == 1) {
            return tasks.get(0);
        }

        // compute nucleus size
        int nucleusSize = Math.max(1, (int) Math.ceil(tasks.size() * topP));
        nucleusSize = Math.min(nucleusSize, tasks.size());

        List<BuildTask> nucleusTasks = tasks.subList(0, nucleusSize);

        // compute the total priority score of the nucleus (using cached scores)
        double totalScore = 0;
        for (BuildTask task : nucleusTasks) {
            totalScore += scoreCache.get(task);
        }

        if (totalScore <= 0) {
            return nucleusTasks.get(0);
        }

        // proportionally sample from the nucleus using a random value (cached scores, consistent with sort)
        double randomValue = random.nextDouble() * totalScore;
        double cumulative = 0;

        for (BuildTask task : nucleusTasks) {
            cumulative += scoreCache.get(task);
            if (randomValue <= cumulative) {
                return task;
            }
        }

        // fallback: return the first task
        return nucleusTasks.get(0);
    }

    /**
     * Returns whether the scheduler is currently running.
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }
}