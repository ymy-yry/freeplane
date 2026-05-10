package org.freeplane.plugin.ai.strategy;

import java.util.List;
import java.util.Map;

/**
 * Optimised tool-call plan.
 * 
 * <p>Encapsulates the tool-call sequence and metadata produced by strategy optimisation.
 * 
 * @author AI Plugin Team
 * @since 1.13.x
 */
public class OptimizedToolCall {
    
    private final String strategyName;
    private final List<ToolCallStep> steps;
    private final long optimizationTimeMs;
    private final double estimatedCost;
    
    public OptimizedToolCall(String strategyName, List<ToolCallStep> steps, 
                             long optimizationTimeMs, double estimatedCost) {
        this.strategyName = strategyName;
        this.steps = steps;
        this.optimizationTimeMs = optimizationTimeMs;
        this.estimatedCost = estimatedCost;
    }
    
    public String getStrategyName() {
        return strategyName;
    }
    
    public List<ToolCallStep> getSteps() {
        return steps;
    }
    
    public long getOptimizationTimeMs() {
        return optimizationTimeMs;
    }
    
    public double getEstimatedCost() {
        return estimatedCost;
    }
    
    public int getStepCount() {
        return steps.size();
    }
    
    @Override
    public String toString() {
        return "OptimizedToolCall{" +
               "strategy='" + strategyName + '\'' +
               ", steps=" + steps.size() +
               ", time=" + optimizationTimeMs + "ms" +
               ", cost=" + estimatedCost +
               '}';
    }
    
    /** Tool-call step. */
    public static class ToolCallStep {
        private final String toolName;
        private final Map<String, Object> parameters;
        private final int order;
        
        public ToolCallStep(String toolName, Map<String, Object> parameters, int order) {
            this.toolName = toolName;
            this.parameters = parameters;
            this.order = order;
        }
        
        public String getToolName() {
            return toolName;
        }
        
        public Map<String, Object> getParameters() {
            return parameters;
        }
        
        public int getOrder() {
            return order;
        }
    }
}
