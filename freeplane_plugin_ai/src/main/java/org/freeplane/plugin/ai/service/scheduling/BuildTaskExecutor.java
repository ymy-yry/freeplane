package org.freeplane.plugin.ai.service.scheduling;

import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.service.AIServiceResponse;
import org.freeplane.plugin.ai.service.impl.DefaultAgentService;

import java.util.concurrent.ExecutorService;

/**
 * Build task executor - executes build tasks and returns results via CompletableFuture.
 *
 * Important: must call dispatchAction rather than processRequest during execution;
 * otherwise the scheduler's thread pool threads will re-submit tasks and wait for a future,
 * causing a deadlock.
 */
public class BuildTaskExecutor {
    // singleton instance
    private static final BuildTaskExecutor instance = new BuildTaskExecutor();
    // agent service
    private final DefaultAgentService agentService;
    
    /**
     * Private constructor - initializes executor components.
     */
    private BuildTaskExecutor() {
        this.agentService = new DefaultAgentService();
    }
    
    /**
     * Returns the executor singleton instance.
     * @return executor instance
     */
    public static BuildTaskExecutor getInstance() {
        return instance;
    }
    
    /**
     * Executes a task and completes the result via task.getFuture().
     *
     * Should be called from within the scheduler's thread pool.
     * Calls dispatchAction directly rather than processRequest to avoid re-entering the
     * scheduling path (deadlock prevention).
     * @param task the task to execute
     * @return service response
     */
    public AIServiceResponse executeTask(BuildTask task) {
        LogUtils.info("BuildTaskExecutor: executing task: " + task);
        try {
            // ensure the agent is initialized (chatModel, toolSet, etc.)
            agentService.ensureAgentInitializedPublic();
            // call the underlying dispatch method directly, bypassing the scheduler entry point
            AIServiceResponse response = agentService.dispatchAction(task.getAction(), task.getRequest());
            if (response.isSuccess()) {
                LogUtils.info("BuildTaskExecutor: task succeeded: " + task.getId());
            } else {
                LogUtils.warn("BuildTaskExecutor: task failed: " + task.getId() + ", error: " + response.getErrorMessage());
            }
            // return the result to the waiting caller via future
            task.getFuture().complete(response);
            return response;
        } catch (Exception e) {
            LogUtils.warn("BuildTaskExecutor: task threw exception: " + task.getId(), e);
            AIServiceResponse errResp = AIServiceResponse.error("Task execution failed: " + e.getMessage());
            task.getFuture().complete(errResp);
            throw new RuntimeException("Task execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a task asynchronously, reusing the provided thread pool instead of creating a new bare thread.
     * @param task the task to execute
     * @param executorService thread pool (should be the scheduler's internal pool)
     * @param callback async callback (optional)
     */
    public void executeTaskAsync(BuildTask task, ExecutorService executorService, TaskExecutionCallback callback) {
        executorService.submit(() -> {
            try {
                AIServiceResponse response = executeTask(task);
                if (callback != null) callback.onComplete(task, response);
            } catch (Exception e) {
                if (callback != null) callback.onError(task, e);
            }
        });
    }

    /**
     * @deprecated Use executeTaskAsync(task, executorService, callback) instead.
     * Retained for backward compatibility only; will be removed in a future release.
     */
    @Deprecated
    public void executeTaskAsync(BuildTask task, TaskExecutionCallback callback) {
        BuildTaskScheduler scheduler = BuildTaskScheduler.getInstance();
        ExecutorService es = scheduler.getExecutorService();
        executeTaskAsync(task, es, callback);
    }
    
    /**
     * Task execution callback interface.
     */
    public interface TaskExecutionCallback {
        void onComplete(BuildTask task, AIServiceResponse response);
        void onError(BuildTask task, Exception error);
    }
}
