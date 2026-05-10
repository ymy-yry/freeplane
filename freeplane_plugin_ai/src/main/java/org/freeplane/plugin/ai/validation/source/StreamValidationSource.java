package org.freeplane.plugin.ai.validation.source;

import java.io.IOException;

/**
 * Streaming aggregation data source for build pipelines.
 * Accepts SSE chunks incrementally and triggers validation once the stream is marked complete.
 *
 * <p>Key design: validation is triggered at stream end, not on every chunk.
 */
public final class StreamValidationSource implements ValidationSource {
    
    private final StringBuilder buffer = new StringBuilder();
    private final String description;
    private volatile boolean completed = false;
    
    /**
     * @param description stream description, e.g. "build-stream" or a node ID
     */
    public StreamValidationSource(String description) {
        this.description = description;
    }
    
    /**
     * Appends a streaming chunk.
     *
     * @param chunk SSE chunk content
     */
    public synchronized void append(String chunk) {
        if (completed) {
            throw new IllegalStateException("Stream already completed");
        }
        buffer.append(chunk);
    }
    
    /**
     * Marks the stream as fully assembled.
     */
    public synchronized void markComplete() {
        this.completed = true;
    }
    
    @Override
    public synchronized String readContent() throws IOException {
        if (!completed) {
            throw new IllegalStateException(
                "Stream not completed yet. Current buffer length: " + buffer.length());
        }
        return buffer.toString();
    }
    
    @Override
    public SourceType getSourceType() {
        return SourceType.STREAM_ASSEMBLED;
    }
    
    @Override
    public boolean isReady() {
        return completed;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
}
