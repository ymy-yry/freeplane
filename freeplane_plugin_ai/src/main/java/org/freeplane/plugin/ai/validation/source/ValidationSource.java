package org.freeplane.plugin.ai.validation.source;

import java.io.IOException;

/**
 * Proxy interface for validation data sources, providing a unified entry point for
 * multi-origin JSON data.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@link #readContent()} is the single exit point returning the full JSON string</li>
 *   <li>{@link #getSourceType()} enumerates the origin type for logging and fallback routing</li>
 *   <li>{@link #isReady()} checks whether data is fully assembled in streaming scenarios</li>
 *   <li>{@link #getDescription()} provides traceability information for logs</li>
 * </ul>
 *
 * <p>The proxy design keeps the architecture loosely coupled: cycle-detection logic (e.g. SnakeDigestGraph)
 * is fully decoupled from the data origin.
 */
public interface ValidationSource {
    
    /**
     * Reads the full JSON content.
     *
     * @return the full JSON string
     * @throws IOException if reading fails
     * @throws IllegalStateException if called before the data is ready
     */
    String readContent() throws IOException;
    
    /**
     * Returns the data source type.
     *
     * @return the {@link SourceType} enum value
     */
    SourceType getSourceType();
    
    /**
     * Returns whether the data is ready to be read.
     *
     * @return {@code true} if the data is complete and available
     */
    boolean isReady();
    
    /**
     * Returns a human-readable description of the data source for log tracing.
     *
     * @return description string, e.g. "model=ernie-4.0"
     */
    String getDescription();
}
