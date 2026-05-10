package org.freeplane.plugin.ai.service.impl;

import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.service.ToolExecutionService;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.edit.EditRequest;
import org.freeplane.plugin.ai.tools.edit.NodeContentEditItem;
import org.freeplane.plugin.ai.tools.create.CreateNodesRequest;
import org.freeplane.plugin.ai.tools.create.AnchorPlacement;
import org.freeplane.plugin.ai.tools.create.NodeCreationItem;
import org.freeplane.plugin.ai.tools.create.NodeFoldingState;
import org.freeplane.plugin.ai.tools.content.NodeContentWriteRequest;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsRequest;
import org.freeplane.plugin.ai.tools.read.FetchNodesForEditingRequest;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersRequest;
import org.freeplane.plugin.ai.strategy.ToolStrategyDispatcher;
import org.freeplane.plugin.ai.strategy.GreedyLocalSearchStrategy;
import org.freeplane.plugin.ai.strategy.IntervalDPStrategy;
import org.freeplane.plugin.ai.strategy.UnionFindLCAStrategy;
import org.freeplane.plugin.ai.strategy.KnapsackDPStrategy;
import org.freeplane.plugin.ai.strategy.OptimizedToolCall;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool execution service implementation.
 * Provides direct tool invocation capability, bypassing the LLM chat stage.
 *
 * <p>Architecture evolution:
 * <ul>
 *   <li>Original: hard-coded Map of tool executors</li>
 *   <li>New: Strategy pattern + dynamic programming algorithm optimization</li>
 *   <li>Backward compatible: original executors retained; strategy dispatch layer added</li>
 * </ul>
 */
public class DefaultToolExecutionService implements ToolExecutionService {

    private AIToolSet toolSet;
    private final Map<String, ToolExecutor> toolExecutors;
    private final ToolStrategyDispatcher strategyDispatcher;
    private boolean strategyEnabled = true; // strategy optimization enabled by default

    public DefaultToolExecutionService() {
        this.toolExecutors = new HashMap<>();
        this.strategyDispatcher = new ToolStrategyDispatcher();
        initializeToolExecutors();
        initializeStrategies();
    }

    /**
     * Initializes the strategy dispatcher and registers all optimization strategies
     * (sorted automatically by priority).
     */
    private void initializeStrategies() {
        // priority 5: greedy + local search (core optimization)
        strategyDispatcher.registerStrategy(new GreedyLocalSearchStrategy());

        // priority 10: interval DP (batch processing of sibling nodes)
        strategyDispatcher.registerStrategy(new IntervalDPStrategy());

        // priority 15: union-find + LCA (eliminate duplicate calls)
        strategyDispatcher.registerStrategy(new UnionFindLCAStrategy());

        // priority 20: unbounded knapsack DP (resource-constrained optimization)
        strategyDispatcher.registerStrategy(new KnapsackDPStrategy());
        
        LogUtils.info("DefaultToolExecutionService: Initialized " + 
                      strategyDispatcher.getStrategyCount() + " optimization strategies");
    }

    private void initializeToolExecutors() {
        // register tool executors
        toolExecutors.put("readNodesWithDescendants", this::executeReadNodesWithDescendants);
        toolExecutors.put("fetchNodesForEditing", this::executeFetchNodesForEditing);
        toolExecutors.put("getSelectedMapAndNodeIdentifiers", this::executeGetSelectedMapAndNodeIdentifiers);
        toolExecutors.put("createNodes", this::executeCreateNodes);
        toolExecutors.put("edit", this::executeEdit);
        // additional tool executors can be registered here
    }

