package org.freeplane.plugin.ai.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unbounded knapsack dynamic-programming strategy — optimal tool selection under resource constraints.
 * 
 * <p>Algorithm:
 * <p>Selects the optimal combination of tools to maximise value (node coverage) subject to
 * time/space budget constraints.
 * 
 * <p>State definition:
 * <pre>
 * dp[t][s] = maximum value achievable with time t and space s
 * </pre>
 * 
 * <p>Recurrence:
 * <pre>
 * dp[t][s] = max{ dp[t - time[i]][s - space[i]] + value[i] }
 * </pre>
 * 
 * <p>Complexity:
 * <ul>
 *   <li>Time: O(|F|·T·S), where |F| = number of tools, T = time budget, S = space budget</li>
 *   <li>Space: O(T·S)</li>
 *   <li>Practical limit: T·S ≤ 10^6</li>
 * </ul>
 * 
 * @author AI Plugin Team
 * @since 1.13.x
 */
public class KnapsackDPStrategy implements ToolExecutionStrategy {
    
    private static final int MAX_BUDGET_PRODUCT = 1_000_000;
    
    @Override
    public boolean supports(String toolName, Map<String, Object> parameters) {
        // supports all tools that require resource optimisation
        if (!"createNodes".equals(toolName) && !"edit".equals(toolName) && 
            !"readNodesWithDescendants".equals(toolName)) {
            return false;
        }
        
        // check if budget constraints are tight
        long timeBudget = getTimeBudget(parameters);
        long spaceBudget = getSpaceBudget(parameters);
        long product = timeBudget * spaceBudget;
        
        return product <= MAX_BUDGET_PRODUCT && product > 0;
    }
    
    @Override
    public Object execute(String toolName, Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();
        
        List<KnapsackTool> tools = getKnapsackTools(parameters);
        int timeBudget = (int) Math.min(getTimeBudget(parameters), 1000);
        int spaceBudget = (int) Math.min(getSpaceBudget(parameters), 1000);
        
        // 2D knapsack DP
        long[][] dp = new long[timeBudget + 1][spaceBudget + 1];
        Choice[][] choice = new Choice[timeBudget + 1][spaceBudget + 1];
        
        // initialisation
        for (int t = 0; t <= timeBudget; t++) {
            for (int s = 0; s <= spaceBudget; s++) {
                dp[t][s] = 0;
                choice[t][s] = null;
            }
        }
        
        // unbounded knapsack DP (the same tool may be chosen multiple times)
        for (KnapsackTool tool : tools) {
            int timeCost = tool.getTimeCost();
            int spaceCost = tool.getSpaceCost();
            
            // use actual priority as value weight (rather than raw node coverage count)
            // higher priority = greater value = more likely to be selected
            int priorityWeight = ToolPerformanceProfile.getPriority(tool.getName());
            int value = tool.getValue() * priorityWeight / 100;  // normalised value
            
            // unbounded knapsack: forward traversal
            for (int t = timeCost; t <= timeBudget; t++) {
                for (int s = spaceCost; s <= spaceBudget; s++) {
                    long newValue = dp[t - timeCost][s - spaceCost] + value;
                    
                    if (newValue > dp[t][s]) {
                        dp[t][s] = newValue;
                        choice[t][s] = new Choice(tool, t - timeCost, s - spaceCost);
                    }
                }
            }
        }
        
        // backtrack to find optimal combination
        List<KnapsackTool> selectedTools = reconstructSolution(choice, timeBudget, spaceBudget);
        
        long elapsed = System.currentTimeMillis() - startTime;
        double totalCost = computeTotalCost(selectedTools);
        
        // Build optimised result
        List<OptimizedToolCall.ToolCallStep> steps = buildToolCallSteps(selectedTools, parameters);
        
        return new OptimizedToolCall(
            "KnapsackDP",
            steps,
            elapsed,
            totalCost
        );
    }
    
    @Override
    public int getPriority() {
        return StrategyPriority.KNAPSACK_DP;
    }
    
