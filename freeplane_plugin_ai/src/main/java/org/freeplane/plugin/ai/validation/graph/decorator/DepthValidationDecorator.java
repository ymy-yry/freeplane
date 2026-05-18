package org.freeplane.plugin.ai.validation.graph.decorator;

import org.freeplane.plugin.ai.validation.graph.GraphDecorator;
import org.freeplane.plugin.ai.validation.graph.IGraphData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 深度验证装饰者
 * 检测节点深度是否超过限制
 */
public class DepthValidationDecorator extends GraphDecorator {
    
    private final int maxDepth;
    private final Map<String, Integer> nodeDepths = new HashMap<>();
    private final List<String> exceededNodes = new ArrayList<>();
    
    public DepthValidationDecorator(IGraphData graph, int maxDepth) {
        super(graph);
        this.maxDepth = maxDepth;
        validateDepth();
    }
    
    /**
     * 执行深度验证
     */
    private void validateDepth() {
        String rootId = wrapped.getRootId();
        if (rootId != null) {
            dfsDepth(rootId, 0);
        }
    }
    
    /**
     * DFS计算深度
     */
    private void dfsDepth(String nodeId, int depth) {
        nodeDepths.put(nodeId, depth);
        
        if (depth > maxDepth) {
            exceededNodes.add(nodeId);
        }
        
        for (String child : wrapped.getChildren(nodeId)) {
            dfsDepth(child, depth + 1);
        }
    }
    
    /**
     * 获取所有节点的深度映射
     */
    public Map<String, Integer> getNodeDepths() {
        return Collections.unmodifiableMap(nodeDepths);
    }
    
    /**
     * 获取超过深度限制的节点列表
     */
    public List<String> getExceededNodes() {
        return Collections.unmodifiableList(exceededNodes);
    }
    
    /**
     * 是否存在超过深度限制的节点
     */
    public boolean hasExceededDepth() {
        return !exceededNodes.isEmpty();
    }
    
    /**
     * 获取最大深度限制
     */
    public int getMaxDepth() {
        return maxDepth;
    }
    
    /**
     * 获取实际最大深度
     */
    public int getActualMaxDepth() {
        return nodeDepths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
