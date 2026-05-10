package org.freeplane.plugin.ai.chat;

import org.freeplane.core.resources.ResourceController;

/**
 * Agent enhancement configuration.
 * Supports advanced Agent reasoning modes and behaviour control.
 */
public class AIAgentConfiguration {
    
    // Configuration property keys
    private static final String AGENT_ENABLE_CHAIN_OF_THOUGHT = "ai_agent_enable_chain_of_thought";
    private static final String AGENT_ENABLE_TOOL_VALIDATION = "ai_agent_enable_tool_validation";
    private static final String AGENT_ENABLE_QUALITY_CHECK = "ai_agent_enable_quality_check";
    private static final String AGENT_MAX_TOOL_CALLS_PER_TURN = "ai_agent_max_tool_calls_per_turn";
    private static final String AGENT_ENABLE_SELF_CORRECTION = "ai_agent_enable_self_correction";
    
    private final ResourceController resourceController;
    
    public AIAgentConfiguration() {
        this(ResourceController.getResourceController());
    }
    
    AIAgentConfiguration(ResourceController resourceController) {
        this.resourceController = resourceController;
    }
    
    /**
     * Whether chain-of-thought reasoning is enabled.
     * Default: true.
     */
    public boolean isChainOfThoughtEnabled() {
        String value = resourceController.getProperty(AGENT_ENABLE_CHAIN_OF_THOUGHT, "true");
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Whether pre-tool-call validation is enabled.
     * Default: true.
     */
    public boolean isToolValidationEnabled() {
        String value = resourceController.getProperty(AGENT_ENABLE_TOOL_VALIDATION, "true");
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Whether response quality checking is enabled.
     * Default: true.
     */
    public boolean isQualityCheckEnabled() {
        String value = resourceController.getProperty(AGENT_ENABLE_QUALITY_CHECK, "true");
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Maximum number of tool calls per conversation turn.
     * Default: 10.
     */
    public int getMaxToolCallsPerTurn() {
        String value = resourceController.getProperty(AGENT_MAX_TOOL_CALLS_PER_TURN, "10");
        try {
            int maxCalls = Integer.parseInt(value);
            return Math.max(1, Math.min(maxCalls, 50)); // clamped to [1, 50]
        } catch (NumberFormatException e) {
            return 10;
        }
    }
    
    /**
     * Whether the self-correction mechanism is enabled.
     * Default: true.
     */
    public boolean isSelfCorrectionEnabled() {
        String value = resourceController.getProperty(AGENT_ENABLE_SELF_CORRECTION, "true");
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Returns a summary of the current agent configuration (for logging and debugging).
     */
    public String getConfigurationSummary() {
        return String.format(
            "Agent Configuration [ChainOfThought: %s, ToolValidation: %s, QualityCheck: %s, MaxToolCalls: %d, SelfCorrection: %s]",
            isChainOfThoughtEnabled(),
            isToolValidationEnabled(),
            isQualityCheckEnabled(),
            getMaxToolCallsPerTurn(),
            isSelfCorrectionEnabled()
        );
    }
}
