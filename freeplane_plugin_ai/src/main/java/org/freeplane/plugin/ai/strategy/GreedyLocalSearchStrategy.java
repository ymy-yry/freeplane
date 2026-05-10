package org.freeplane.plugin.ai.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Greedy + local-search optimisation strategy for tool calls.
 * 
 * <p>Algorithm:
 * <ol>
 *   <li>Greedy phase: sort by value density (covered nodes / cost) and select tools greedily</li>
 *   <li>Local-search phase: improve the solution via Swap neighbourhood search (try replacing tools to lower cost)</li>
 * </ol>
 * 
 * <p>Complexity:
 * <ul>
 *   <li>Time: O(n·log n + k·n²), n = tool count, k = local-search iterations</li>
 *   <li>Space: O(n)</li>
 *   <li>Approximation ratio: O(log n)</li>
 * </ul>
 * 
 * <p>Suitable when:
 * <ul>
 *   <li>Number of tools ≤ 50 (greedy approximation is good)</li>
 *   <li>Fast response required (&lt; 10 ms)</li>
 *   <li>Map depth ≤ 10 levels</li>
 * </ul>
 * 
 * @author AI Plugin Team
 * @since 1.13.x
 */
public class GreedyLocalSearchStrategy implements ToolExecutionStrategy {
    
    private static final int MAX_LOCAL_SEARCH_ITERATIONS = 10;
    
    @Override
    public boolean supports(String toolName, Map<String, Object> parameters) {
        // supports mind-map generation and node expansion scenarios
        if (!"createNodes".equals(toolName) && !"readNodesWithDescendants".equals(toolName)) {
            return false;
        }
        
        // use greedy when tool count is moderate (≤ 50 tools)
        int toolCount = getToolCount(parameters);
        return toolCount <= 50;
    }
    
    @Override
    public Object execute(String toolName, Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();
        
        // 1. greedy selection
        List<ToolProfile> selectedTools = greedySelect(parameters);
        
        // 2. local-search refinement
        selectedTools = localSearchOptimize(selectedTools, parameters);
        
        long elapsed = System.currentTimeMillis() - startTime;
        double cost = computeTotalCost(selectedTools);
        
        // 3. Build optimised result
        List<OptimizedToolCall.ToolCallStep> steps = buildToolCallSteps(selectedTools, parameters);
        
        return new OptimizedToolCall(
            "Greedy+LocalSearch",
            steps,
            elapsed,
            cost
        );
    }
    
    @Override
    public int getPriority() {
        return StrategyPriority.GREEDY_OPTIMIZATION;
    }
    
    @Override
    public String getStrategyName() {
        return "GreedyLocalSearch";
    }
    
    /**
     * Greedy selection: sorts tools by value density.
     */
    private List<ToolProfile> greedySelect(Map<String, Object> parameters) {
        List<ToolProfile> availableTools = getAvailableTools(parameters);
        Set<String> requiredNodes = getRequiredNodes(parameters);
        
        List<ToolProfile> selected = new ArrayList<>();
        Set<String> coveredNodes = new HashSet<>();
        
        // sort by value density (descending)
        availableTools.sort((t1, t2) -> {
            double density1 = computeValueDensity(t1, requiredNodes);
            double density2 = computeValueDensity(t2, requiredNodes);
            return Double.compare(density2, density1); // descending
        });
        
        // greedy selection
        for (ToolProfile tool : availableTools) {
            if (coveredNodes.containsAll(requiredNodes)) {
                break; // all required nodes already covered
            }
            
            // check if this tool covers any not-yet-covered nodes
            Set<String> toolCoverage = tool.getCoveredNodes();
            boolean hasNewCoverage = false;
            for (String node : toolCoverage) {
                if (requiredNodes.contains(node) && !coveredNodes.contains(node)) {
                    hasNewCoverage = true;
                    break;
                }
            }
            
            if (hasNewCoverage) {
                selected.add(tool);
                coveredNodes.addAll(toolCoverage);
            }
        }
        
        return selected;
    }
    
    /**
     * Local-search refinement: Swap neighbourhood.
     */
    private List<ToolProfile> localSearchOptimize(List<ToolProfile> currentSolution, 
                                                    Map<String, Object> parameters) {
        List<ToolProfile> availableTools = getAvailableTools(parameters);
        Set<String> requiredNodes = getRequiredNodes(parameters);
        
        List<ToolProfile> bestSolution = new ArrayList<>(currentSolution);
        double bestCost = computeTotalCost(bestSolution);
        
        // local-search iterations
        for (int iter = 0; iter < MAX_LOCAL_SEARCH_ITERATIONS; iter++) {
            boolean improved = false;
            
            // try Swap: remove one tool and add another
            for (int i = 0; i < bestSolution.size(); i++) {
                ToolProfile removeTool = bestSolution.get(i);
                
                for (ToolProfile addTool : availableTools) {
                    if (bestSolution.contains(addTool)) {
                        continue; // already in solution
                    }
                    
                    // generate candidate solution
                    List<ToolProfile> candidate = new ArrayList<>(bestSolution);
                    candidate.remove(i);
                    candidate.add(addTool);
                    
                    // check that all nodes are still covered
                    if (!coversAllNodes(candidate, requiredNodes)) {
                        continue;
                    }
                    
                    // compute cost
                    double candidateCost = computeTotalCost(candidate);
                    
                    // accept if improved
                    if (candidateCost < bestCost) {
                        bestSolution = candidate;
                        bestCost = candidateCost;
                        improved = true;
                        break;
                    }
                }
                
                if (improved) {
                    break;
                }
            }
            
            // terminate early if no improvement found
            if (!improved) {
                break;
            }
        }
        
        return bestSolution;
    }
    
