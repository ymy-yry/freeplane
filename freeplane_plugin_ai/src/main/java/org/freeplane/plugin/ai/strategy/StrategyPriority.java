package org.freeplane.plugin.ai.strategy;

/**
 * Strategy priority constants.
 * 
 * <p>Defines priority ranges for each strategy type to ensure deterministic strategy selection.
 * 
 * @author AI Plugin Team
 * @since 1.13.x
 */
public final class StrategyPriority {
    
    /**
     * Core optimisation strategy (greedy + local search).
     * <p>Highest priority; suitable for the majority of scenarios.
     */
    public static final int GREEDY_OPTIMIZATION = 5;
    
    /**
     * Interval dynamic-programming strategy.
     * <p>Suitable for batch processing of sibling nodes.
     */
    public static final int INTERVAL_DP = 10;
    
    /**
     * Union-Find + LCA optimisation strategy.
     * <p>Suitable for eliminating redundant tool calls.
     */
    public static final int UNION_FIND_LCA = 15;
    
    /**
     * Unbounded knapsack dynamic-programming strategy.
     * <p>Suitable for optimal tool selection under resource constraints.
     */
    public static final int KNAPSACK_DP = 20;
    
    /**
     * Fault-tolerance / degraded fallback strategy.
     * <p>Triggered on failure.
     */
    public static final int FALLBACK = 40;
    
    /**
     * Last-resort fallback strategy.
     * <p>Lowest priority; used when no other strategy matches.
     */
    public static final int DEFAULT = 100;
    
    private StrategyPriority() {
        // prevent instantiation
    }
}
