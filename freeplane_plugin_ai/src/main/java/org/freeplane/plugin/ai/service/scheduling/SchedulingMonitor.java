package org.freeplane.plugin.ai.service.scheduling;

import org.freeplane.core.util.LogUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduling monitor for tracking task execution metrics and performance data.
 */
public class SchedulingMonitor {
    // singleton instance
    private static final SchedulingMonitor instance = new SchedulingMonitor();
    
    // task metrics
    private final Map<String, TaskMetrics> taskMetricsByAction;
    private final AtomicLong totalTasks;
    private final AtomicLong completedTasks;
    private final AtomicLong failedTasks;
    private final AtomicLong totalWaitTime;
    private final AtomicLong totalExecutionTime;
    
    // resource utilization
    private final AtomicLong peakConcurrentTasks;
    // Bounded ring buffer using ArrayDeque; removeFirst() is O(1) vs ArrayList.remove(0) O(n)
    private final ArrayDeque<ResourceUsageSnapshot> resourceSnapshots;
    private static final int MAX_RESOURCE_SNAPSHOTS = 100;
    
    // parameter impacts (up to 50 entries per action, also using ArrayDeque)
    private final Map<String, ArrayDeque<ParameterImpact>> parameterImpacts;
    
    private SchedulingMonitor() {
        this.taskMetricsByAction = new ConcurrentHashMap<>();
        this.totalTasks = new AtomicLong(0);
        this.completedTasks = new AtomicLong(0);
        this.failedTasks = new AtomicLong(0);
        this.totalWaitTime = new AtomicLong(0);
        this.totalExecutionTime = new AtomicLong(0);
        this.peakConcurrentTasks = new AtomicLong(0);
        this.resourceSnapshots = new ArrayDeque<>(MAX_RESOURCE_SNAPSHOTS);
        this.parameterImpacts = new ConcurrentHashMap<>();
    }
    
    public static SchedulingMonitor getInstance() {
        return instance;
    }
    
    public void recordTaskStart(BuildTask task) {
        totalTasks.incrementAndGet();
        int concurrentTasks = BuildTaskScheduler.getInstance().getRunningTaskCount();
        peakConcurrentTasks.updateAndGet(current -> Math.max(current, concurrentTasks));
        recordResourceUsage();
    }
    
    public void recordTaskComplete(BuildTask task, boolean success) {
        if (success) {
            completedTasks.incrementAndGet();
        } else {
            failedTasks.incrementAndGet();
        }
        totalWaitTime.addAndGet(task.getWaitTime());
        totalExecutionTime.addAndGet(task.getExecutionTime());
        TaskMetrics metrics = taskMetricsByAction.computeIfAbsent(task.getAction(), k -> new TaskMetrics());
        metrics.recordTask(task, success);
        recordParameterImpact(task, success);
        recordResourceUsage();
    }
    
    private void recordResourceUsage() {
        int queueSize = BuildTaskScheduler.getInstance().getQueueSize();
        int runningTasks = BuildTaskScheduler.getInstance().getRunningTaskCount();
        long timestamp = System.currentTimeMillis();
        ResourceUsageSnapshot snapshot = new ResourceUsageSnapshot(timestamp, queueSize, runningTasks);
        synchronized (resourceSnapshots) {
            resourceSnapshots.addLast(snapshot);
            if (resourceSnapshots.size() > MAX_RESOURCE_SNAPSHOTS) {
                resourceSnapshots.removeFirst(); // O(1)
            }
        }
    }
    
    private static final int MAX_PARAMETER_IMPACTS = 50;

    private void recordParameterImpact(BuildTask task, boolean success) {
        SchedulingConfig config = SchedulingConfig.getInstance();
        String action = task.getAction();
        ParameterImpact impact = new ParameterImpact(
            System.currentTimeMillis(), action,
            config.getTemperature(), config.getTopP(),
            task.getWaitTime(), task.getExecutionTime(), success
        );
        ArrayDeque<ParameterImpact> impacts = parameterImpacts.computeIfAbsent(action, k -> new ArrayDeque<>(MAX_PARAMETER_IMPACTS));
        synchronized (impacts) {
            impacts.addLast(impact);
            if (impacts.size() > MAX_PARAMETER_IMPACTS) {
                impacts.removeFirst(); // O(1)
            }
        }
    }
    
