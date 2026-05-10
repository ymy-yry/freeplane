package org.freeplane.plugin.ai.buffer;

/**
 * Standard interface for intelligent buffer layers.
 * All buffer-layer implementations must follow this interface to ensure
 * pluggability and consistency.
 */
public interface IBufferLayer {

    /**
     * Returns the name of this buffer layer.
     * @return the buffer layer identifier
     */
    String getName();

    /**
     * Returns whether this layer can handle the given request.
     * @param request the request context
     * @return {@code true} if the layer can handle it
     */
    boolean canHandle(BufferRequest request);

    /**
     * Processes the request and returns a response.
     * @param request the request context
     * @return the processing result
     */
    BufferResponse process(BufferRequest request);

    /**
     * Returns the priority of this buffer layer (lower number = higher priority).
     * @return priority value
     */
    default int getPriority() {
        return 100;
    }
}