    /**
     * Computes value density = (covered node count × priority weight) / cost.
     * 
     * <p>Priority weights are based on actual performance data (see ToolPerformanceProfile):
     * <ul>
     *   <li>Tree-structure operations: weight 1.0 (95–100, call first)</li>
     *   <li>Style operations: weight 0.94 (88–94)</li>
     *   <li>Selection/navigation: weight 0.90 (85–90)</li>
     *   <li>Search operations: weight 0.87 (80–88)</li>
     *   <li>Filter operations: weight 0.81 (75–82)</li>
     *   <li>Formula evaluation: weight 0.72 (65–75, avoid high-frequency calls)</li>
     *   <li>Export operations: weight 0.67 (60–70)</li>
     *   <li>Bulk operations: weight 0.62 (55–65, slowest)</li>
     * </ul>
     */
    private double computeValueDensity(ToolProfile tool, Set<String> requiredNodes) {
        int coveredCount = 0;
        for (String node : tool.getCoveredNodes()) {
            if (requiredNodes.contains(node)) {
                coveredCount++;
            }
        }
        
        if (coveredCount == 0) {
            return 0;
        }
        
        // get the tool's priority weight (normalised to 0–1)
        double priorityWeight = ToolPerformanceProfile.getPriority(tool.getName()) / 100.0;
        
        double cost = tool.getTimeCost() + tool.getSpaceCost();
        
        // value density = covered-node count × priority weight / cost
        return (coveredCount * priorityWeight) / cost;
    }
    
    /**
     * Returns {@code true} if the given tool set covers all required nodes.
     */
    private boolean coversAllNodes(List<ToolProfile> tools, Set<String> requiredNodes) {
        Set<String> covered = new HashSet<>();
        for (ToolProfile tool : tools) {
            for (String node : tool.getCoveredNodes()) {
                if (requiredNodes.contains(node)) {
                    covered.add(node);
                }
            }
        }
        return covered.containsAll(requiredNodes);
    }
    
    /**
     * Computes the total cost of the selected tools.
     */
    private double computeTotalCost(List<ToolProfile> tools) {
        double totalCost = 0;
        for (ToolProfile tool : tools) {
            totalCost += tool.getTimeCost() + tool.getSpaceCost();
        }
        return totalCost;
    }
    
    /**
     * Builds the tool-call step list.
     */
    private List<OptimizedToolCall.ToolCallStep> buildToolCallSteps(
            List<ToolProfile> tools, Map<String, Object> parameters) {
        List<OptimizedToolCall.ToolCallStep> steps = new ArrayList<>();
        
        for (int i = 0; i < tools.size(); i++) {
            ToolProfile tool = tools.get(i);
            steps.add(new OptimizedToolCall.ToolCallStep(
                tool.getName(),
                parameters,
                i + 1
            ));
        }
        
        return steps;
    }
    
    // ========== Helper methods (extract from parameters) ==========
    
    private int getToolCount(Map<String, Object> parameters) {
        // simplified: retrieve tool count from parameters
        Object count = parameters.get("availableToolCount");
        return count != null ? (Integer) count : 14; // default: 14 tools
    }
    
    private List<ToolProfile> getAvailableTools(Map<String, Object> parameters) {
        // simplified: return mock tool list
        @SuppressWarnings("unchecked")
        List<ToolProfile> tools = (List<ToolProfile>) parameters.get("availableTools");
        return tools != null ? tools : createMockTools();
    }
    
    private Set<String> getRequiredNodes(Map<String, Object> parameters) {
        @SuppressWarnings("unchecked")
        Set<String> nodes = (Set<String>) parameters.get("requiredNodes");
        return nodes != null ? nodes : Set.of("node1", "node2", "node3");
    }
    
    private List<ToolProfile> createMockTools() {
        List<ToolProfile> tools = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            tools.add(new ToolProfile(
                "tool_" + i,
                10 + i * 5,  // time cost
                5 + i * 2,   // space cost
                Set.of("node" + (i + 1))
            ));
        }
        return tools;
    }
    
    /** Tool profile (simplified). */
    public static class ToolProfile {
        private final String name;
        private final long timeCost;
        private final long spaceCost;
        private final Set<String> coveredNodes;
        
        public ToolProfile(String name, long timeCost, long spaceCost, Set<String> coveredNodes) {
            this.name = name;
            this.timeCost = timeCost;
            this.spaceCost = spaceCost;
            this.coveredNodes = coveredNodes;
        }
        
        public String getName() { return name; }
        public long getTimeCost() { return timeCost; }
        public long getSpaceCost() { return spaceCost; }
        public Set<String> getCoveredNodes() { return coveredNodes; }
    }
}
