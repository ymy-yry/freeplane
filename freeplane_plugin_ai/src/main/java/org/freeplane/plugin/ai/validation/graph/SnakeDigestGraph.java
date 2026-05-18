package org.freeplane.plugin.ai.validation.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 灵蛇环测图 - 基础图数据容器
 * 采用"蛇吞蛋"原理：JSON解析后逐步释放内存，只保留邻接表结构
 * 
 * @author 提出者与设计者
 */
public class SnakeDigestGraph implements IGraphData {
    
    private final Map<String, List<String>> adjacency = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private String rootId;
    
    /**
     * 注册节点
     */
    public void registerNode(String nodeId, String label) {
        labels.put(nodeId, label);
        // 懒扩容：叶子节点不创建ArrayList，节省40字节/节点
    }
    
    /**
     * 添加边（父节点→子节点）
     */
    public void addEdge(String parentId, String childId) {
        adjacency.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childId);
        // 确保子节点也在邻接表中（使用空ArrayList而非Collections.emptyList()，因为后续可能添加子节点）
        adjacency.putIfAbsent(childId, new ArrayList<>());
    }
    
    /**
     * 设置根节点ID
     */
    public void setRootId(String rootId) {
        this.rootId = rootId;
    }
    
    @Override
    public String getRootId() {
        return rootId;
    }
    
    @Override
    public List<String> getChildren(String nodeId) {
        List<String> children = adjacency.get(nodeId);
        return children == null 
                ? Collections.emptyList() 
                : Collections.unmodifiableList(children);
    }
    
    @Override
    public String getLabel(String nodeId) {
        return labels.get(nodeId);
    }
    
    @Override
    public Set<String> getAllNodeIds() {
        return adjacency.keySet();
    }
    
    /**
     * 获取节点数量
     */
    public int getNodeCount() {
        return adjacency.size();
    }
    
    /**
     * 创建颜色映射（供DFS使用）
     */
    public Map<String, Integer> newColorMap() {
        Map<String, Integer> color = new HashMap<>();
        for (String nodeId : adjacency.keySet()) {
            color.put(nodeId, 0); // WHITE = 0
        }
        return color;
    }
    
    /**
     * 创建父节点映射（供环路径重建使用）
     */
    public Map<String, String> newParentMap() {
        return new HashMap<>();
    }
}
