package org.freeplane.plugin.ai.validation.graph.decorator;

import org.freeplane.plugin.ai.validation.graph.GraphDecorator;
import org.freeplane.plugin.ai.validation.graph.IGraphData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 环检测装饰者 - 三色标记DFS算法
 * 
 * 颜色状态：
 * - WHITE (0): 未访问
 * - GRAY  (1): 访问中（在当前递归栈中）
 * - BLACK (2): 已访问完成
 * 
 * @author 提出者与设计者
 */
public class CycleDetectionDecorator extends GraphDecorator {
    
    private static final int WHITE = 0;
    private static final int GRAY = 1;
    private static final int BLACK = 2;
    
    private final List<List<String>> cycles = new ArrayList<>();
    
    public CycleDetectionDecorator(IGraphData graph) {
        super(graph);
        detectCycles();
    }
    
    /**
     * 执行环检测
     */
    private void detectCycles() {
        Map<String, Integer> color = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        
        // 初始化所有节点为WHITE
        for (String node : wrapped.getAllNodeIds()) {
            color.put(node, WHITE);
        }
        
        // 对每个未访问的节点执行DFS
        for (String node : wrapped.getAllNodeIds()) {
            if (color.get(node) == WHITE) {
                dfs(node, color, parent);
            }
        }
    }
    
    /**
     * DFS遍历（三色标记）
     */
    private void dfs(String node, Map<String, Integer> color, Map<String, String> parent) {
        color.put(node, GRAY); // 标记为访问中
        
        for (String child : wrapped.getChildren(node)) {
            if (color.get(child) == GRAY) {
                // 发现回边 → 检测到环
                List<String> cycle = buildCyclePath(node, child, parent);
                cycles.add(cycle);
            } else if (color.get(child) == WHITE) {
                parent.put(child, node);
                dfs(child, color, parent);
            }
            // BLACK节点：已处理完成，跳过
        }
        
        color.put(node, BLACK); // 标记为已完成
    }
    
    /**
     * 重建环路径（按需重建，正常路径零开销）
     */
    private List<String> buildCyclePath(String current, String target, Map<String, String> parent) {
        List<String> path = new ArrayList<>();
        path.add(target);
        
        while (!current.equals(target)) {
            path.add(current);
            String p = parent.get(current);
            if (p == null) {
                break; // 防御性检查
            }
            current = p;
        }
        
        path.add(target);
        Collections.reverse(path);
        return Collections.unmodifiableList(path);
    }
    
    /**
     * 是否存在环
     */
    public boolean hasCycle() {
        return !cycles.isEmpty();
    }
    
    /**
     * 获取所有检测到的环路径
     */
    public List<List<String>> getCycles() {
        return Collections.unmodifiableList(cycles);
    }
    
    /**
     * 获取环的数量
     */
    public int getCycleCount() {
        return cycles.size();
    }
}
