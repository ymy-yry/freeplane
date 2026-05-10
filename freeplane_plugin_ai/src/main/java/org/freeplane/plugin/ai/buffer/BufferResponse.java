package org.freeplane.plugin.ai.buffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Buffer-layer response object.
 * Contains the processing result, quality score, optimisation logs, and other metadata.
 */
public class BufferResponse {

    /** Whether the processing succeeded. */
    private boolean success;

    /** Processing result data. */
    private Map<String, Object> data;

    /** The model that was used. */
    private String usedModel;

    /** Quality score (0-100). */
    private double qualityScore;

    /** Processing time in milliseconds. */
    private long processingTime;

    /** Processing log entries. */
    private List<String> logs;

    /** Error message (if any). */
    private String errorMessage;

    public BufferResponse() {
        this.success = false;
        this.data = new HashMap<>();
        this.logs = new ArrayList<>();
        this.qualityScore = 0.0;
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getUsedModel() {
        return usedModel;
    }

    public void setUsedModel(String usedModel) {
        this.usedModel = usedModel;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Appends a log entry.
     */
    public void addLog(String log) {
        this.logs.add(log);
    }

    /**
     * Puts a key-value pair into the result data map.
     */
    public void putData(String key, Object value) {
        this.data.put(key, value);
    }

    /**
     * Builds a successful response.
     */
    public static BufferResponse success(Map<String, Object> data) {
        BufferResponse response = new BufferResponse();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    /**
     * Builds a failed response.
     */
    public static BufferResponse error(String errorMessage) {
        BufferResponse response = new BufferResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }

    @Override
    public String toString() {
        return "BufferResponse{" +
                "success=" + success +
                ", usedModel='" + usedModel + '\'' +
                ", qualityScore=" + qualityScore +
                ", processingTime=" + processingTime +
                ", logs=" + logs +
                '}';
    }
}