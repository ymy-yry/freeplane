package org.freeplane.plugin.ai.service.impl;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.buffer.BufferLayerRouter;
import org.freeplane.plugin.ai.buffer.mindmap.MindMapPromptOptimizer;
import org.freeplane.plugin.ai.chat.AIChatService;
import org.freeplane.plugin.ai.chat.AIChatModelFactory;
import org.freeplane.plugin.ai.chat.AIChatServiceFactory;
import org.freeplane.plugin.ai.chat.AIProviderConfiguration;
import org.freeplane.plugin.ai.chat.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.maps.ControllerMapModelProvider;
import org.freeplane.plugin.ai.service.AIService;
import org.freeplane.plugin.ai.service.AIServiceResponse;
import org.freeplane.plugin.ai.service.AIServiceType;
import org.freeplane.plugin.ai.service.ToolExecutionService;
import org.freeplane.plugin.ai.service.impl.DefaultToolExecutionService;
import org.freeplane.plugin.ai.service.scheduling.BuildTask;
import org.freeplane.plugin.ai.service.scheduling.BuildTaskScheduler;
import org.freeplane.plugin.ai.service.scheduling.SchedulingConfig;
import org.freeplane.plugin.ai.service.scheduling.SchedulingMonitor;

import java.util.concurrent.TimeUnit;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultAgentService implements AIService {

    private static volatile AIChatService agentService;
    private static volatile ChatModel chatModel;
    private static volatile StreamingChatModel streamingChatModel;
    private static volatile AIToolSet toolSet;
    private static volatile AvailableMaps availableMaps;
    private static volatile BufferLayerRouter bufferLayerRouter;
    private static final AtomicInteger inputTokens = new AtomicInteger(0);
    private static final AtomicInteger outputTokens = new AtomicInteger(0);
    private static final MindMapPromptOptimizer promptOptimizer = new MindMapPromptOptimizer();
    private static volatile ToolExecutionService toolExecutionService;
    private static volatile BuildTaskScheduler taskScheduler;
    private static volatile SchedulingConfig schedulingConfig;
    private static volatile SchedulingMonitor schedulingMonitor;

    @Override
    public AIServiceType getServiceType() {
        return AIServiceType.AGENT;
    }

    @Override
    public String getServiceName() {
        return "default_agent_service";
    }

    @Override
    public AIServiceResponse processRequest(Map<String, Object> request) {
        try {
            String action = (String) request.get("action");
            if (action == null) {
                return AIServiceResponse.error("Action is required");
            }

            // scheduler enabled: submit task and wait asynchronously via future
            if (schedulingConfig != null && schedulingConfig.isEnabled() && taskScheduler != null) {
                BuildTask task = taskScheduler.submitTask(action, request);
                // if queue is full, return CANCELLED result immediately
                if (task.getStatus() == BuildTask.TaskStatus.CANCELLED) {
                    return task.getFuture().getNow(AIServiceResponse.error("Scheduler queue is full"));
                }
                // wait up to 120 seconds for the task to complete
                return task.getFuture().get(120, TimeUnit.SECONDS);
            }

            // scheduler disabled: execute directly
            return dispatchAction(action, request);
        } catch (java.util.concurrent.TimeoutException e) {
            LogUtils.warn("DefaultAgentService.processRequest task wait timed out", e);
            return AIServiceResponse.error("Agent action timed out after 120s");
        } catch (java.util.concurrent.ExecutionException e) {
            LogUtils.warn("DefaultAgentService.processRequest task execution exception", e);
            return AIServiceResponse.error("Agent action failed: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AIServiceResponse.error("Agent action interrupted");
        } catch (Exception e) {
            LogUtils.warn("DefaultAgentService.processRequest failed", e);
            return AIServiceResponse.error("Agent action failed: " + e.getMessage());
        }
    }

    /**
     * Dispatches to the concrete handler based on the action type.
     * Public to allow cross-package access (BuildTaskExecutor calls this directly
     * to avoid re-entering the scheduling path and causing a deadlock).
     */
    public AIServiceResponse dispatchAction(String action, Map<String, Object> request) {
        switch (action) {
            case "generate-mindmap":
                return handleGenerateMindMap(request);
            case "expand-node":
                return handleExpandNode(request);
            case "summarize":
                return handleSummarize(request);
            case "tag":
                return handleTag(request);
            case "execute-tool":
                return handleExecuteTool(request);
            default:
                return AIServiceResponse.error("Unknown action: " + action);
        }
    }

    private AIServiceResponse handleGenerateMindMap(Map<String, Object> request) {
        String topic = (String) request.get("topic");
        if (topic == null || topic.trim().isEmpty()) {
            return AIServiceResponse.error("Topic is required");
        }

        // Create task for monitoring
        BuildTask task = new BuildTask("generate-mindmap", request);
        
        try {
            ensureAgentInitialized();
            
            // Record task start
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskStart(task);
            }

            String prompt = buildMindMapPrompt(topic, request);
            // use a simple System Prompt; full prompt engineering is in the prompts.properties template
            String result = chatWithModel(
                "You are a helpful assistant. Follow the instructions below carefully.",
                prompt
            );

            Map<String, Object> data = Map.of(
                "success", true,
                "topic", topic,
                "result", result,
                "tokenUsage", Map.of(
                    "inputTokens", inputTokens.get(),
                    "outputTokens", outputTokens.get()
                )
            );

            // Record task completion
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, true);
            }

            return AIServiceResponse.success("Mindmap prompt generated", data);
        } catch (Exception e) {
            // Record task failure
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, false);
            }
            LogUtils.warn("DefaultAgentService.handleGenerateMindMap failed", e);
            return AIServiceResponse.error("Failed to generate mindmap: " + e.getMessage());
        }
    }

    private AIServiceResponse handleExpandNode(Map<String, Object> request) {
        String nodeId = (String) request.get("nodeId");
        if (nodeId == null) {
            return AIServiceResponse.error("NodeId is required");
        }

        // Create task for monitoring
        BuildTask task = new BuildTask("expand-node", request);
        
        try {
            ensureAgentInitialized();
            
            // Record task start
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskStart(task);
            }

            String mapId = (String) request.get("mapId");
            Integer depth = (Integer) request.get("depth");
            Integer count = (Integer) request.get("count");
            String focus = (String) request.get("focus");

            String prompt = buildExpandNodePrompt(nodeId, mapId, depth, count, focus);
            String result = chatWithModel(
                "You are a mind map expert. Return only valid JSON with a 'children' array, no markdown, no explanation.",
                prompt
            );

            Map<String, Object> data = Map.of(
                "nodeId", nodeId,
                "result", result,
                "tokenUsage", Map.of(
                    "inputTokens", inputTokens.get(),
                    "outputTokens", outputTokens.get()
                )
            );

            // Record task completion
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, true);
            }

            return AIServiceResponse.success("Node expansion prompt generated", data);
        } catch (Exception e) {
            // Record task failure
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, false);
            }
            LogUtils.warn("DefaultAgentService.handleExpandNode failed", e);
            return AIServiceResponse.error("Failed to expand node: " + e.getMessage());
        }
    }

    private AIServiceResponse handleSummarize(Map<String, Object> request) {
        String nodeId = (String) request.get("nodeId");
        if (nodeId == null) {
            return AIServiceResponse.error("NodeId is required");
        }

        // Create task for monitoring
        BuildTask task = new BuildTask("summarize", request);
        
        try {
            ensureAgentInitialized();
            
            // Record task start
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskStart(task);
            }

            String mapId = (String) request.get("mapId");
            Integer maxWords = (Integer) request.get("maxWords");
            Boolean writeToNote = (Boolean) request.get("writeToNote");

            String prompt = buildSummarizePrompt(nodeId, mapId, maxWords, writeToNote);
            String summary = chatWithModel(
                "You are a professional summarizer. Return only the summary text, no extra content.",
                prompt
            );

            Map<String, Object> data = Map.of(
                "nodeId", nodeId,
                "summary", summary,
                "tokenUsage", Map.of(
                    "inputTokens", inputTokens.get(),
                    "outputTokens", outputTokens.get()
                )
            );

            // Record task completion
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, true);
            }

            return AIServiceResponse.success("Branch summarized", data);
        } catch (Exception e) {
            // Record task failure
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, false);
            }
            LogUtils.warn("DefaultAgentService.handleSummarize failed", e);
            return AIServiceResponse.error("Failed to summarize: " + e.getMessage());
        }
    }

    private AIServiceResponse handleTag(Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        java.util.List<String> nodeIds = (java.util.List<String>) request.get("nodeIds");
        if (nodeIds == null || nodeIds.isEmpty()) {
            return AIServiceResponse.error("NodeIds is required");
        }

        // Create task for monitoring
        BuildTask task = new BuildTask("tag", request);
        
        try {
            ensureAgentInitialized();
            
            // Record task start
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskStart(task);
            }

            String mapId = (String) request.get("mapId");
            String prompt = buildTagPrompt(nodeIds, mapId);
            String result = chatWithModel(
                "You are a tagging expert. Return only valid JSON with a 'tags' array, no markdown, no explanation.",
                prompt
            );

            Map<String, Object> data = Map.of(
                "nodeIds", nodeIds,
                "result", result,
                "tokenUsage", Map.of(
                    "inputTokens", inputTokens.get(),
                    "outputTokens", outputTokens.get()
                )
            );

            // Record task completion
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, true);
            }

            return AIServiceResponse.success("Tags generated", data);
        } catch (Exception e) {
            // Record task failure
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, false);
            }
            LogUtils.warn("DefaultAgentService.handleTag failed", e);
            return AIServiceResponse.error("Failed to generate tags: " + e.getMessage());
        }
    }

    /**
     * Called by BuildTaskExecutor to ensure the agent has been fully initialized.
     * Public to allow cross-package access (required by the scheduling package).
     */
    public void ensureAgentInitializedPublic() {
        ensureAgentInitialized();
    }

    /**
     * Streaming interface for branch summarization.
     * Reuses buildSummarizePrompt to construct the prompt, then delivers tokens
     * via StreamingChatModel callbacks.
     *
     * @param nodeId   node ID
     * @param mapId    map ID (may be null)
     * @param maxWords maximum summary word count (may be null, defaults to 100)
     * @param handler  SSE callback handler
     */
    public void summarizeStream(String nodeId, String mapId, Integer maxWords,
                                StreamingChatResponseHandler handler) {
        ensureAgentInitialized();
        if (streamingChatModel == null) {
            handler.onError(new UnsupportedOperationException("Streaming not configured for current model"));
            return;
        }
        String prompt = buildSummarizePrompt(nodeId, mapId, maxWords, null);
        java.util.List<dev.langchain4j.data.message.ChatMessage> messages = java.util.Arrays.asList(
            SystemMessage.from("You are a professional summarizer. Return only the summary text, no extra content."),
            UserMessage.from(prompt)
        );
        streamingChatModel.chat(messages, handler);
    }

    private void ensureAgentInitialized() {
        if (agentService == null) {
            synchronized (DefaultAgentService.class) {
                if (agentService == null) {
                    try {
                        AIProviderConfiguration configuration = new AIProviderConfiguration();
                        if (!isProviderConfigured(configuration)) {
                            LogUtils.warn("DefaultAgentService: No AI provider configured");
                            return;
                        }

                        availableMaps = new AvailableMaps(new ControllerMapModelProvider());
                        bufferLayerRouter = new BufferLayerRouter();

                        ToolCallSummaryHandler toolCallSummaryHandler = summary -> {
                            LogUtils.info("Agent tool call: " + summary.getSummaryText());
                        };

                        AvailableMaps.MapAccessListener mapAccessListener = (mapId, mapModel) -> {
                            LogUtils.info("Map accessed: " + mapId);
                        };

                        toolSet = new AIToolSetBuilder()
                            .toolCallSummaryHandler(toolCallSummaryHandler)
                            .availableMaps(availableMaps)
                            .mapAccessListener(mapAccessListener)
                            .toolCaller(org.freeplane.plugin.ai.tools.utilities.ToolCaller.CHAT)
                            .build();

                        ChatTokenUsageTracker tokenTracker = new ChatTokenUsageTracker(totals -> {
                            inputTokens.addAndGet((int) totals.getInputTokenCount());
                            outputTokens.addAndGet((int) totals.getOutputTokenCount());
                        });

                        agentService = AIChatServiceFactory.createService(
                            toolSet,
                            null,
                            tokenTracker,
                            toolCallSummaryHandler,
                            () -> false,
                            usage -> {}
                        );

                        // initialize the underlying ChatModel (for generate/expand/summarize, bypassing tool registration)
                        chatModel = AIChatModelFactory.createChatLanguageModel(configuration);
                        // initialize the streaming ChatModel (for summarizeStream)
                        streamingChatModel = AIChatModelFactory.createStreamingChatModel(configuration);

                        // initialize the tool execution service
                        toolExecutionService = new DefaultToolExecutionService();
                        toolExecutionService.setToolSet(toolSet);

                        // initialize scheduling components
                        schedulingConfig = SchedulingConfig.getInstance();
                        schedulingMonitor = SchedulingMonitor.getInstance();
                        taskScheduler = BuildTaskScheduler.getInstance();
                        taskScheduler.start();

                        LogUtils.info("DefaultAgentService: Agent AIChatService initialized successfully");
                        LogUtils.info("DefaultAgentService: ToolExecutionService initialized successfully");
                        LogUtils.info("DefaultAgentService: Scheduling components initialized successfully");
                    } catch (Exception e) {
                        LogUtils.warn("DefaultAgentService: Failed to initialize Agent AIChatService", e);
                    }
                }
            }
        }
    }

    private boolean isProviderConfigured(AIProviderConfiguration configuration) {
        return isNonEmpty(configuration.getOpenRouterKey())
            || isNonEmpty(configuration.getGeminiKey())
            || configuration.hasOllamaServiceAddress()
            || configuration.hasErnieKey()
            || configuration.hasDashScopeKey();
    }

    private boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Sends a request directly via the underlying ChatModel without tool registration
     * or control-instruction system messages.
     * Used for generate/expand/summarize to avoid interference from AIChatService's
     * complex system message.
     */
    private String chatWithModel(String systemPrompt, String userPrompt) {
        if (chatModel == null) {
            throw new IllegalStateException("ChatModel not initialized");
        }
        ChatRequest request = ChatRequest.builder()
            .messages(Arrays.asList(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
            ))
            .build();
        ChatResponse response = chatModel.chat(request);
        return response.aiMessage().text();
    }

    private String buildMindMapPrompt(String topic, Map<String, Object> request) {
        // use MindMapPromptOptimizer to load CoT template from prompts.yaml
        org.freeplane.plugin.ai.buffer.BufferRequest bufferRequest =
            new org.freeplane.plugin.ai.buffer.BufferRequest("Generate mind map: " + topic);
        bufferRequest.setRequestType(org.freeplane.plugin.ai.buffer.BufferRequest.RequestType.MINDMAP_GENERATION);
        bufferRequest.addParameter("topic", topic);
        bufferRequest.addParameter("maxDepth", request.get("maxDepth") != null ? (Integer) request.get("maxDepth") : 3);
        bufferRequest.addParameter("language", "zh");
        
        return promptOptimizer.optimizePrompt(bufferRequest);
    }

    private String buildExpandNodePrompt(String nodeId, String mapId, Integer depth, Integer count, String focus) {
        if (depth == null) depth = 1;
        if (count == null) count = 3;
        if (focus == null) focus = "related content";

        // pre-fetch the real node text and context on the Java side
        String nodeText = nodeId;
        String contextInfo = "";
        try {
            if (availableMaps != null) {
                MapModel mapModel = availableMaps.getCurrentMapModel();
                if (mapModel != null) {
                    NodeModel node = mapModel.getNodeForID(nodeId);
                    if (node != null) {
                        nodeText = node.getText();
                        NodeModel parent = node.getParentNode();
                        if (parent != null) {
                            contextInfo += "\nParent node: " + parent.getText();
                        }
                        if (node.getChildCount() > 0) {
                            StringBuilder existingChildren = new StringBuilder("\nExisting children:");
                            for (int i = 0; i < Math.min(node.getChildCount(), 5); i++) {
                                existingChildren.append("\n- ").append(node.getChildAt(i).getText());
                            }
                            contextInfo += existingChildren.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.warn("DefaultAgentService: failed to read node text for " + nodeId, e);
        }

        // use MindMapPromptOptimizer to load CoT template from prompts.yaml
        org.freeplane.plugin.ai.buffer.BufferRequest bufferRequest =
            new org.freeplane.plugin.ai.buffer.BufferRequest("Expand node: " + nodeText);
        bufferRequest.setRequestType(org.freeplane.plugin.ai.buffer.BufferRequest.RequestType.NODE_EXPANSION);
        bufferRequest.addParameter("nodeText", nodeText);
        bufferRequest.addParameter("contextInfo", contextInfo);
        bufferRequest.addParameter("depth", depth);
        bufferRequest.addParameter("count", count);
        bufferRequest.addParameter("focus", focus);
        bufferRequest.addParameter("language", "zh");
        
        return promptOptimizer.optimizePrompt(bufferRequest);
    }

    private String buildSummarizePrompt(String nodeId, String mapId, Integer maxWords, Boolean writeToNote) {
        if (maxWords == null) maxWords = 100;

        // pre-fetch the full subtree text on the Java side
        String branchContent = "(unable to read node content)";
        try {
            if (availableMaps != null) {
                MapModel mapModel = availableMaps.getCurrentMapModel();
                if (mapModel != null) {
                    NodeModel node = mapModel.getNodeForID(nodeId);
                    if (node != null) {
                        branchContent = extractBranchText(node);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.warn("DefaultAgentService: failed to read branch text for " + nodeId, e);
        }

        // use MindMapPromptOptimizer to load CoT template from prompts.yaml
        org.freeplane.plugin.ai.buffer.BufferRequest bufferRequest =
            new org.freeplane.plugin.ai.buffer.BufferRequest("Summarize: " + branchContent.substring(0, Math.min(50, branchContent.length())));
        bufferRequest.setRequestType(org.freeplane.plugin.ai.buffer.BufferRequest.RequestType.BRANCH_SUMMARY);
        bufferRequest.addParameter("content", branchContent);
        bufferRequest.addParameter("maxWords", maxWords);
        bufferRequest.addParameter("language", "zh");
        
        return promptOptimizer.optimizePrompt(bufferRequest);
    }

    private String extractBranchText(NodeModel node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.getText());
        if (node.getChildCount() > 0) {
            sb.append("\n");
            extractChildrenText(node, sb, 1);
        }
        return sb.toString();
    }

    private void extractChildrenText(NodeModel node, StringBuilder sb, int depth) {
        if (depth > 5) return;
        String indent = "  ".repeat(depth);
        for (int i = 0; i < node.getChildCount(); i++) {
            NodeModel child = (NodeModel) node.getChildAt(i);
            sb.append(indent).append("- ").append(child.getText()).append("\n");
            if (child.getChildCount() > 0) {
                extractChildrenText(child, sb, depth + 1);
            }
        }
    }

    private String buildTagPrompt(java.util.List<String> nodeIds, String mapId) {
        return String.format(
            "You are a professional tag generation expert, skilled at generating concise and meaningful tags for mind map nodes.\n\n" +
            "Task: Generate appropriate tags for the following nodes.\n\n" +
            "Node list:\n%s\n\n" +
            "Detailed instructions:\n" +
            "1. Generate 1-3 tags per node\n" +
            "2. Tags should be concise and meaningful\n" +
            "3. Tags should be relevant to the node content\n\n" +
            "Important: Since you cannot access mind map nodes directly, follow these steps:\n" +
            "1. First use getSelectedMapAndNodeIdentifiers or readNodesWithDescendants to retrieve node information\n" +
            "2. Then use readNodesWithDescendants to read the content of each node\n" +
            "3. Generate tags based on the content\n" +
            "4. To write tags, use the following flow:\n" +
            "   a) For TAGS/ICONS: use the edit tool directly (no fetchNodesForEditing needed)\n" +
            "   b) For TEXT/DETAILS/NOTE: must call fetchNodesForEditing first to get originalContentType\n\n" +
            "Return format:\n" +
            "Return JSON with the ID and corresponding tag list for each node:\n" +
            "{\n" +
            "  \"tags\": [\n" +
            "    {\"nodeId\": \"<nodeId>\", \"tags\": [\"tag1\", \"tag2\"]},\n" +
            "    ...\n" +
            "  ]\n" +
            "}",
            nodeIds
        );
    }

    private AIServiceResponse handleExecuteTool(Map<String, Object> request) {
        // Create task for monitoring
        BuildTask task = new BuildTask("execute-tool", request);
        
        try {
            ensureAgentInitialized();
            
            // Record task start
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskStart(task);
            }

            String toolName = (String) request.get("toolName");
            if (toolName == null || toolName.trim().isEmpty()) {
                return AIServiceResponse.error("Tool name is required");
            }

            Map<String, Object> parameters = (Map<String, Object>) request.get("parameters");
            if (parameters == null) {
                parameters = new HashMap<>();
            }

            if (toolExecutionService == null) {
                return AIServiceResponse.error("Tool execution service not initialized");
            }

            if (!toolExecutionService.isToolSupported(toolName)) {
                return AIServiceResponse.error("Tool not supported: " + toolName);
            }

            Object result = toolExecutionService.executeTool(toolName, parameters);

            Map<String, Object> data = Map.of(
                "toolName", toolName,
                "result", result
            );

            // Record task completion
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, true);
            }

            return AIServiceResponse.success("Tool executed successfully", data);
        } catch (Exception e) {
            // Record task failure
            if (schedulingMonitor != null) {
                schedulingMonitor.recordTaskComplete(task, false);
            }
            LogUtils.warn("DefaultAgentService.handleExecuteTool failed", e);
            return AIServiceResponse.error("Failed to execute tool: " + e.getMessage());
        }
    }

    @Override
    public boolean canHandle(Map<String, Object> request) {
        String serviceType = (String) request.get("serviceType");
        return AIServiceType.AGENT.getCode().equals(serviceType);
    }

    @Override
    public int getPriority() {
        return 20;
    }
}
