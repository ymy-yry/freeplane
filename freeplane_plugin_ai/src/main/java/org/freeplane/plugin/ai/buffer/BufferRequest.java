package org.freeplane.plugin.ai.buffer;

import java.util.HashMap;
import java.util.Map;

/**
 * Buffer-layer request context object.
 * A data container passed between buffer-layer components.
 */
public class BufferRequest {

    /** Raw user input. */
    private String userInput;

    /** Request type (auto-generate / expand / summarise, etc.). */
    private RequestType requestType;

    /** Additional parameters. */
    private Map<String, Object> parameters;

    /** Request timestamp. */
    private long timestamp;

    public enum RequestType {
        MINDMAP_GENERATION,   // mindmap generation
        NODE_EXPANSION,       // node expansion
        BRANCH_SUMMARY,       // branch summarisation
        AUTO_TAGGING,         // automatic tagging
        GENERAL_CHAT          // general chat
    }

    public BufferRequest() {
        this.timestamp = System.currentTimeMillis();
        this.parameters = new HashMap<>();
    }

    public BufferRequest(String userInput) {
        this();
        this.userInput = userInput;
    }

    // Getters and Setters

    public String getUserInput() {
        return userInput;
    }

    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Adds a parameter.
     */
    public void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }

    /**
     * Returns a parameter value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key) {
        return (T) this.parameters.get(key);
    }

    /**
     * Returns a parameter value, with a default fallback.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = this.parameters.get(key);
        return value != null ? (T) value : defaultValue;
    }

    @Override
    public String toString() {
        return "BufferRequest{" +
                "userInput='" + userInput + '\'' +
                ", requestType=" + requestType +
                ", parameters=" + parameters +
                '}';
    }
}