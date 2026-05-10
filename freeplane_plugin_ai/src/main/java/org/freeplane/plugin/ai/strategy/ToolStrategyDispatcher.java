package org.freeplane.plugin.ai.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.freeplane.core.util.LogUtils;

/**
 * Tool execution strategy dispatcher.
 *
 * <p>Iterates all registered strategies in priority order and delegates to
 * the first one that supports the given tool name and parameters.
 *
 * <p>Usage:
 * <pre>{@code
 * ToolStrategyDispatcher dispatcher = new ToolStrategyDispatcher();
 * dispatcher.registerStrategy(new GreedyLocalSearchStrategy());
 * dispatcher.registerStrategy(new IntervalDPStrategy());
 *
 * Object result = dispatcher.dispatch("createNodes", parameters);
 * }</pre>
 *
 * @author AI Plugin Team
 * @since 1.13.x
 */
public class ToolStrategyDispatcher {
    
    private final List<ToolExecutionStrategy> strategies = new ArrayList<>();
    
    /**
     * Registers a strategy. Strategies are kept sorted by priority (lower value = higher priority).
     *
     * @param strategy the strategy to register
     */
    public void registerStrategy(ToolExecutionStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy");
        strategies.add(strategy);
        // sort by priority (lower number = higher priority)
        Collections.sort(strategies, Comparator.comparingInt(ToolExecutionStrategy::getPriority));
        LogUtils.info("Registered strategy: " + strategy.getStrategyName() + 
                      " (priority=" + strategy.getPriority() + ")");
    }
    
    /**
     * Unregisters a strategy by name.
     *
     * @param strategyName the strategy name
     * @return {@code true} if the strategy was found and removed
     */
    public boolean unregisterStrategy(String strategyName) {
        boolean removed = strategies.removeIf(s -> s.getStrategyName().equals(strategyName));
        if (removed) {
            LogUtils.info("Unregistered strategy: " + strategyName);
        }
        return removed;
    }
    
    /**
     * Dispatches a tool-call request to the appropriate strategy.
     *
     * @param toolName   the tool name
     * @param parameters the tool parameters
     * @return the strategy execution result
     * @throws UnsupportedOperationException if no registered strategy supports the request
     */
    public Object dispatch(String toolName, Map<String, Object> parameters) {
        for (ToolExecutionStrategy strategy : strategies) {
            if (strategy.supports(toolName, parameters)) {
                LogUtils.info("Selected strategy: " + strategy.getStrategyName() + 
                              " for tool: " + toolName);
                return strategy.execute(toolName, parameters);
            }
        }
        
        throw new UnsupportedOperationException(
            "No strategy supports tool: " + toolName + 
            " with parameters: " + parameters);
    }
    
    /**
     * Returns an unmodifiable view of all registered strategies.
     *
     * @return the strategy list
     */
    public List<ToolExecutionStrategy> getStrategies() {
        return Collections.unmodifiableList(strategies);
    }
    
    /**
     * Returns the number of registered strategies.
     *
     * @return the strategy count
     */
    public int getStrategyCount() {
        return strategies.size();
    }
}
