package org.freeplane.plugin.ai.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interval dynamic-programming strategy — optimises batch processing of sibling nodes.
 * 
 * <p>Algorithm:
 * <p>For a sequence of sibling nodes under the same parent, interval DP finds the optimal
 * batch-processing plan.
 * 
 * <p>State definition:
 * <pre>
 * dp[i][j] = minimum cost to process the sibling-node interval [i, j]
 * </pre>
 * 
 * <p>Recurrence:
 * <pre>
 * dp[i][j] = min{
 *   dp[i][k] + dp[k+1][j],           // split point k
 *   cost(batchTool(i, j))            // use batch tool for the entire interval
 * }
 * where i ≤ k < j
 * </pre>
 * 
 * <p>Complexity:
 * <ul>
 *   <li>Time: O(n³), n = number of sibling nodes</li>
 *   <li>Space: O(n²)</li>
 *   <li>Practical limit: n ≤ 50</li>
 * </ul>
 * 
 * @author AI Plugin Team
 * @since 1.13.x
 */
public class IntervalDPStrategy implements ToolExecutionStrategy {
    
    private static final int MAX_SIBLING_COUNT = 50;
    
    @Override
    public boolean supports(String toolName, Map<String, Object> parameters) {
        // supports batch-create and batch-edit
        if (!"createNodes".equals(toolName) && !"edit".equals(toolName)) {
            return false;
        }
        
        // check sibling node count
        int siblingCount = getSiblingCount(parameters);
        return siblingCount >= 3 && siblingCount <= MAX_SIBLING_COUNT;
    }
    
    @Override
    public Object execute(String toolName, Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();
        
        List<SiblingNode> siblings = getSiblingNodes(parameters);
        int n = siblings.size();
        
        // DP table: dp[i][j] = minimum cost to process interval [i, j]
        long[][] dp = new long[n][n];
        int[][] splitPoint = new int[n][n];
        
        // initialise: single node
        for (int i = 0; i < n; i++) {
            dp[i][i] = siblings.get(i).getIndividualCost();
            splitPoint[i][i] = -1; // -1 = no split
        }
        
        // interval DP
        for (int len = 2; len <= n; len++) {          // interval length
            for (int i = 0; i <= n - len; i++) {      // left endpoint
                int j = i + len - 1;                   // right endpoint
                dp[i][j] = Long.MAX_VALUE;
                
                // enumerate split points
                for (int k = i; k < j; k++) {
                    long cost = dp[i][k] + dp[k+1][j];
                    if (cost < dp[i][j]) {
                        dp[i][j] = cost;
                        splitPoint[i][j] = k;
                    }
                }
                
                // try batch-tool processing
                long batchCost = computeBatchCost(siblings.subList(i, j+1));
                if (batchCost < dp[i][j]) {
                    dp[i][j] = batchCost;
                    splitPoint[i][j] = -2; // -2 = batch processing
                }
            }
        }
        
        // backtrack to build the plan
        List<BatchOperation> operations = reconstructSolution(siblings, splitPoint, 0, n-1);
        
        long elapsed = System.currentTimeMillis() - startTime;
        double totalCost = dp[0][n-1];
        
        // Build optimised result
        List<OptimizedToolCall.ToolCallStep> steps = buildToolCallSteps(operations, parameters);
        
        return new OptimizedToolCall(
            "IntervalDP",
            steps,
            elapsed,
            totalCost
        );
    }
    
    @Override
    public int getPriority() {
        return StrategyPriority.INTERVAL_DP;
    }
    
    @Override
    public String getStrategyName() {
        return "IntervalDP";
    }
    
