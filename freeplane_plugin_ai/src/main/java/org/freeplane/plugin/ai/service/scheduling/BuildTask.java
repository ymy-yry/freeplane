package org.freeplane.plugin.ai.service.scheduling;

import org.freeplane.plugin.ai.service.AIServiceResponse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Build task - represents a build task with priority and metadata.
 */
public class BuildTask {
    // task ID
    private final String id;
    // action type
    private final String action;
    // request parameters
    private final Map<String, Object> request;
    // creation timestamp
    private final long createdAt;
    // sequence number
    private final long sequenceNumber;
    // base priority
    private final int priority;
    // task status
    private TaskStatus status;
    // start timestamp
    private long startedAt;
    // completion timestamp
    private long completedAt;
    // priority score
    private double priorityScore;
    // async result: the scheduler completes this future to return the result to the caller
    private final CompletableFuture<AIServiceResponse> future;

    /**
     * Constructs a task object.
     * @param action action type
     * @param request request parameters
     */
    public BuildTask(String action, Map<String, Object> request) {
        this(action, request, 0L);
    }

    /**
     * Constructs a task object.
     * @param action action type
     * @param request request parameters
     * @param sequenceNumber sequence number
     */
    public BuildTask(String action, Map<String, Object> request, long sequenceNumber) {
        this.id = UUID.randomUUID().toString();
        this.action = action;
        this.request = request;
        this.createdAt = System.currentTimeMillis();
        this.sequenceNumber = sequenceNumber;
        this.status = TaskStatus.PENDING;
        this.priority = calculateBasePriority(action);
        this.priorityScore = priority;
        this.future = new CompletableFuture<>();
    }

    /**
     * Calculates the base priority for an action.
     * @param action action type
     * @return base priority (1-5)
     */
    private int calculateBasePriority(String action) {
        switch (action) {
            case "generate-mindmap":
                return 5;
            case "expand-node":
                return 4;
            case "summarize":
                return 3;
            case "tag":
                return 2;
            case "execute-tool":
                return 1;
            default:
                return 0;
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getRequest() {
        return request;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public int getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Sets the task status.
     * @param status task status
     */
    public void setStatus(TaskStatus status) {
        this.status = status;
        if (status == TaskStatus.RUNNING) {
            this.startedAt = System.currentTimeMillis();
        } else if (status == TaskStatus.COMPLETED) {
            this.completedAt = System.currentTimeMillis();
        }
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    public void setPriorityScore(double priorityScore) {
        this.priorityScore = priorityScore;
    }

    /**
     * Returns the async result Future. Callers can wait for completion via future.get(timeout).
     * @return CompletableFuture
     */
    public CompletableFuture<AIServiceResponse> getFuture() {
        return future;
    }

    /**
     * Returns the time the task has been waiting.
     * @return wait time in milliseconds
     */
    public long getWaitTime() {
        if (status == TaskStatus.PENDING) {
            return System.currentTimeMillis() - createdAt;
        } else if (status == TaskStatus.RUNNING) {
            return startedAt - createdAt;
        } else {
            return startedAt - createdAt;
        }
    }

    /**
     * Returns the task execution time.
     * @return execution time in milliseconds
     */
    public long getExecutionTime() {
        if (status == TaskStatus.COMPLETED) {
            return completedAt - startedAt;
        } else if (status == TaskStatus.RUNNING) {
            return System.currentTimeMillis() - startedAt;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return "BuildTask{" +
                "id='" + id + '\'' +
                ", action='" + action + '\'' +
                ", status=" + status +
                ", priority=" + priority +
                ", priorityScore=" + priorityScore +
                '}';
    }

    /**
     * Task status enum.
     */
    public enum TaskStatus {
        PENDING,    // waiting to be executed
        RUNNING,    // currently executing
        COMPLETED,  // finished successfully
        FAILED,     // execution failed
        CANCELLED   // cancelled (queue full, timeout, or explicit cancellation)
    }
}
