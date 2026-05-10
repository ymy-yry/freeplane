package org.freeplane.plugin.ai.buffer.mindmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.buffer.BufferResponse;

import java.util.Map;

/**
 * Mindmap result optimizer.
 * Responsible for JSON format validation, quality assessment, and result optimisation.
 */
public class MindMapResultOptimizer {

    private final ObjectMapper objectMapper;

    public MindMapResultOptimizer() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Optimises the result returned by AI.
     * @param aiResponse raw AI response text
     * @param response response object (used for logging and scoring)
     * @return optimised result data, or {@code null} on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> optimizeResult(String aiResponse, BufferResponse response) {
        try {
            // 1. Clean the AI response.
            String cleanedJson = cleanAIResponse(aiResponse);
            response.addLog("AI response cleaned: " + cleanedJson.length() + " characters");

            // 2. Validate JSON format.
            Map<String, Object> mindMapData = validateAndParseJSON(cleanedJson);
            response.addLog("JSON format validation passed");

            // 3. Quality assessment.
            double qualityScore = assessQuality(mindMapData);
            response.setQualityScore(qualityScore);
            response.addLog("Quality score: " + qualityScore);

            // 4. Result optimisation.
            Map<String, Object> optimizedData = optimizeMindMapData(mindMapData);
            response.addLog("Result optimisation complete");

            return optimizedData;

        } catch (Exception e) {
            LogUtils.warn("MindMapResultOptimizer: optimization failed", e);
            response.addLog("Optimisation failed: " + e.getMessage());
            response.setQualityScore(0.0);
            return null;
        }
    }

    /**
     * Cleans the AI response by stripping Markdown code-block markers.
     */
    private String cleanAIResponse(String response) {
        String cleaned = response.trim();

        // Strip Markdown code-block markers.
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * Validates and parses the JSON string.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> validateAndParseJSON(String json) throws Exception {
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            // Validate basic structure.
            if (!data.containsKey("text")) {
                throw new IllegalArgumentException("JSON is missing the 'text' field");
            }

            return data;
        } catch (Exception e) {
            LogUtils.warn("MindMapResultOptimizer: invalid JSON format", e);
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        }
    }

    /**
     * Assesses the quality of the mindmap data and returns a score in [0, 100].
     */
    @SuppressWarnings("unchecked")
    private double assessQuality(Map<String, Object> mindMapData) {
        double score = 50.0; // base score

        // 1. Node count score (0-20 points).
        int nodeCount = countNodes(mindMapData);
        if (nodeCount >= 5) {
            score += 20.0;
        } else if (nodeCount >= 3) {
            score += 15.0;
        } else if (nodeCount >= 1) {
            score += 10.0;
        }

        // 2. Hierarchy depth score (0-15 points).
        int maxDepth = calculateMaxDepth(mindMapData);
        if (maxDepth >= 3) {
            score += 15.0;
        } else if (maxDepth >= 2) {
            score += 10.0;
        } else if (maxDepth >= 1) {
            score += 5.0;
        }

        // 3. Content quality score (0-15 points).
        if (hasMeaningfulContent(mindMapData)) {
            score += 15.0;
        }

        return Math.min(100.0, score);
    }

    /**
     * Counts the total number of nodes recursively.
     */
    @SuppressWarnings("unchecked")
    private int countNodes(Map<String, Object> nodeData) {
        int count = 1; // current node

        java.util.List<Map<String, Object>> children =
            (java.util.List<Map<String, Object>>) nodeData.get("children");

        if (children != null) {
            for (Map<String, Object> child : children) {
                count += countNodes(child);
            }
        }

        return count;
    }

    /**
     * Calculates the maximum depth of the mindmap tree.
     */
    @SuppressWarnings("unchecked")
    private int calculateMaxDepth(Map<String, Object> nodeData) {
        int maxDepth = 0;

        java.util.List<Map<String, Object>> children =
            (java.util.List<Map<String, Object>>) nodeData.get("children");

        if (children != null && !children.isEmpty()) {
            maxDepth = 1;
            for (Map<String, Object> child : children) {
                int childDepth = calculateMaxDepth(child);
                maxDepth = Math.max(maxDepth, 1 + childDepth);
            }
        }

        return maxDepth;
    }

    /**
     * Returns {@code true} if the node contains meaningful text content.
     */
    private boolean hasMeaningfulContent(Map<String, Object> nodeData) {
        String text = (String) nodeData.get("text");
        return text != null && text.trim().length() > 2;
    }

    /**
     * Optimises the mindmap data structure in-place.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> optimizeMindMapData(Map<String, Object> mindMapData) {
        // 1. Remove empty nodes.
        removeEmptyNodes(mindMapData);

        // 2. Normalise text.
        normalizeText(mindMapData);

        return mindMapData;
    }

    /**
     * Removes nodes with empty or blank text.
     */
    @SuppressWarnings("unchecked")
    private void removeEmptyNodes(Map<String, Object> nodeData) {
        java.util.List<Map<String, Object>> children =
            (java.util.List<Map<String, Object>>) nodeData.get("children");

        if (children != null) {
            children.removeIf(child -> {
                String text = (String) child.get("text");
                return text == null || text.trim().isEmpty();
            });

            // Recurse into remaining children.
            for (Map<String, Object> child : children) {
                removeEmptyNodes(child);
            }
        }
    }

    /**
     * Normalises node text by trimming whitespace.
     */
    @SuppressWarnings("unchecked")
    private void normalizeText(Map<String, Object> nodeData) {
        String text = (String) nodeData.get("text");
        if (text != null) {
            nodeData.put("text", text.trim());
        }

        java.util.List<Map<String, Object>> children =
            (java.util.List<Map<String, Object>>) nodeData.get("children");

        if (children != null) {
            for (Map<String, Object> child : children) {
                normalizeText(child);
            }
        }
    }
}