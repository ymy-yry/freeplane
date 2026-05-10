package org.freeplane.plugin.ai.service;

/**
 * AI service type enum.
 */
public enum AIServiceType {
    /**
     * Intelligent Q&A service.
     */
    CHAT("chat", "Chat"),

    /**
     * Agent service (e.g. mind map generation, node expansion).
     */
    AGENT("agent", "Agent");
    
    private final String code;
    private final String name;
    
    AIServiceType(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public static AIServiceType fromCode(String code) {
        for (AIServiceType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}