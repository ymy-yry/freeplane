package org.freeplane.plugin.ai.validation.graph;

import org.freeplane.plugin.ai.validation.graph.decorator.ChildrenCountDecorator;
import org.freeplane.plugin.ai.validation.graph.decorator.CycleDetectionDecorator;
import org.freeplane.plugin.ai.validation.graph.decorator.DepthValidationDecorator;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装饰者模式单元测试
 * 验证可选性和组合性
 */
public class GraphDecoratorTest {
    
    private SnakeDigestGraph graph;
    
    @Before
    public void setUp() {
        graph = new SnakeDigestGraph();
    }
    
    // ========== 基础图构建测试 ==========
    
    @Test
    public void testBasicGraphConstruction() {
        // 构建简单树：A -> [B, C], B -> [D]
        graph.setRootId("A");
        graph.registerNode("A", "Root");
        graph.registerNode("B", "Child B");
        graph.registerNode("C", "Child C");
        graph.registerNode("D", "Child D");
        
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        
        assertThat(graph.getRootId()).isEqualTo("A");
        assertThat(graph.getNodeCount()).isEqualTo(4);
        assertThat(graph.getChildren("A")).containsExactly("B", "C");
        assertThat(graph.getChildren("B")).containsExactly("D");
        assertThat(graph.getChildren("C")).isEmpty();
    }
    
    // ========== 环检测装饰者测试 ==========
    
    @Test
    public void testCycleDetection_NoCycle() {
        // 无环树：A -> B -> C
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        
        CycleDetectionDecorator decorator = new CycleDetectionDecorator(graph);
        
        assertThat(decorator.hasCycle()).isFalse();
        assertThat(decorator.getCycleCount()).isEqualTo(0);
    }
    
