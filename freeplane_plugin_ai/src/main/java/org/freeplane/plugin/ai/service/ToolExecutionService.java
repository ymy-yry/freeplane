package org.freeplane.plugin.ai.service;

import org.freeplane.plugin.ai.tools.AIToolSet;

import java.util.Map;

/**
 * Tool execution service interface.
 * Provides direct tool invocation capability, bypassing the LLM chat stage.
 */
public interface ToolExecutionService {

    /**
     * Executes a tool call.
     * @param toolName the name of the tool
     * @param parameters tool parameters
     * @return execution result
     */
    Object executeTool(String toolName, Map<String, Object> parameters);

    /**
     * Returns the list of supported tools.
     * @return array of tool names
     */
    String[] getSupportedTools();

    /**
     * Checks whether a tool is supported.
     * @param toolName the name of the tool
     * @return true if supported
     */
    boolean isToolSupported(String toolName);

    /**
     * Sets the AIToolSet instance.
     * @param toolSet AIToolSet instance
     */
    void setToolSet(AIToolSet toolSet);

    /**
     * Returns the AIToolSet instance.
     * @return AIToolSet instance
     */
    AIToolSet getToolSet();
}