    public SchedulingMetrics getMetrics() {
        SchedulingMetrics metrics = new SchedulingMetrics();
        metrics.setTotalTasks(totalTasks.get());
        metrics.setCompletedTasks(completedTasks.get());
        metrics.setFailedTasks(failedTasks.get());
        metrics.setTotalWaitTime(totalWaitTime.get());
        metrics.setTotalExecutionTime(totalExecutionTime.get());
        metrics.setPeakConcurrentTasks(peakConcurrentTasks.get());
        metrics.setTaskMetricsByAction(new HashMap<>(taskMetricsByAction));
        synchronized (resourceSnapshots) {
            metrics.setRecentResourceSnapshots(new ArrayList<>(resourceSnapshots));
        }
        // snapshot parameterImpacts with per-deque locking
        Map<String, List<ParameterImpact>> impactsCopy = new HashMap<>();
        parameterImpacts.forEach((action, deque) -> {
            synchronized (deque) {
                impactsCopy.put(action, new ArrayList<>(deque));
            }
        });
        metrics.setParameterImpacts(impactsCopy);
        return metrics;
    }
    
    public void resetMetrics() {
        taskMetricsByAction.clear();
        totalTasks.set(0);
        completedTasks.set(0);
        failedTasks.set(0);
        totalWaitTime.set(0);
        totalExecutionTime.set(0);
        peakConcurrentTasks.set(0);
        synchronized (resourceSnapshots) {
            resourceSnapshots.clear();
        }
        parameterImpacts.clear();
        LogUtils.info("SchedulingMonitor: metrics reset");
    }
    
    public void logMetrics() {
        SchedulingMetrics metrics = getMetrics();
        LogUtils.info("Scheduling Monitor Metrics:");
        LogUtils.info("Total tasks: " + metrics.getTotalTasks());
        LogUtils.info("Completed tasks: " + metrics.getCompletedTasks());
        LogUtils.info("Failed tasks: " + metrics.getFailedTasks());
        LogUtils.info("Peak concurrent tasks: " + metrics.getPeakConcurrentTasks());
        if (metrics.getTotalTasks() > 0) {
            LogUtils.info("Avg wait time: " + (metrics.getTotalWaitTime() / metrics.getTotalTasks()) + "ms");
            LogUtils.info("Avg execution time: " + (metrics.getTotalExecutionTime() / metrics.getTotalTasks()) + "ms");
        }
        LogUtils.info("Task metrics by action type:");
        metrics.getTaskMetricsByAction().forEach((action, taskMetrics) -> {
            LogUtils.info("  " + action + ": " + taskMetrics);
        });
    }
    
    public static class SchedulingMetrics {
        private long totalTasks;
        private long completedTasks;
        private long failedTasks;
        private long totalWaitTime;
        private long totalExecutionTime;
        private long peakConcurrentTasks;
        private Map<String, TaskMetrics> taskMetricsByAction;
        private List<ResourceUsageSnapshot> recentResourceSnapshots;
        private Map<String, List<ParameterImpact>> parameterImpacts; // read-only snapshot after copy
        
