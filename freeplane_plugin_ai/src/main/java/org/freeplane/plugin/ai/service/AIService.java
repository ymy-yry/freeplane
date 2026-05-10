package org.freeplane.plugin.ai.service;

import java.util.Map;

/**
 * Unified AI service interface.
 * All AI service providers must implement this interface.
 */
public interface AIService {

    /**
     * Returns the service type.
     * @return service type
     */
    AIServiceType getServiceType();

    /**
     * Returns the service name.
     * @return service name
     */
    String getServiceName();

    /**
     * Processes a request.
     * @param request request parameters
     * @return processing result
     */
    AIServiceResponse processRequest(Map<String, Object> request);

    /**
     * Returns whether this service can handle the given request.
     * @param request request parameters
     * @return true if this service can handle the request
     */
    boolean canHandle(Map<String, Object> request);

    /**
     * Returns the service priority (lower number = higher priority).
     * @return priority
     */
    default int getPriority() {
        return 100;
    }
}