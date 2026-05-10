package org.freeplane.plugin.ai.buffer.mindmap;

import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.buffer.BufferRequest;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

/**
 * Mindmap prompt optimizer.
 * Maintains a domain-specific prompt template library, selects the appropriate template
 * for a request, and fills in its parameters.
 * Supports YAML format (with native multi-line text support).
 */
public class MindMapPromptOptimizer {

    private Map<String, Object> promptTemplates;

    public MindMapPromptOptimizer() {
        loadTemplates();
    }

    /**
     * Loads prompt templates from the YAML resource file.
     */
    private void loadTemplates() {
        try (InputStream is = getClass().getResourceAsStream("/org/freeplane/plugin/ai/buffer/prompts.yaml")) {
            if (is != null) {
                Yaml yaml = new Yaml();
                promptTemplates = yaml.load(is);
                LogUtils.info("MindMapPromptOptimizer: loaded YAML prompt templates");
            } else {
                LogUtils.warn("MindMapPromptOptimizer: prompts.yaml not found");
            }
        } catch (Exception e) {
            LogUtils.warn("MindMapPromptOptimizer: failed to load templates", e);
        }
    }

    /**
     * Optimises the prompt for the given request.
     * @param request the buffer request
     * @return the optimised prompt string
     */
    public String optimizePrompt(BufferRequest request) {
        String templateKey = selectTemplateKey(request);
        String template = getTemplate(templateKey);

        if (template == null) {
            LogUtils.warn("MindMapPromptOptimizer: template not found - " + templateKey);
            template = getDefaultTemplate(request);
        }

        // Fill in template parameters.
        String optimizedPrompt = fillTemplate(template, request);
        LogUtils.info("MindMapPromptOptimizer: optimized prompt length - " + optimizedPrompt.length() + " chars");

        return optimizedPrompt;
    }

    /**
     * Retrieves a template value from the nested Map structure using a dot-separated key.
     */
    @SuppressWarnings("unchecked")
    private String getTemplate(String key) {
        if (promptTemplates == null) return null;
        
        String[] parts = key.split("\\.");
        Map<String, Object> current = promptTemplates;
        
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return null;
            }
        }
        
        Object value = current.get(parts[parts.length - 1]);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Selects the appropriate template key based on the request type and language.
     */
    private String selectTemplateKey(BufferRequest request) {
        String language = request.getParameter("language", "zh");
        BufferRequest.RequestType requestType = request.getRequestType();

        switch (requestType) {
            case MINDMAP_GENERATION:
                return "mindmap.generation." + language;
            case NODE_EXPANSION:
                return "mindmap.expansion." + language;
            case BRANCH_SUMMARY:
                return "mindmap.summary." + language;
            default:
                return "mindmap.generation." + language;
        }
    }

    /**
     * Returns a hard-coded default template when no YAML template is found.
     */
    private String getDefaultTemplate(BufferRequest request) {
        String topic = request.getParameter("topic", "Unknown topic");
        int maxDepth = request.getParameter("maxDepth", 3);
        String language = request.getParameter("language", "zh");

        if ("zh".equals(language)) {
            return String.format(
                "Generate a complete mindmap structure for '%s'.\n" +
                "Requirements:\n" +
                "1. Include %d levels of nodes\n" +
                "2. Return strict JSON format\n" +
                "3. Use Chinese\n\n" +
                "Return format:\n" +
                "{\n" +
                "  \"text\": \"%s\",\n" +
                "  \"children\": []\n" +
                "}\n\n" +
                "Return JSON only.",
                topic, maxDepth, topic
            );
        } else {
            return String.format(
                "Generate a complete mind map structure for '%s'.\n" +
                "Requirements:\n" +
                "1. Include %d levels of nodes\n" +
                "2. Return strict JSON format\n" +
                "3. Use English\n\n" +
                "Return format:\n" +
                "{\n" +
                "  \"text\": \"%s\",\n" +
                "  \"children\": []\n" +
                "}\n\n" +
                "Return JSON only.",
                topic, maxDepth, topic
            );
        }
    }

    /**
     * Fills template placeholders with parameter values from the request.
     */
    private String fillTemplate(String template, BufferRequest request) {
        String result = template;

        // Replace common placeholder tokens.
        result = result.replace("{topic}", request.getParameter("topic", ""));
        result = result.replace("{maxDepth}", String.valueOf(request.getParameter("maxDepth", 3)));
        result = result.replace("{language}", request.getParameter("language", "zh"));
        result = result.replace("{nodeText}", request.getParameter("nodeText", ""));
        result = result.replace("{count}", String.valueOf(request.getParameter("count", 5)));
        result = result.replace("{depth}", String.valueOf(request.getParameter("depth", 2)));
        result = result.replace("{focus}", request.getParameter("focus", ""));
        result = result.replace("{content}", request.getParameter("content", ""));
        result = result.replace("{maxWords}", String.valueOf(request.getParameter("maxWords", 100)));

        return result;
    }
}