    /**
     * Computes the cost of processing a set of nodes with the batch tool.
     * <p>Batch tools typically offer a discount: cost = base_cost * log(n)
     */
    private long computeBatchCost(List<SiblingNode> nodes) {
        if (nodes.isEmpty()) {
            return 0;
        }
        
        // Assume the batch tool's base cost is 1.5× the average per-node cost
        long sumCost = 0;
        for (SiblingNode node : nodes) {
            sumCost += node.getIndividualCost();
        }
        long avgCost = sumCost / nodes.size();
        
        // batch discount: log2(n)
        double discount = Math.log(nodes.size()) / Math.log(2);
        return (long) (avgCost * 1.5 * discount);
    }
    
    /**
     * Backtracks through the split-point table to reconstruct the optimal plan.
     */
    private List<BatchOperation> reconstructSolution(
            List<SiblingNode> siblings, int[][] splitPoint, int i, int j) {
        
        List<BatchOperation> operations = new ArrayList<>();
        
        if (i > j) {
            return operations;
        }
        
        int split = splitPoint[i][j];
        
        if (split == -1) {
            // single node
            operations.add(new BatchOperation(
                BatchOperation.Type.SINGLE,
                siblings.subList(i, i+1)
            ));
        } else if (split == -2) {
            // batch processing
            operations.add(new BatchOperation(
                BatchOperation.Type.BATCH,
                siblings.subList(i, j+1)
            ));
        } else {
            // split point: recursively handle left and right sub-intervals
            operations.addAll(reconstructSolution(siblings, splitPoint, i, split));
            operations.addAll(reconstructSolution(siblings, splitPoint, split+1, j));
        }
        
        return operations;
    }
    
    /**
     * Builds the tool-call step list.
     */
    private List<OptimizedToolCall.ToolCallStep> buildToolCallSteps(
            List<BatchOperation> operations, Map<String, Object> parameters) {
        
        List<OptimizedToolCall.ToolCallStep> steps = new ArrayList<>();
        int order = 1;
        
        for (BatchOperation op : operations) {
            Map<String, Object> stepParams = Map.of(
                "operationType", op.getType().name(),
                "nodeCount", op.getNodes().size(),
                "nodeIds", op.getNodeIds()
            );
            
            steps.add(new OptimizedToolCall.ToolCallStep(
                "batchProcess",
                stepParams,
                order++
            ));
        }
        
        return steps;
    }
    
    // ========== Helper methods ==========
    
    private int getSiblingCount(Map<String, Object> parameters) {
        Object count = parameters.get("siblingCount");
        return count != null ? (Integer) count : 0;
    }
    
    @SuppressWarnings("unchecked")
    private List<SiblingNode> getSiblingNodes(Map<String, Object> parameters) {
        List<SiblingNode> nodes = (List<SiblingNode>) parameters.get("siblingNodes");
        return nodes != null ? nodes : createMockSiblingNodes(10);
    }
    
    private List<SiblingNode> createMockSiblingNodes(int count) {
        List<SiblingNode> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            nodes.add(new SiblingNode(
                "sibling_" + i,
                10 + i * 2  // individual cost
            ));
        }
        return nodes;
    }
    
    /** Sibling node. */
    public static class SiblingNode {
        private final String nodeId;
        private final long individualCost;
        
        public SiblingNode(String nodeId, long individualCost) {
            this.nodeId = nodeId;
            this.individualCost = individualCost;
        }
        
        public String getNodeId() { return nodeId; }
        public long getIndividualCost() { return individualCost; }
    }
    
    /** Batch operation. */
    public static class BatchOperation {
        public enum Type { SINGLE, BATCH }
        
        private final Type type;
        private final List<SiblingNode> nodes;
        
        public BatchOperation(Type type, List<SiblingNode> nodes) {
            this.type = type;
            this.nodes = nodes;
        }
        
        public Type getType() { return type; }
        public List<SiblingNode> getNodes() { return nodes; }
        
        public List<String> getNodeIds() {
            List<String> ids = new ArrayList<>();
            for (SiblingNode node : nodes) {
                ids.add(node.getNodeId());
            }
            return ids;
        }
    }
}
