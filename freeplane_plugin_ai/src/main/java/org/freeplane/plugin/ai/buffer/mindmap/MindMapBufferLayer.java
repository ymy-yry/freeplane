package org.freeplane.plugin.ai.buffer.mindmap;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.plugin.ai.buffer.BufferRequest;
import org.freeplane.plugin.ai.buffer.BufferResponse;
import org.freeplane.plugin.ai.buffer.IBufferLayer;
import org.freeplane.plugin.ai.chat.AIChatModelFactory;
import org.freeplane.plugin.ai.chat.AIProviderConfiguration;
import org.freeplane.plugin.ai.validation.MindMapGenerationValidator;
import org.freeplane.plugin.ai.validation.MindMapValidationResult;
import org.freeplane.plugin.ai.validation.source.PromptValidationSource;
import org.freeplane.plugin.ai.validation.source.ValidationSource;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Full mindmap buffer layer.
 * Integrates requirement analysis, prompt optimisation, model selection, and result optimisation.
 */
public class MindMapBufferLayer implements IBufferLayer {

    private final MindMapRequirementAnalyzer requirementAnalyzer;
    private final MindMapPromptOptimizer promptOptimizer;
    private final MindMapModelRouter modelRouter;
    private final MindMapResultOptimizer resultOptimizer;
    private final MindMapGenerationValidator validator;

    public MindMapBufferLayer() {
        this.requirementAnalyzer = new MindMapRequirementAnalyzer();
        this.promptOptimizer = new MindMapPromptOptimizer();
        this.modelRouter = new MindMapModelRouter();
        this.resultOptimizer = new MindMapResultOptimizer();
        this.validator = new MindMapGenerationValidator();
        LogUtils.info("MindMapBufferLayer: initialized");
    }

    @Override
    public String getName() {
        return "MindMapBufferLayer";
    }

    @Override
    public boolean canHandle(BufferRequest request) {
        if (request == null || request.getUserInput() == null) {
            return false;
        }

        String input = request.getUserInput().toLowerCase();

        // Check for mindmap-related keywords.
        boolean hasMindMapKeyword = input.contains("mindmap") ||
                                   input.contains("mind map") ||
                                   input.contains("generate") ||
                                   input.contains("create");

        return hasMindMapKeyword;
    }

    @Override
    public int getPriority() {
        return 10; // high priority
    }

    @Override
    public BufferResponse process(BufferRequest request) {
        long startTime = System.currentTimeMillis();
        BufferResponse response = new BufferResponse();

        try {
            // Step 1: Requirement analysis.
            LogUtils.info("MindMapBufferLayer: step 1 - requirement analysis");
            requirementAnalyzer.analyze(request);
            response.addLog("Request type identified: " + request.getRequestType());

            // Step 2: Prompt optimisation.
            LogUtils.info("MindMapBufferLayer: step 2 - prompt optimization");
            String optimizedPrompt = promptOptimizer.optimizePrompt(request);
            response.addLog("Prompt optimised: " + optimizedPrompt.length() + " chars");

            // Step 3: Model selection.
            LogUtils.info("MindMapBufferLayer: step 3 - model selection");
            String selectedModel = modelRouter.selectBestModel(request);
            if (selectedModel == null) {
                response.setSuccess(false);
                response.setErrorMessage("No AI model available. Please check your API key configuration.");
                response.setProcessingTime(System.currentTimeMillis() - startTime);
                return response;
            }
            response.setUsedModel(selectedModel);
            response.addLog("Model selected: " + selectedModel);

            // Step 4: Call AI.
            LogUtils.info("MindMapBufferLayer: step 4 - calling AI");
            String aiResponse = callAI(optimizedPrompt, selectedModel, request);

            // Step 4.5: Structural validation (with cycle-detection degradation).
            // CIRCULAR_DEPENDENCY -> must degrade to sampleJSON; cyclic data cannot be written to the mindmap.
            // Other structural errors (exceeded depth/children) -> log a warning and continue.
            LogUtils.info("MindMapBufferLayer: step 4.5 - structural validation");
            aiResponse = validateAndHandleDegradation(aiResponse, request, response);

            // Step 5: Result optimisation.
            LogUtils.info("MindMapBufferLayer: step 5 - result optimization");
            Map<String, Object> optimizedData = resultOptimizer.optimizeResult(aiResponse, response);

            if (optimizedData == null) {
                response.setSuccess(false);
                response.setErrorMessage("Invalid format returned by AI.");
                response.setProcessingTime(System.currentTimeMillis() - startTime);
                return response;
            }

            // Step 6: Create mindmap nodes.
            LogUtils.info("MindMapBufferLayer: step 6 - creating mindmap nodes");
            int nodeCount = createMindMapNodes(optimizedData);
            response.putData("nodeCount", nodeCount);

            // Mark success.
            response.setSuccess(true);
            response.setData(optimizedData);
            response.addLog("Nodes created: " + nodeCount);
            response.setProcessingTime(System.currentTimeMillis() - startTime);

            LogUtils.info("MindMapBufferLayer: processing completed successfully in " +
                         (System.currentTimeMillis() - startTime) + "ms");

        } catch (Exception e) {
            LogUtils.warn("MindMapBufferLayer: processing failed", e);
            response.setSuccess(false);
            response.setErrorMessage("Processing failed: " + e.getMessage());
            response.setProcessingTime(System.currentTimeMillis() - startTime);
        }

        return response;
    }

