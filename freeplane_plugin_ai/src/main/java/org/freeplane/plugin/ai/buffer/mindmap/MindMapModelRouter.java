package org.freeplane.plugin.ai.buffer.mindmap;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.buffer.BufferRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mindmap model router.
 * Maintains model capability profiles, implements a scoring algorithm, and selects the optimal model.
 */
public class MindMapModelRouter {

    /** Model capability profile. */
    private static class ModelProfile {
        String modelName;
        String providerName;
        double qualityScore;    // quality score (0-100)
        double speedScore;      // speed score (0-100)
        double costScore;       // cost score (0-100, higher = cheaper)
        boolean supportsJson;   // whether JSON output is supported
        boolean supportsChinese; // whether Chinese language is supported

        ModelProfile(String modelName, String providerName, double quality, double speed, double cost) {
            this.modelName = modelName;
            this.providerName = providerName;
            this.qualityScore = quality;
            this.speedScore = speed;
            this.costScore = cost;
            this.supportsJson = true;
            this.supportsChinese = true;
        }
    }

    private final List<ModelProfile> availableModels;

    public MindMapModelRouter() {
        this.availableModels = new ArrayList<>();
        initializeModelProfiles();
    }

    /**
     * Initialises the model capability profiles.
     */
    private void initializeModelProfiles() {
        // OpenAI GPT-4o - high quality, well-suited for mindmap generation.
        availableModels.add(new ModelProfile(
            "openai/gpt-4o", "openrouter",
            95.0, 80.0, 60.0
        ));

        // Google Gemini - balanced performance and cost.
        availableModels.add(new ModelProfile(
            "gemini-2.0-flash", "gemini",
            85.0, 90.0, 80.0
        ));

        // DashScope Qwen - good Chinese language support.
        availableModels.add(new ModelProfile(
            "qwen-max", "dashscope",
            88.0, 85.0, 75.0
        ));

        // ERNIE - optimised for Chinese.
        availableModels.add(new ModelProfile(
            "ernie-4.5", "ernie",
            82.0, 75.0, 70.0
        ));

        LogUtils.info("MindMapModelRouter: initialized " + availableModels.size() + " model profiles");
    }

    /**
     * Selects the best available model for the given request.
     * @param request the buffer request
     * @return model selection string (providerName/modelName), or {@code null} if none available
     */
    public String selectBestModel(BufferRequest request) {
        // Filter to models whose API keys are configured.
        List<ModelProfile> usableModels = filterUsableModels();

        if (usableModels.isEmpty()) {
            LogUtils.warn("MindMapModelRouter: no usable models available");
            return null;
        }

        // Calculate a score for each model.
        Map<String, Double> modelScores = new HashMap<>();
        for (ModelProfile profile : usableModels) {
            double score = calculateScore(profile, request);
            modelScores.put(profile.providerName + "/" + profile.modelName, score);
            LogUtils.info("MindMapModelRouter: model " + profile.modelName + " scored " + score);
        }

        // Select the highest-scoring model.
        String bestModel = null;
        double highestScore = -1.0;

        for (Map.Entry<String, Double> entry : modelScores.entrySet()) {
            if (entry.getValue() > highestScore) {
                highestScore = entry.getValue();
                bestModel = entry.getKey();
            }
        }

        LogUtils.info("MindMapModelRouter: selected best model - " + bestModel + " (score: " + highestScore + ")");
        return bestModel;
    }

    /**
     * Filters the model list to only those with a configured API key.
     */
    private List<ModelProfile> filterUsableModels() {
        ResourceController rc = ResourceController.getResourceController();
        List<ModelProfile> usable = new ArrayList<>();

        for (ModelProfile profile : availableModels) {
            boolean hasKey = false;
            switch (profile.providerName) {
                case "openrouter":
                    hasKey = !rc.getProperty("ai_openrouter_key", "").trim().isEmpty();
                    break;
                case "gemini":
                    hasKey = !rc.getProperty("ai_gemini_key", "").trim().isEmpty();
                    break;
                case "dashscope":
                    hasKey = !rc.getProperty("ai_dashscope_key", "").trim().isEmpty();
                    break;
                case "ernie":
                    hasKey = !rc.getProperty("ai_ernie_key", "").trim().isEmpty();
                    break;
            }

            if (hasKey) {
                usable.add(profile);
            }
        }

        return usable;
    }

    /**
     * Calculates the weighted score for a model given the current request.
     */
    private double calculateScore(ModelProfile profile, BufferRequest request) {
        double score = 0.0;

        // Weight configuration.
        double qualityWeight = 0.5;  // quality weight 50%
        double speedWeight = 0.3;    // speed weight 30%
        double costWeight = 0.2;     // cost weight 20%

        // Adjust weights based on request type.
        BufferRequest.RequestType requestType = request.getRequestType();
        if (requestType == BufferRequest.RequestType.MINDMAP_GENERATION) {
            // Mindmap generation prioritises quality.
            qualityWeight = 0.6;
            speedWeight = 0.25;
            costWeight = 0.15;
        } else if (requestType == BufferRequest.RequestType.BRANCH_SUMMARY) {
            // Branch summarisation prioritises speed.
            qualityWeight = 0.4;
            speedWeight = 0.4;
            costWeight = 0.2;
        }

        // Compute weighted score.
        score = profile.qualityScore * qualityWeight +
                profile.speedScore * speedWeight +
                profile.costScore * costWeight;

        // Bonus for Chinese language support.
        String language = request.getParameter("language", "zh");
        if ("zh".equals(language) && profile.supportsChinese) {
            score += 5.0; // bonus for Chinese support
        }

        return score;
    }
}