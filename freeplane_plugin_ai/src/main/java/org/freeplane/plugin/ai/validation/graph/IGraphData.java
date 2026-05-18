package org.freeplane.plugin.ai.validation.graph;

import java.util.List;
import java.util.Set;

/**
 * 图数据接口 - 装饰者模式的基础组件
 */
public interface IGraphData {
    
    /**
     * 获取根节点ID
     */
    String getRootId();
    
    /**
     * 获取子节点列表（返回不可变视图）
     */
    List<String> getChildren(String nodeId);
    
    /**
     * 获取节点标签
     */
    String getLabel(String nodeId);
    
    /**
     * 获取所有节点ID集合
     */
    Set<String> getAllNodeIds();
}