    /**
     * Validates the AI-returned JSON and applies degradation based on the result.
     *
     * <ul>
     *   <li>CIRCULAR_DEPENDENCY: cyclic data cannot be rendered; must degrade to sampleJSON.</li>
     *   <li>EXCEEDS_MAX_DEPTH / EXCEEDS_MAX_CHILDREN: structure is still legal; log a warning and continue.</li>
     *   <li>PARSE_ERROR: parsing failed; degrade to sampleJSON.</li>
     * </ul>
     *
     * @return the original aiResponse or the degraded fallback content
     */
    private String validateAndHandleDegradation(String aiResponse, BufferRequest request,
                                                BufferResponse response) {
        // Use ValidationSource proxy so that logs include model info.
        ValidationSource source = new PromptValidationSource(
            aiResponse, 
            request.getParameter("selectedModel", null)
        );
        MindMapValidationResult validationResult = validator.validate(source);

        if (validationResult.isValid()) {
            // Validation passed (possibly with warnings).
            if (validationResult.hasWarnings()) {
                validationResult.getWarnings().forEach(w ->
                    response.addLog("[VALIDATION WARNING] " + w.getCode() + ": " + w.getMessage()));
                LogUtils.info("MindMapBufferLayer: validation passed with "
                    + validationResult.getWarnings().size() + " warning(s)");
            } else {
                LogUtils.info("MindMapBufferLayer: validation passed");
            }
            return aiResponse;
        }

        // Errors found: classify and handle.
        boolean hasCycle = validationResult.getErrors().stream()
            .anyMatch(e -> "CIRCULAR_DEPENDENCY".equals(e.getCode()));
        boolean hasParseError = validationResult.getErrors().stream()
            .anyMatch(e -> "PARSE_ERROR".equals(e.getCode()));

        if (hasCycle || hasParseError) {
            // Cyclic structure / parse failure -> must degrade; cyclic data cannot be written to the mindmap.
            String reason = hasCycle ? "CIRCULAR_DEPENDENCY" : "PARSE_ERROR";
            String errorMsg = validationResult.getErrors().stream()
                .filter(e -> reason.equals(e.getCode()))
                .findFirst().map(e -> e.getMessage()).orElse(reason);
            LogUtils.warn("MindMapBufferLayer: validation failed [" + reason
                + "], degrading to sample JSON. reason=" + errorMsg);
            response.addLog("[VALIDATION DEGRADED] " + reason + ": falling back to sample mindmap");
            return createSampleMindMapJSON(request.getParameter("topic", "Topic"));
        }

        // Other structural errors (exceeded depth, exceeded child count, etc.) -> log and continue.
        validationResult.getErrors().forEach(e ->
            response.addLog("[VALIDATION WARNING] " + e.getCode() + ": " + e.getMessage()));
        LogUtils.warn("MindMapBufferLayer: validation has " + validationResult.getErrors().size()
            + " non-critical error(s), continuing with original response");
        return aiResponse;
    }

    /**
     * Lazily-initialised underlying ChatModel (double-checked locking),
     * bypassing the complex system message of AIChatService.
     */
    private volatile ChatModel chatModel;