    @Test
    public void testCycleDetection_SimpleCycle() {
        // 有环：A -> B -> A
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("B", "A");
        
        CycleDetectionDecorator decorator = new CycleDetectionDecorator(graph);
        
        assertThat(decorator.hasCycle()).isTrue();
        assertThat(decorator.getCycleCount()).isEqualTo(1);
        
        List<List<String>> cycles = decorator.getCycles();
        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0)).containsExactly("A", "B", "A");
    }
    
    @Test
    public void testCycleDetection_ComplexCycle() {
        // 复杂环：A -> B -> C -> A
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");
        
        CycleDetectionDecorator decorator = new CycleDetectionDecorator(graph);
        
        assertThat(decorator.hasCycle()).isTrue();
        assertThat(decorator.getCycleCount()).isEqualTo(1);
        
        List<List<String>> cycles = decorator.getCycles();
        assertThat(cycles.get(0)).containsExactly("A", "B", "C", "A");
    }
    
    // ========== 深度验证装饰者测试 ==========
    
    @Test
    public void testDepthValidation_WithinLimit() {
        // 深度3：A(0) -> B(1) -> C(2)
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        
        DepthValidationDecorator decorator = new DepthValidationDecorator(graph, 5);
        
        assertThat(decorator.hasExceededDepth()).isFalse();
        assertThat(decorator.getActualMaxDepth()).isEqualTo(2);
        assertThat(decorator.getExceededNodes()).isEmpty();
    }
    
    @Test
    public void testDepthValidation_ExceedsLimit() {
        // 深度5：A(0) -> B(1) -> C(2) -> D(3) -> E(4)
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");
        
        DepthValidationDecorator decorator = new DepthValidationDecorator(graph, 3);
        
        assertThat(decorator.hasExceededDepth()).isTrue();
        assertThat(decorator.getActualMaxDepth()).isEqualTo(4);
        
        List<String> exceeded = decorator.getExceededNodes();
        // 深度>3的节点：E(4)
        assertThat(exceeded).containsExactly("E");
    }
    
    // ========== 子节点数验证装饰者测试 ==========
    
    @Test
    public void testChildrenCount_WithinLimit() {
        // A有3个子节点
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("A", "D");
        
        ChildrenCountDecorator decorator = new ChildrenCountDecorator(graph, 5);
        
        assertThat(decorator.hasExceededCount()).isFalse();
        assertThat(decorator.getActualMaxChildrenCount()).isEqualTo(3);
    }
    
    @Test
    public void testChildrenCount_ExceedsLimit() {
        // A有5个子节点
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("A", "D");
        graph.addEdge("A", "E");
        graph.addEdge("A", "F");
        
        ChildrenCountDecorator decorator = new ChildrenCountDecorator(graph, 3);
        
        assertThat(decorator.hasExceededCount()).isTrue();
        assertThat(decorator.getActualMaxChildrenCount()).isEqualTo(5);
        assertThat(decorator.getExceededNodes()).containsExactly("A");
    }
    
    // ========== 装饰者组合测试 ==========
    
    @Test
    public void testDecoratorChain_CycleAndDepth() {
        // 组合：环检测 + 深度验证（无环图）
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");
        graph.addEdge("E", "F"); // 深度5
        
        IGraphData decorated = graph;
        decorated = new CycleDetectionDecorator(decorated);
        decorated = new DepthValidationDecorator(decorated, 3);
        
        // 装饰链: graph -> CycleDetectionDecorator -> DepthValidationDecorator
        DepthValidationDecorator depthDec = (DepthValidationDecorator) decorated;
        CycleDetectionDecorator cycleDec = (CycleDetectionDecorator) depthDec.getWrapped();
        
        assertThat(cycleDec.hasCycle()).isFalse();
        assertThat(depthDec.hasExceededDepth()).isTrue();
    }
    
    @Test
    public void testDecoratorChain_OnlyCycle() {
        // 只用环检测装饰者（可选性验证）
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("B", "A");
        
        IGraphData decorated = new CycleDetectionDecorator(graph);
        
        assertThat(decorated).isInstanceOf(CycleDetectionDecorator.class);
        assertThat(((CycleDetectionDecorator) decorated).hasCycle()).isTrue();
    }
    
    @Test
    public void testDecoratorChain_OnlyDepth() {
        // 只用深度验证装饰者（可选性验证）
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        
        IGraphData decorated = new DepthValidationDecorator(graph, 10);
        
        assertThat(decorated).isInstanceOf(DepthValidationDecorator.class);
        assertThat(((DepthValidationDecorator) decorated).hasExceededDepth()).isFalse();
    }
    
    @Test
    public void testDecoratorChain_AllDecorators() {
        // 完整装饰链：环检测 + 深度 + 子节点数
        graph.setRootId("A");
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        
        IGraphData decorated = graph;
        decorated = new CycleDetectionDecorator(decorated);
        decorated = new DepthValidationDecorator(decorated, 10);
        decorated = new ChildrenCountDecorator(decorated, 5);
        
        // 装饰链: graph -> CycleDetectionDecorator -> DepthValidationDecorator -> ChildrenCountDecorator
        ChildrenCountDecorator countDec = (ChildrenCountDecorator) decorated;
        DepthValidationDecorator depthDec = (DepthValidationDecorator) countDec.getWrapped();
        CycleDetectionDecorator cycleDec = (CycleDetectionDecorator) depthDec.getWrapped();
        
        assertThat(cycleDec.hasCycle()).isFalse();
        assertThat(depthDec.hasExceededDepth()).isFalse();
        assertThat(countDec.hasExceededCount()).isFalse();
    }
    
    // ========== 防御性封装测试 ==========
    
    @Test(expected = UnsupportedOperationException.class)
    public void testDefensiveEncapsulation_CannotModifyChildren() {
        graph.setRootId("A");
        graph.addEdge("A", "B");
        
        List<String> children = graph.getChildren("A");
        children.add("X"); // 应该抛出异常
    }
    
    @Test
    public void testDefensiveEncapsulation_ReturnsEmptyList() {
        graph.setRootId("A");
        
        List<String> children = graph.getChildren("NonExistent");
        assertThat(children).isEmpty();
    }
}