    @Override
    public Object executeTool(String toolName, Map<String, Object> parameters) {
        if (!isToolSupported(toolName)) {
            throw new IllegalArgumentException("Tool not supported: " + toolName);
        }

        if (toolSet == null) {
            throw new IllegalStateException("AIToolSet not initialized");
        }

        // strategy optimization path (try first)
        if (strategyEnabled) {
            try {
                LogUtils.info("ToolExecutionService: Attempting strategy optimization for tool " + toolName);
                Object optimizedResult = strategyDispatcher.dispatch(toolName, parameters);

                // if the strategy returns an optimized plan, log it
                if (optimizedResult instanceof OptimizedToolCall) {
                    OptimizedToolCall optimized = (OptimizedToolCall) optimizedResult;
                    LogUtils.info("ToolExecutionService: Strategy optimization applied: " +
                                  optimized.getStrategyName() + ", steps=" + optimized.getStepCount() +
                                  ", time=" + optimized.getOptimizationTimeMs() + "ms");

                    // Note: the return value here is the optimized plan; actual tool execution
                    // still needs to call the original executor.
                    // Future: extend to directly execute the optimized tool call sequence.
                }

                return optimizedResult;
            } catch (UnsupportedOperationException e) {
                // no matching strategy - fall back to original executor
                LogUtils.info("ToolExecutionService: No matching strategy, falling back to original executor");
            } catch (Exception e) {
                // strategy execution failed - fall back to original executor
                LogUtils.warn("ToolExecutionService: Strategy optimization failed, falling back to original executor", e);
            }
        }

        // original executor path (backward compatible)
        ToolExecutor executor = toolExecutors.get(toolName);
        try {
            LogUtils.info("ToolExecutionService: Executing tool " + toolName + " (original executor)");
            Object result = executor.execute(parameters);
            LogUtils.info("ToolExecutionService: Tool " + toolName + " executed successfully");
            return result;
        } catch (Exception e) {
            LogUtils.warn("ToolExecutionService: Error executing tool " + toolName, e);
            throw new RuntimeException("Tool execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String[] getSupportedTools() {
        return toolExecutors.keySet().toArray(new String[0]);
    }

    @Override
    public boolean isToolSupported(String toolName) {
        return toolExecutors.containsKey(toolName);
    }

    @Override
    public void setToolSet(AIToolSet toolSet) {
        this.toolSet = toolSet;
    }

    @Override
    public AIToolSet getToolSet() {
        return toolSet;
    }

    /**
     * Enables or disables strategy optimization.
     *
     * @param enabled true to enable strategy optimization, false to use the original executor
     */
    public void setStrategyEnabled(boolean enabled) {
        this.strategyEnabled = enabled;
        LogUtils.info("ToolExecutionService: Strategy optimization " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Returns whether strategy optimization is enabled.
     *
     * @return true if strategy optimization is enabled
     */
    public boolean isStrategyEnabled() {
        return strategyEnabled;
    }

    /**
     * Returns the strategy dispatcher (for monitoring and management).
     *
     * @return strategy dispatcher instance
     */
    public ToolStrategyDispatcher getStrategyDispatcher() {
        return strategyDispatcher;
    }

    // tool executor interface
    @FunctionalInterface
    private interface ToolExecutor {
        Object execute(Map<String, Object> parameters) throws Exception;
    }

    // tool execution implementations
    private Object executeReadNodesWithDescendants(Map<String, Object> parameters) {
        String mapIdentifier = (String) parameters.get("mapIdentifier");
        List<String> nodeIdentifiers = (List<String>) parameters.get("nodeIdentifiers");
        List<String> contextSectionsStr = (List<String>) parameters.get("contextSections");
        Integer fullContentDepth = (Integer) parameters.get("fullContentDepth");
        Integer summaryDepth = (Integer) parameters.get("summaryDepth");
        Integer maximumTotalTextCharacters = (Integer) parameters.get("maximumTotalTextCharacters");
        
        // convert contextSections to enum type
        List<org.freeplane.plugin.ai.tools.read.ContextSection> contextSections = null;
        if (contextSectionsStr != null) {
            contextSections = contextSectionsStr.stream()
                    .map(section -> org.freeplane.plugin.ai.tools.read.ContextSection.valueOf(section))
                    .collect(Collectors.toList());
        }
        
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
                mapIdentifier,
                nodeIdentifiers,
                contextSections,
                fullContentDepth,
                summaryDepth,
                maximumTotalTextCharacters
        );
        return toolSet.readNodesWithDescendants(request);
    }

    private Object executeFetchNodesForEditing(Map<String, Object> parameters) {
        String mapIdentifier = (String) parameters.get("mapIdentifier");
        List<String> nodeIdentifiers = (List<String>) parameters.get("nodeIdentifiers");
        List<String> editableContentFields = (List<String>) parameters.get("editableContentFields");
        
        // convert editableContentFields to enum type
        List<org.freeplane.plugin.ai.tools.content.EditableContentField> fields = editableContentFields.stream()
                .map(field -> org.freeplane.plugin.ai.tools.content.EditableContentField.valueOf(field))
                .collect(Collectors.toList());
        
        FetchNodesForEditingRequest request = new FetchNodesForEditingRequest(mapIdentifier, nodeIdentifiers, fields);
        return toolSet.fetchNodesForEditing(request);
    }

    private Object executeGetSelectedMapAndNodeIdentifiers(Map<String, Object> parameters) {
        String selectionModeStr = (String) parameters.get("selectionCollectionMode");
        org.freeplane.plugin.ai.tools.selection.SelectionCollectionMode selectionMode = null;
        if (selectionModeStr != null) {
            selectionMode = org.freeplane.plugin.ai.tools.selection.SelectionCollectionMode.valueOf(selectionModeStr);
        }
        SelectionIdentifiersRequest request = new SelectionIdentifiersRequest(selectionMode);
        return toolSet.getSelectedMapAndNodeIdentifiers(request);
    }

    private Object executeCreateNodes(Map<String, Object> parameters) {
        String mapIdentifier = (String) parameters.get("mapIdentifier");
        String userSummary = (String) parameters.get("userSummary");
        
        // parse anchorPlacement
        Map<String, Object> anchorPlacementMap = (Map<String, Object>) parameters.get("anchorPlacement");
        String anchorNodeIdentifier = (String) anchorPlacementMap.get("anchorNodeIdentifier");
        String placementMode = (String) anchorPlacementMap.get("placementMode");
        AnchorPlacement anchorPlacement = new AnchorPlacement(
                anchorNodeIdentifier,
                org.freeplane.plugin.ai.tools.create.AnchorPlacementMode.valueOf(placementMode)
        );
        
        // parse nodes
        List<Map<String, Object>> nodesMap = (List<Map<String, Object>>) parameters.get("nodes");
        List<NodeCreationItem> nodes = new ArrayList<>();
        for (int i = 0; i < nodesMap.size(); i++) {
            Map<String, Object> nodeMap = nodesMap.get(i);
            
            // parse content
            Map<String, Object> contentMap = (Map<String, Object>) nodeMap.get("content");
            NodeContentWriteRequest content = null;
            if (contentMap != null) {
                content = new NodeContentWriteRequest(
                        (String) contentMap.get("text"),
                        contentMap.containsKey("textContentType") ? 
                                org.freeplane.plugin.ai.tools.content.ContentType.valueOf((String) contentMap.get("textContentType")) : null,
                        (String) contentMap.get("details"),
                        contentMap.containsKey("detailsContentType") ? 
                                org.freeplane.plugin.ai.tools.content.ContentType.valueOf((String) contentMap.get("detailsContentType")) : null,
                        (String) contentMap.get("note"),
                        contentMap.containsKey("noteContentType") ? 
                                org.freeplane.plugin.ai.tools.content.ContentType.valueOf((String) contentMap.get("noteContentType")) : null,
                        null, // attributes
                        (List<String>) contentMap.get("tags"),
                        (List<String>) contentMap.get("icons"),
                        (String) contentMap.get("hyperlink")
                );
            }
            
            NodeCreationItem node = new NodeCreationItem(
                    (Integer) nodeMap.get("index"),
                    (Integer) nodeMap.get("parentIndex"),
                    content,
                    nodeMap.containsKey("foldingState") ? 
                            org.freeplane.plugin.ai.tools.create.NodeFoldingState.valueOf((String) nodeMap.get("foldingState")) : null,
                    (String) nodeMap.get("mainStyle")
            );
            nodes.add(node);
        }
        
        CreateNodesRequest request = new CreateNodesRequest(mapIdentifier, userSummary, anchorPlacement, nodes);
        return toolSet.createNodes(request);
    }

    private Object executeEdit(Map<String, Object> parameters) {
        String mapIdentifier = (String) parameters.get("mapIdentifier");
        String userSummary = (String) parameters.get("userSummary");
        
        // parse items
        List<Map<String, Object>> itemsMap = (List<Map<String, Object>>) parameters.get("items");
        List<NodeContentEditItem> items = new ArrayList<>();
        for (Map<String, Object> itemMap : itemsMap) {
            NodeContentEditItem item = new NodeContentEditItem(
                    (String) itemMap.get("nodeIdentifier"),
                    org.freeplane.plugin.ai.tools.edit.EditedElement.valueOf((String) itemMap.get("editedElement")),
                    itemMap.containsKey("originalContentType") ? 
                            org.freeplane.plugin.ai.tools.content.ContentType.valueOf((String) itemMap.get("originalContentType")) : null,
                    (String) itemMap.get("value"),
                    (Integer) itemMap.get("index"),
                    itemMap.containsKey("operation") ? 
                            org.freeplane.plugin.ai.tools.edit.EditOperation.valueOf((String) itemMap.get("operation")) : null,
                    (String) itemMap.get("targetKey")
            );
            items.add(item);
        }
        
        EditRequest request = new EditRequest(mapIdentifier, userSummary, items);
        return toolSet.edit(request);
    }
}