    /**
     * Lazily initialises the ChatModel.
     */
    private void ensureChatModelInitialized() {
        if (chatModel == null) {
            synchronized (this) {
                if (chatModel == null) {
                    try {
                        AIProviderConfiguration configuration = new AIProviderConfiguration();
                        chatModel = AIChatModelFactory.createChatLanguageModel(configuration);
                        LogUtils.info("MindMapBufferLayer: ChatModel initialized");
                    } catch (Exception e) {
                        LogUtils.warn("MindMapBufferLayer: failed to initialize ChatModel", e);
                    }
                }
            }
        }
    }

    /**
     * Calls the real AI model (uses the low-level ChatModel directly,
     * avoiding interference from AIChatService's system message).
     */
    private String callAI(String prompt, String selectedModel, BufferRequest request) {
        LogUtils.info("MindMapBufferLayer: calling AI model " + selectedModel);
        ensureChatModelInitialized();
        if (chatModel == null) {
            LogUtils.warn("MindMapBufferLayer: ChatModel unavailable, using sample JSON");
            return createSampleMindMapJSON(request.getParameter("topic", "Topic"));
        }
        try {
            ChatRequest chatRequest = ChatRequest.builder()
                .messages(Arrays.asList(
                    SystemMessage.from("You are a mind map expert. Return only valid JSON, no markdown, no explanation."),
                    UserMessage.from(prompt)
                ))
                .build();
            ChatResponse chatResponse = chatModel.chat(chatRequest);
            return chatResponse.aiMessage().text();
        } catch (Exception e) {
            LogUtils.warn("MindMapBufferLayer: AI call failed", e);
            return createSampleMindMapJSON(request.getParameter("topic", "Topic"));
        }
    }

    /**
     * Fallback sample JSON used when AI is unavailable.
     */
    private String createSampleMindMapJSON(String topic) {
        return String.format(
            "{" +
            "  \"text\": \"%s\"," +
            "  \"children\": [" +
            "    {\"text\": \"Branch 1\", \"children\": [{\"text\": \"Sub-branch 1.1\"}, {\"text\": \"Sub-branch 1.2\"}]}," +
            "    {\"text\": \"Branch 2\", \"children\": [{\"text\": \"Sub-branch 2.1\"}]}," +
            "    {\"text\": \"Branch 3\"}" +
            "  ]" +
            "}",
            topic
        );
    }

    /**
     * Creates mindmap nodes from the AI-produced data structure.
     */
    @SuppressWarnings("unchecked")
    private int createMindMapNodes(Map<String, Object> mindMapData) {
        try {
            // Obtain MMapController.
            Controller controller = Controller.getCurrentController();
            if (controller == null) {
                LogUtils.warn("MindMapBufferLayer: Controller not available");
                return 0;
            }

            MModeController modeController = (MModeController) controller.getModeController();
            MMapController mapController = (MMapController) modeController.getMapController();
            MapModel mapModel = controller.getMap();

            if (mapModel == null) {
                LogUtils.warn("MindMapBufferLayer: No map is currently open");
                return 0;
            }

            // Set root node text.
            NodeModel rootNode = mapModel.getRootNode();
            String rootText = (String) mindMapData.get("text");
            if (rootText != null) {
                rootNode.setText(rootText);
                mapController.nodeChanged(rootNode);
            }

            // Recursively create child nodes.
            List<Map<String, Object>> children =
                (List<Map<String, Object>>) mindMapData.get("children");

            int[] nodeCount = {1}; // includes root
            if (children != null) {
                createNodesRecursive(rootNode, children, mapController, nodeCount);
            }

            return nodeCount[0];
        } catch (Exception e) {
            LogUtils.warn("MindMapBufferLayer: failed to create mindmap nodes", e);
            return 0;
        }
    }

    /**
     * Recursively creates child nodes.
     */
    @SuppressWarnings("unchecked")
    private void createNodesRecursive(NodeModel parentNode,
                                      List<Map<String, Object>> children,
                                      MMapController mapController,
                                      int[] nodeCount) {
        if (children == null) return;

        for (Map<String, Object> childData : children) {
            String text = (String) childData.get("text");
            if (text == null || text.trim().isEmpty()) continue;

            NodeModel childNode = mapController.addNewNode(
                parentNode,
                parentNode.getChildCount(),
                node -> node.setText(text.trim())
            );
            nodeCount[0]++;

            // Recurse into sub-children.
            List<Map<String, Object>> subChildren =
                (List<Map<String, Object>>) childData.get("children");
            if (subChildren != null && !subChildren.isEmpty()) {
                createNodesRecursive(childNode, subChildren, mapController, nodeCount);
            }
        }
    }
}