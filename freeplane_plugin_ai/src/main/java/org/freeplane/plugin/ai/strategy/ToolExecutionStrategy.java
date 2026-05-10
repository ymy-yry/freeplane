package org.freeplane.plugin.ai.strategy;

import java.util.Map;

/**
 * Tool execution strategy interface.
 * 
 * <p>Core interface of the Strategy pattern, defining a unified contract for different
 * tool execution strategies. Each strategy decides whether it supports the given request
 * based on parameter characteristics, and then executes the corresponding optimisation algorithm.
 * 
 * <p>Design principles:
 * <ul>
 *   <li>Open/Closed Principle: open for extension (add new strategies), closed for modification</li>
 *   <li>Single Responsibility: each strategy handles exactly one optimisation scenario</li>
 *   <li>Dependency Inversion: higher-level modules depend on this interface, not concrete implementations</li>
 * </ul>
 * 
 * @author AI Plugin Team
 * @since 1.13.x
 */
public interface ToolExecutionStrategy {
    
    /**
     * Returns {@code true} if this strategy supports the current tool-call request.
     * 
     * @param toolName   the tool name (e.g. "createNodes", "edit")
     * @param parameters the tool-call parameters
     * @return {@code true} if this strategy can handle the request
     */
    boolean supports(String toolName, Map<String, Object> parameters);
    
    /**
     * Executes the strategy optimisation logic.
     * 
     * @param toolName   the tool name
     * @param parameters the tool-call parameters
     * @return the optimised tool-call plan
     */
    Object execute(String toolName, Map<String, Object> parameters);
    
    /**
     * Returns the strategy priority (lower value = higher priority).
     * 
     * <p>Recommended ranges:
     * <ul>
     *   <li>1–10: core business strategies (e.g. greedy)</li>
     *   <li>11–20: auxiliary business strategies (e.g. interval DP)</li>
     *   <li>21–30: optimisation strategies (e.g. Union-Find + LCA)</li>
     *   <li>31–40: fault-tolerance / fallback strategies</li>
     *   <li>90–100: last-resort fallback strategies</li>
     * </ul>
     * 
     * @return the priority value
     */
    int getPriority();
    
    /**
     * Returns the strategy name (used for logging and monitoring).
     * 
     * @return the strategy name
     */
    String getStrategyName();
}
