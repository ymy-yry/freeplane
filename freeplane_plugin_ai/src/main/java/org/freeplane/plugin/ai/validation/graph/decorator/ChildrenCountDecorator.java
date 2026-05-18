package org.freeplane.plugin.ai.validation.graph.decorator;

import org.freeplane.plugin.ai.validation.graph.GraphDecorator;
import org.freeplane.plugin.ai.validation.graph.IGraphData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 子节点数量验证装饰者
 * 检测节点的子节点数是否超过限制
 */
public class ChildrenCountDecorator extends GraphDecorator {
    
    private final int maxChildrenCount;
    private final Map<String, Integer> childrenCounts = new HashMap<>();
    private final List<String> exceededNodes = new ArrayList<>();
    
    public ChildrenCountDecorator(IGraphData graph, int maxChildrenCount) {
        super(graph);
        this.maxChildrenCount = maxChildrenCount;
        validateChildrenCount();
    }
    
    /**
     * 执行子节点数量验证
     */
    private void validateChildrenCount() {
        for (String nodeId : wrapped.getAllNodeIds()) {
            List<String> children = wrapped.getChildren(nodeId);
            int count = children.size();
            childrenCounts.put(nodeId, count);
            
            if (count > maxChildrenCount) {
                exceededNodes.add(nodeId);
            }
        }
    }
    
    /**
     * 获取所有节点的子节点数映射
     */
    public Map<String, Integer> getChildrenCounts() {
        return Collections.unmodifiableMap(childrenCounts);
    }
    
    /**
     * 获取超过子节点数限制的节点列表
     */
    public List<String> getExceededNodes() {
        return Collections.unmodifiableList(exceededNodes);
    }
    
    /**
     * 是否存在超过子节点数限制的节点
     */
    public boolean hasExceededCount() {
        return !exceededNodes.isEmpty();
    }
    
    /**
     * 获取最大子节点数限制
     */
    public int getMaxChildrenCount() {
        return maxChildrenCount;
    }
    
    /**
     * 获取实际最大子节点数
     */
    public int getActualMaxChildrenCount() {
        return childrenCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