    @Override
    public String getStrategyName() {
        return "KnapsackDP";
    }
    
    /**
     * Reconstructs the optimal solution by backtracking through the choice table.
     */
    private List<KnapsackTool> reconstructSolution(Choice[][] choice, int timeBudget, int spaceBudget) {
        List<KnapsackTool> selected = new ArrayList<>();
        
        int t = timeBudget;
        int s = spaceBudget;
        
        while (t > 0 && s > 0 && choice[t][s] != null) {
            Choice ch = choice[t][s];
            selected.add(ch.getTool());
            
            t = ch.getPrevTime();
            s = ch.getPrevSpace();
        }
        
        return selected;
    }
    
    /**
     * Computes the total cost of the selected tools.
     */
    private double computeTotalCost(List<KnapsackTool> tools) {
        double totalCost = 0;
        for (KnapsackTool tool : tools) {
            totalCost += tool.getTimeCost() + tool.getSpaceCost();
        }
        return totalCost;
    }
    
    /**
     * Builds the tool-call step list.
     */
    private List<OptimizedToolCall.ToolCallStep> buildToolCallSteps(
            List<KnapsackTool> tools, Map<String, Object> parameters) {
        
        List<OptimizedToolCall.ToolCallStep> steps = new ArrayList<>();
        int order = 1;
        
        for (KnapsackTool tool : tools) {
            Map<String, Object> stepParams = Map.of(
                "toolName", tool.getName(),
                "timeCost", tool.getTimeCost(),
                "spaceCost", tool.getSpaceCost(),
                "value", tool.getValue()
            );
            
            steps.add(new OptimizedToolCall.ToolCallStep(
                "knapsackOptimizedCall",
                stepParams,
                order++
            ));
        }
        
        return steps;
    }
    
    // ========== Helper methods ==========
    
    private long getTimeBudget(Map<String, Object> parameters) {
        Object budget = parameters.get("timeBudget");
        return budget != null ? (Long) budget : 5000L; // default 5 seconds
    }
    
    private long getSpaceBudget(Map<String, Object> parameters) {
        Object budget = parameters.get("spaceBudget");
        return budget != null ? (Long) budget : 256L; // default 256 MB
    }
    
    @SuppressWarnings("unchecked")
    private List<KnapsackTool> getKnapsackTools(Map<String, Object> parameters) {
        List<KnapsackTool> tools = (List<KnapsackTool>) parameters.get("knapsackTools");
        return tools != null ? tools : createMockKnapsackTools();
    }
    
    private List<KnapsackTool> createMockKnapsackTools() {
        List<KnapsackTool> tools = new ArrayList<>();
        
        // create 14 mock tools
        for (int i = 0; i < 14; i++) {
            int timeCost = 10 + i * 5;
            int spaceCost = 5 + i * 2;
            int value = 20 - i; // tool 0 has the highest value
            
            tools.add(new KnapsackTool(
                "tool_" + i,
                timeCost,
                spaceCost,
                value
            ));
        }
        
        return tools;
    }
    
    /** Knapsack tool item. */
    public static class KnapsackTool {
        private final String name;
        private final int timeCost;
        private final int spaceCost;
        private final int value;
        
        public KnapsackTool(String name, int timeCost, int spaceCost, int value) {
            this.name = name;
            this.timeCost = timeCost;
            this.spaceCost = spaceCost;
            this.value = value;
        }
        
        public String getName() { return name; }
        public int getTimeCost() { return timeCost; }
        public int getSpaceCost() { return spaceCost; }
        public int getValue() { return value; }
    }
    
    /** Choice record (used for backtracking). */
    private static class Choice {
        private final KnapsackTool tool;
        private final int prevTime;
        private final int prevSpace;
        
        public Choice(KnapsackTool tool, int prevTime, int prevSpace) {
            this.tool = tool;
            this.prevTime = prevTime;
            this.prevSpace = prevSpace;
        }
        
        public KnapsackTool getTool() { return tool; }
        public int getPrevTime() { return prevTime; }
        public int getPrevSpace() { return prevSpace; }
    }
}