        public long getTotalTasks() { return totalTasks; }
        public void setTotalTasks(long totalTasks) { this.totalTasks = totalTasks; }
        public long getCompletedTasks() { return completedTasks; }
        public void setCompletedTasks(long completedTasks) { this.completedTasks = completedTasks; }
        public long getFailedTasks() { return failedTasks; }
        public void setFailedTasks(long failedTasks) { this.failedTasks = failedTasks; }
        public long getTotalWaitTime() { return totalWaitTime; }
        public void setTotalWaitTime(long totalWaitTime) { this.totalWaitTime = totalWaitTime; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public void setTotalExecutionTime(long totalExecutionTime) { this.totalExecutionTime = totalExecutionTime; }
        public long getPeakConcurrentTasks() { return peakConcurrentTasks; }
        public void setPeakConcurrentTasks(long peakConcurrentTasks) { this.peakConcurrentTasks = peakConcurrentTasks; }
        public Map<String, TaskMetrics> getTaskMetricsByAction() { return taskMetricsByAction; }
        public void setTaskMetricsByAction(Map<String, TaskMetrics> taskMetricsByAction) { this.taskMetricsByAction = taskMetricsByAction; }
        public List<ResourceUsageSnapshot> getRecentResourceSnapshots() { return recentResourceSnapshots; }
        public void setRecentResourceSnapshots(List<ResourceUsageSnapshot> recentResourceSnapshots) { this.recentResourceSnapshots = recentResourceSnapshots; }
        public Map<String, List<ParameterImpact>> getParameterImpacts() { return parameterImpacts; }
        public void setParameterImpacts(Map<String, List<ParameterImpact>> parameterImpacts) { this.parameterImpacts = parameterImpacts; }
    }
    
    public static class TaskMetrics {
        private long totalTasks;
        private long completedTasks;
        private long failedTasks;
        private long totalWaitTime;
        private long totalExecutionTime;
        
        public void recordTask(BuildTask task, boolean success) {
            totalTasks++;
            if (success) { completedTasks++; } else { failedTasks++; }
            totalWaitTime += task.getWaitTime();
            totalExecutionTime += task.getExecutionTime();
        }
        
        @Override
        public String toString() {
            return "TaskMetrics{totalTasks=" + totalTasks + ", completedTasks=" + completedTasks
                    + ", failedTasks=" + failedTasks
                    + ", avgWaitTime=" + (totalTasks > 0 ? totalWaitTime / totalTasks : 0)
                    + ", avgExecutionTime=" + (totalTasks > 0 ? totalExecutionTime / totalTasks : 0) + '}';
        }
        
        public long getTotalTasks() { return totalTasks; }
        public long getCompletedTasks() { return completedTasks; }
        public long getFailedTasks() { return failedTasks; }
        public long getTotalWaitTime() { return totalWaitTime; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
    }
    
    public static class ResourceUsageSnapshot {
        private final long timestamp;
        private final int queueSize;
        private final int runningTasks;
        
        public ResourceUsageSnapshot(long timestamp, int queueSize, int runningTasks) {
            this.timestamp = timestamp;
            this.queueSize = queueSize;
            this.runningTasks = runningTasks;
        }
        
        public long getTimestamp() { return timestamp; }
        public int getQueueSize() { return queueSize; }
        public int getRunningTasks() { return runningTasks; }
        
        @Override
        public String toString() {
            return "ResourceUsageSnapshot{timestamp=" + timestamp + ", queueSize=" + queueSize
                    + ", runningTasks=" + runningTasks + '}';
        }
    }
    
    public static class ParameterImpact {
        private final long timestamp;
        private final String action;
        private final double temperature;
        private final double topP;
        private final long waitTime;
        private final long executionTime;
        private final boolean success;
        
        public ParameterImpact(long timestamp, String action, double temperature, double topP,
                               long waitTime, long executionTime, boolean success) {
            this.timestamp = timestamp;
            this.action = action;
            this.temperature = temperature;
            this.topP = topP;
            this.waitTime = waitTime;
            this.executionTime = executionTime;
            this.success = success;
        }
        
        public long getTimestamp() { return timestamp; }
        public String getAction() { return action; }
        public double getTemperature() { return temperature; }
        public double getTopP() { return topP; }
        public long getWaitTime() { return waitTime; }
        public long getExecutionTime() { return executionTime; }
        public boolean isSuccess() { return success; }
        
        @Override
        public String toString() {
            return "ParameterImpact{action='" + action + "', temperature=" + temperature
                    + ", topP=" + topP + ", waitTime=" + waitTime
                    + ", executionTime=" + executionTime + ", success=" + success + '}';
        }
    }
}
