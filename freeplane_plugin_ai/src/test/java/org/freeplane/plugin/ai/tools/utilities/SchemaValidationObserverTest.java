package org.freeplane.plugin.ai.tools.utilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.freeplane.plugin.ai.mcpserver.ModelContextProtocolTool;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SchemaValidationObserverTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void onBefore_withMissingRequiredField_throwsException() {
        // Schema with required field "nodeId"
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<String, Object>());
        schema.put("required", java.util.Arrays.asList("nodeId"));
        
        ModelContextProtocolTool tool = new ModelContextProtocolTool("testTool", "Test tool", schema);
        Map<String, ModelContextProtocolTool> toolSchemas = new HashMap<>();
        toolSchemas.put("testTool", tool);
        
        SchemaValidationObserver observer = new SchemaValidationObserver(toolSchemas, objectMapper);
        
        // Empty arguments should fail validation for required field
        ToolExecutionBeforeEvent event = ToolExecutionBeforeEvent.create(
            "testTool", "{}", ToolCaller.MCP);
        
        assertThatThrownBy(() -> observer.onBefore(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required field 'nodeId'");
    }

    @Test
    public void onBefore_withAllRequiredFields_passesValidation() {
        // Schema with required field "nodeId"
        Map<String, Object> properties = new HashMap<>();
        properties.put("nodeId", createStringProperty("Node identifier"));
        
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.Arrays.asList("nodeId"));
        
        ModelContextProtocolTool tool = new ModelContextProtocolTool("testTool", "Test tool", schema);
        Map<String, ModelContextProtocolTool> toolSchemas = new HashMap<>();
        toolSchemas.put("testTool", tool);
        
        SchemaValidationObserver observer = new SchemaValidationObserver(toolSchemas, objectMapper);
        
        // Valid arguments with required field
        String validArgs = "{\"nodeId\": \"node-123\"}";
        ToolExecutionBeforeEvent event = ToolExecutionBeforeEvent.create(
            "testTool", validArgs, ToolCaller.MCP);
        
        // Should not throw
        observer.onBefore(event);
    }

    @Test
    public void onBefore_withEmptyArgsAndNoRequiredFields_passesValidation() {
        // Schema with no required fields
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<String, Object>());
        schema.put("required", java.util.Collections.emptyList());
        
        ModelContextProtocolTool tool = new ModelContextProtocolTool("testTool", "Test tool", schema);
        Map<String, ModelContextProtocolTool> toolSchemas = new HashMap<>();
        toolSchemas.put("testTool", tool);
        
        SchemaValidationObserver observer = new SchemaValidationObserver(toolSchemas, objectMapper);
        
        // Empty arguments should pass when no required fields
        ToolExecutionBeforeEvent event = ToolExecutionBeforeEvent.create(
            "testTool", "{}", ToolCaller.MCP);
        
        // Should not throw
        observer.onBefore(event);
    }

    @Test
    public void onBefore_withEmptyStringArgsAndRequiredFields_throwsException() {
        // Schema with required field
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<String, Object>());
        schema.put("required", java.util.Arrays.asList("nodeId"));
        
        ModelContextProtocolTool tool = new ModelContextProtocolTool("testTool", "Test tool", schema);
        Map<String, ModelContextProtocolTool> toolSchemas = new HashMap<>();
        toolSchemas.put("testTool", tool);
        
        SchemaValidationObserver observer = new SchemaValidationObserver(toolSchemas, objectMapper);
        
        // Empty string arguments should fail validation
        ToolExecutionBeforeEvent event = ToolExecutionBeforeEvent.create(
            "testTool", "", ToolCaller.MCP);
        
        assertThatThrownBy(() -> observer.onBefore(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required field 'nodeId'");
    }

    @Test
    public void onBefore_withUnknownTool_skipsValidation() {
        Map<String, ModelContextProtocolTool> toolSchemas = new HashMap<>();
        // Empty - no tools registered
        
        SchemaValidationObserver observer = new SchemaValidationObserver(toolSchemas, objectMapper);
        
        // Tool not in schemas should skip validation
        ToolExecutionBeforeEvent event = ToolExecutionBeforeEvent.create(
            "unknownTool", "{}", ToolCaller.MCP);
        
        // Should not throw
        observer.onBefore(event);
    }

    @Test
    public void onBefore_withInvalidJson_throwsException() {
        Map<String, ModelContextProtocolTool> toolSchemas = new HashMap<>();
        SchemaValidationObserver observer = new SchemaValidationObserver(toolSchemas, objectMapper);
        
        // Add a tool to trigger validation
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        ModelContextProtocolTool tool = new ModelContextProtocolTool("testTool", "Test tool", schema);
        toolSchemas.put("testTool", tool);
        
        // Invalid JSON
        ToolExecutionBeforeEvent event = ToolExecutionBeforeEvent.create(
            "testTool", "{invalid json}", ToolCaller.MCP);
        
        assertThatThrownBy(() -> observer.onBefore(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid arguments");
    }

    private Map<String, Object> createStringProperty(String description) {
        Map<String, Object> property = new HashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
