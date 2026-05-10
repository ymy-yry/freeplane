package org.freeplane.plugin.ai.buffer.mindmap;

import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.buffer.BufferRequest;

import java.util.regex.Pattern;

/**
 * Mindmap requirement understanding engine.
 * Identifies task type, extracts parameters, and recognises implicit needs.
 */
public class MindMapRequirementAnalyzer {

    // Task-type recognition keywords
    private static final String[] GENERATION_KEYWORDS = {
        "generate", "create", "make", "build"
    };

    private static final String[] EXPANSION_KEYWORDS = {
        "expand", "extend", "detail"
    };

    private static final String[] SUMMARY_KEYWORDS = {
        "summary", "summarize"
    };

    /**
     * Analyses the user's request and populates the {@link BufferRequest} with detected parameters.
     * @param request the request object to populate
     */
    public void analyze(BufferRequest request) {
        String input = request.getUserInput();
        if (input == null || input.trim().isEmpty()) {
            LogUtils.warn("MindMapRequirementAnalyzer: empty input");
            return;
        }

        // 1. Identify task type.
        BufferRequest.RequestType requestType = identifyRequestType(input);
        request.setRequestType(requestType);
        LogUtils.info("MindMapRequirementAnalyzer: identified request type - " + requestType);

        // 2. Extract topic.
        String topic = extractTopic(input);
        request.addParameter("topic", topic);
        LogUtils.info("MindMapRequirementAnalyzer: extracted topic - " + topic);

        // 3. Extract hierarchy depth.
        int maxDepth = extractMaxDepth(input);
        request.addParameter("maxDepth", maxDepth);
        LogUtils.info("MindMapRequirementAnalyzer: extracted maxDepth - " + maxDepth);

        // 4. Detect language.
        String language = detectLanguage(input);
        request.addParameter("language", language);
        LogUtils.info("MindMapRequirementAnalyzer: detected language - " + language);

        // 5. Identify implicit needs.
        identifyImplicitNeeds(request);
    }

    /**
     * Identifies the request type from the input text.
     */
    private BufferRequest.RequestType identifyRequestType(String input) {
        String lowerInput = input.toLowerCase();

        // Check for expansion keywords.
        for (String keyword : EXPANSION_KEYWORDS) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return BufferRequest.RequestType.NODE_EXPANSION;
            }
        }

        // Check for summary keywords.
        for (String keyword : SUMMARY_KEYWORDS) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return BufferRequest.RequestType.BRANCH_SUMMARY;
            }
        }

        // Default to generation type.
        for (String keyword : GENERATION_KEYWORDS) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return BufferRequest.RequestType.MINDMAP_GENERATION;
            }
        }

        // Fall back to mindmap generation.
        return BufferRequest.RequestType.MINDMAP_GENERATION;
    }

    /**
     * Extracts the main topic from the input text by stripping common command prefixes.
     */
    private String extractTopic(String input) {
        // Strip common Chinese command prefixes.
        String topic = input
            .replaceAll("(?i)(帮我|给我|请|为我)\\s*(生成|创建|做|制作|画|构建|建立)", "")
            .replaceAll("(?i)(generate|create|make|build)\\s*(a|an|the)?", "")
            .trim();

        // If the topic is still empty, use the original input.
        if (topic.isEmpty()) {
            topic = input;
        }

        return topic;
    }

    /**
     * Extracts the maximum depth from the input (looks for patterns like "3 levels").
     */
    private int extractMaxDepth(String input) {
        // Try to match a numeric depth.
        Pattern pattern = Pattern.compile("(\\d+)\\s*层");
        java.util.regex.Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            try {
                int depth = Integer.parseInt(matcher.group(1));
                return Math.max(1, Math.min(10, depth)); // clamped to [1, 10]
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // Default depth.
        return 3;
    }

    /**
     * Detects the language of the input.
     */
    private String detectLanguage(String input) {
        // Simple heuristic: contains CJK characters → Chinese.
        if (Pattern.compile("[\\u4e00-\\u9fa5]").matcher(input).find()) {
            return "zh";
        }
        return "en";
    }

    /**
     * Identifies implicit needs and adjusts request parameters accordingly.
     */
    private void identifyImplicitNeeds(BufferRequest request) {
        String input = request.getUserInput().toLowerCase();

        // Increase depth if "detailed" is mentioned.
        if (input.contains("详细") || input.contains("detailed")) {
            int currentDepth = request.getParameter("maxDepth", 3);
            request.addParameter("maxDepth", Math.min(currentDepth + 1, 10));
        }

        // Decrease depth if "simple" is mentioned.
        if (input.contains("简单") || input.contains("simple")) {
            int currentDepth = request.getParameter("maxDepth", 3);
            request.addParameter("maxDepth", Math.max(currentDepth - 1, 1));
        }
    }
}