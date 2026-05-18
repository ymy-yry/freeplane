package org.freeplane.plugin.ai.validation.graph;

import java.util.List;
import java.util.Set;

/**
 * 图数据装饰者抽象基类
 * 所有具体装饰者必须继承此类
 */
public abstract class GraphDecorator implements IGraphData {
    
    protected final IGraphData wrapped;
    
    public GraphDecorator(IGraphData graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        this.wrapped = graph;
    }
    
    @Override
    public String getRootId() {
        return wrapped.getRootId();
    }
    
    @Override
    public List<String> getChildren(String nodeId) {
        return wrapped.getChildren(nodeId);
    }
    
    @Override
    public String getLabel(String nodeId) {
        return wrapped.getLabel(nodeId);
    }
    
    @Override
    public Set<String> getAllNodeIds() {
        return wrapped.getAllNodeIds();
    }
    
    /**
     * 获取被包装的图对象
     */
    public IGraphData getWrapped() {
        return wrapped;
    }
}
