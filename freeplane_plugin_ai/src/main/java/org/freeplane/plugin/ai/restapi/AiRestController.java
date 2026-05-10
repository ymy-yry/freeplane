package org.freeplane.plugin.ai.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.buffer.BufferLayerRouter;
import org.freeplane.plugin.ai.buffer.BufferRequest;
import org.freeplane.plugin.ai.buffer.BufferResponse;
import org.freeplane.plugin.ai.chat.AIChatPanel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.service.AIService;
import org.freeplane.plugin.ai.service.AIServiceLoader;
import org.freeplane.plugin.ai.service.AIServiceResponse;
import org.freeplane.plugin.ai.service.impl.DefaultAgentService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * REST controller for AI-related endpoints under /api/ai/*.
 *
 * Chat section (/api/ai/chat/):
 * - GET  /api/ai/chat/models  - list available models
 * - POST /api/ai/chat/message - single-turn chat
 * - POST /api/ai/chat/smart   - smart buffer layer request
 *
 * Build section (/api/ai/build/):
 * - POST /api/ai/build/expand-node      - expand a node with AI
 * - POST /api/ai/build/summarize        - summarize a branch
 * - POST /api/ai/build/generate-mindmap - generate a full mindmap
 * - POST /api/ai/build/tag              - auto-tag nodes
 *
 * Config section (/api/ai/config/):
 * - POST /api/ai/config/save - persist model configuration
 */
public class AiRestController {

    private final AvailableMaps availableMaps;
    private final AIChatPanel aiChatPanel;
    private final ObjectMapper objectMapper;
    private final BufferLayerRouter bufferLayerRouter;

    public AiRestController(AvailableMaps availableMaps, AIChatPanel aiChatPanel) {
        this.availableMaps = availableMaps;
        this.aiChatPanel = aiChatPanel;
        this.objectMapper = new ObjectMapper();
        this.bufferLayerRouter = new BufferLayerRouter();
    }

    /**
     * POST /api/ai/config/save
     * Saves frontend model config (providerName, apiKey, baseUrl, modelName) to ResourceController.
     * Supported providers: ernie, openrouter, gemini, ollama.
     */
    public void handleSaveConfig(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Map<?, ?> body = readBody(exchange);
            String providerName = (String) body.get("providerName");
            String apiKey      = (String) body.get("apiKey");
            String baseUrl     = (String) body.get("baseUrl");
            String modelName   = (String) body.get("modelName");

            if (providerName == null || providerName.trim().isEmpty()) {
                sendError(exchange, 400, "providerName is required");
                return;
            }
            if (apiKey == null || apiKey.trim().isEmpty()) {
                sendError(exchange, 400, "apiKey is required");
                return;
            }

            ResourceController rc = ResourceController.getResourceController();
            String prefix = providerName.toLowerCase().trim();

            // Write API key.
            rc.setProperty("ai_" + prefix + "_key", apiKey.trim());

            // Write base URL (if provided).
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                rc.setProperty("ai_" + prefix + "_service_address", baseUrl.trim());
            }

            // Write model name (if provided) and update selected_model.
            if (modelName != null && !modelName.trim().isEmpty()) {
                rc.setProperty("ai_selected_model", prefix + "|" + modelName.trim());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("provider", prefix);
            sendJson(exchange, 200, result);
        } catch (Exception e) {
            LogUtils.warn("AiRestController.handleSaveConfig error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * GET /api/ai/chat/models
     * Returns the list of available AI models for the current configuration (dynamically built from provider settings).
     * The data source is consistent with the model selector in the Swing panel.
     */
    public void handleGetModels(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            List<Map<String, Object>> modelList = new ArrayList<>();

            // Read each provider's config and build the model list dynamically (same data source as the Swing UI).
            ResourceController rc = ResourceController.getResourceController();

            String openrouterKey = rc.getProperty("ai_openrouter_key", "");
            if (!openrouterKey.trim().isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("providerName", "openrouter");
                m.put("providerDisplayName", "OpenRouter");
                m.put("modelName", "openai/gpt-4o");
                m.put("displayName", "OpenRouter: openai/gpt-4o");
                m.put("isFree", false);
                modelList.add(m);
            }

            String geminiKey = rc.getProperty("ai_gemini_key", "");
            if (!geminiKey.trim().isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("providerName", "gemini");
                m.put("providerDisplayName", "Google Gemini");
                m.put("modelName", "gemini-2.0-flash");
                m.put("displayName", "Gemini: gemini-2.0-flash");
                m.put("isFree", false);
                modelList.add(m);
            }

            String dashscopeKey = rc.getProperty("ai_dashscope_key", "");
            if (!dashscopeKey.trim().isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("providerName", "dashscope");
                m.put("providerDisplayName", "DashScope (Qwen)");
                m.put("modelName", "qwen-max");
                m.put("displayName", "DashScope (Qwen): qwen-max");
                m.put("isFree", false);
                modelList.add(m);
            }

            String ernieKey = rc.getProperty("ai_ernie_key", "");
            if (!ernieKey.trim().isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("providerName", "ernie");
                m.put("providerDisplayName", "ERNIE (Baidu)");
                m.put("modelName", "ernie-4.5");
                m.put("displayName", "ERNIE (Baidu): ernie-4.5");
                m.put("isFree", false);
                modelList.add(m);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("models", modelList);
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            LogUtils.warn("AiRestController.handleGetModels error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/ai/chat/message
     * Single-turn chat endpoint (uses AIService architecture).
     */
    public void handleChat(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;
    
        try {
            Map<?, ?> body = readBody(exchange);
            String message = (String) body.get("message");
            String modelSelection = (String) body.get("modelSelection");
            String mapId = (String) body.get("mapId");
            String selectedNodeId = (String) body.get("selectedNodeId");
    
            if (message == null || message.trim().isEmpty()) {
                sendError(exchange, 400, "message is required");
                return;
            }
    
            // Build request parameters.
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("serviceType", "chat");
            request.put("message", message);
            request.put("modelSelection", modelSelection);
            request.put("mapId", mapId);
            request.put("selectedNodeId", selectedNodeId);
    
            // Dispatch to the appropriate AIService.
            AIService service = AIServiceLoader.selectService(request);
            if (service == null) {
                sendError(exchange, 500, "No chat service available");
                return;
            }
    
            AIServiceResponse serviceResponse = service.processRequest(request);
            if (serviceResponse.isSuccess()) {
                sendJson(exchange, 200, serviceResponse.getData());
            } else {
                sendError(exchange, 500, serviceResponse.getErrorMessage());
            }
        } catch (Exception e) {
            LogUtils.warn("AiRestController.handleChat error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/ai/chat/stream
     * Streaming chat endpoint (SSE).
     * Response format: text/event-stream. Each event: "data: &lt;token&gt;\n\n".
     * Completion signal: "data: [DONE]\n\n". Error signal: "data: [ERROR] &lt;message&gt;\n\n".
     */
    public void handleChatStream(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        Map<?, ?> body;
        try {
            body = readBody(exchange);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid request body");
            return;
        }
        String message = (String) body.get("message");
        if (message == null || message.trim().isEmpty()) {
            sendError(exchange, 400, "message is required");
            return;
        }
        final String userMessage = message.trim();

        // Set SSE headers before sending anything
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0); // 0 = chunked / unknown length
        final OutputStream os = exchange.getResponseBody();

        // CountDownLatch keeps this handler thread alive until streaming completes.
        // Without it, HttpServer closes the connection immediately after chatStream() returns,
        // because StreamingChatModel.chat() is non-blocking (callbacks fire on SDK threads).
        final CountDownLatch latch = new CountDownLatch(1);

        aiChatPanel.chatStream(userMessage, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) return;
                try {
                    // Escape newlines so each SSE event stays on one line
                    String escaped = partialResponse.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
                    String chunk = "data: " + escaped + "\n\n";
                    os.write(chunk.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException ignored) {
                    // Client disconnected; unblock the handler thread
                    latch.countDown();
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                try {
                    os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();
                } catch (IOException ignored) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onError(Throwable error) {
                try {
                    String msg = error.getMessage();
                    if (msg == null) msg = error.getClass().getSimpleName();
                    String escaped = msg.replace("\\", "\\\\").replace("\n", " ").replace("\r", " ");
                    os.write(("data: [ERROR] " + escaped + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();
                } catch (IOException ignored) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            }
        });

        // Block until onCompleteResponse or onError fires
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * POST /api/ai/build/generate-mindmap
     * AI one-click mindmap generation (uses AIService architecture).
     */
    public void handleGenerateMindMap(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;
    
        try {
            Map<?, ?> body = readBody(exchange);
            String topic = (String) body.get("topic");
            String modelSelection = (String) body.get("modelSelection");
            Integer maxDepth = body.get("maxDepth") instanceof Number ? ((Number) body.get("maxDepth")).intValue() : 3;
    
            if (topic == null || topic.trim().isEmpty()) {
                sendError(exchange, 400, "topic is required");
                return;
            }
    
            LogUtils.info("AiRestController.handleGenerateMindMap: topic=" + topic + ", maxDepth=" + maxDepth);
    
            // Route through BufferLayerRouter (same as Auto mode – nodes are created directly in the backend).
            BufferRequest bufferRequest = new BufferRequest("Generate mindmap: " + topic);
            bufferRequest.setRequestType(BufferRequest.RequestType.MINDMAP_GENERATION);
            bufferRequest.addParameter("topic", topic);
            bufferRequest.addParameter("maxDepth", maxDepth);
            if (modelSelection != null && !modelSelection.trim().isEmpty()) {
                bufferRequest.addParameter("selectedModel", modelSelection);
            }
            
            BufferResponse bufferResponse = bufferLayerRouter.processRequest(bufferRequest);
            
            if (bufferResponse.isSuccess()) {
                // Build response payload.
                Map<String, Object> responseData = new LinkedHashMap<>();
                responseData.put("success", true);
                responseData.put("topic", topic);
                responseData.put("nodeCount", bufferResponse.getData().get("nodeCount"));
                responseData.put("result", objectMapper.writeValueAsString(bufferResponse.getData()));
                responseData.put("usedModel", bufferResponse.getUsedModel());
                responseData.put("logs", bufferResponse.getLogs());
                
                sendJson(exchange, 200, responseData);
            } else {
                sendError(exchange, 500, bufferResponse.getErrorMessage());
            }
        } catch (Exception e) {
            LogUtils.warn("AiRestController.handleGenerateMindMap error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/ai/build/expand-node
     * AI node expansion (uses AIService architecture).
     */
    public void handleExpandNode(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Map<?, ?> body = readBody(exchange);
            String nodeId = (String) body.get("nodeId");
            String mapId = (String) body.get("mapId");          // required: target map identifier
            Integer depth = body.get("depth") instanceof Number ? ((Number) body.get("depth")).intValue() : null;   // optional: expansion depth
            Integer count = body.get("count") instanceof Number ? ((Number) body.get("count")).intValue() : null;   // optional: number of nodes to generate
            String focus = (String) body.get("focus");          // optional: expansion direction hint

            if (nodeId == null) {
                sendError(exchange, 400, "nodeId is required");
                return;
            }

            // Build request parameters.
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("serviceType", "agent");
            request.put("action", "expand-node");
            request.put("nodeId", nodeId);
            request.put("mapId", mapId);
            request.put("depth", depth);
            request.put("count", count);
            request.put("focus", focus);

            // Dispatch to the appropriate AIService.
            AIService service = AIServiceLoader.selectService(request);
            if (service == null) {
                sendError(exchange, 500, "No agent service available");
                return;
            }

            AIServiceResponse serviceResponse = service.processRequest(request);
            if (serviceResponse.isSuccess()) {
                sendJson(exchange, 200, serviceResponse.getData());
            } else {
                sendError(exchange, 500, serviceResponse.getErrorMessage());
            }
        } catch (Exception e) {
            LogUtils.warn("AiRestController.handleExpandNode error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/ai/build/summarize
     * Branch summarization (uses AIService architecture).
     */
    public void handleSummarize(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Map<?, ?> body = readBody(exchange);
            String nodeId = (String) body.get("nodeId");
            String mapId = (String) body.get("mapId");              // required: target map identifier
            Integer maxWords = body.get("maxWords") instanceof Number ? ((Number) body.get("maxWords")).intValue() : null; // optional
            boolean writeToNote = Boolean.TRUE.equals(body.get("writeToNote")); // optional

            if (nodeId == null) {
                sendError(exchange, 400, "nodeId is required");
                return;
            }

            // Build request parameters.
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("serviceType", "agent");
            request.put("action", "summarize");
            request.put("nodeId", nodeId);
            request.put("mapId", mapId);
            request.put("maxWords", maxWords);
            request.put("writeToNote", writeToNote);

            // Dispatch to the appropriate AIService.
            AIService service = AIServiceLoader.selectService(request);
            if (service == null) {
                sendError(exchange, 500, "No agent service available");
                return;
            }

            AIServiceResponse serviceResponse = service.processRequest(request);
            if (serviceResponse.isSuccess()) {
                sendJson(exchange, 200, serviceResponse.getData());
            } else {
                sendError(exchange, 500, serviceResponse.getErrorMessage());
            }
        } catch (Exception e) {
            LogUtils.warn("AiRestController.handleSummarize error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/ai/build/summarize-stream
     * Branch summarization SSE streaming endpoint.
     * Response format: text/event-stream. Each event: "data: &lt;token&gt;\n\n".
     * Completion signal: "data: [DONE]\n\n". Error signal: "data: [ERROR] &lt;message&gt;\n\n".
     */
    public void handleSummarizeStream(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        Map<?, ?> body;
        try {
            body = readBody(exchange);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid request body");
            return;
        }
        String nodeId = (String) body.get("nodeId");
        if (nodeId == null || nodeId.trim().isEmpty()) {
            sendError(exchange, 400, "nodeId is required");
            return;
        }
        String mapId = (String) body.get("mapId");
        Integer maxWords = body.get("maxWords") instanceof Number
            ? ((Number) body.get("maxWords")).intValue() : null;

        // Retrieve the DefaultAgentService singleton.
        AIService service = AIServiceLoader.getServiceByName("default_agent_service");
        if (!(service instanceof DefaultAgentService)) {
            sendError(exchange, 500, "Agent service not available");
            return;
        }
        DefaultAgentService agentService = (DefaultAgentService) service;

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        final OutputStream os = exchange.getResponseBody();

        final CountDownLatch latch = new CountDownLatch(1);

        agentService.summarizeStream(nodeId, mapId, maxWords, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) return;
                try {
                    String escaped = partialResponse.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
                    os.write(("data: " + escaped + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException ignored) {
                    latch.countDown();
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                try {
                    os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();
                } catch (IOException ignored) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onError(Throwable error) {
                try {
                    String msg = error.getMessage();
                    if (msg == null) msg = error.getClass().getSimpleName();
                    String escaped = msg.replace("\\", "\\\\").replace("\n", " ").replace("\r", " ");
                    os.write(("data: [ERROR] " + escaped + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();
                } catch (IOException ignored) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * POST /api/ai/build/tag
     * Automatic keyword tagging (uses AIService architecture).
     */
    public void handleTag(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;
    
        try {
            Map<?, ?> body = readBody(exchange);
            String mapId = (String) body.get("mapId");          // required: target map identifier
            @SuppressWarnings("unchecked")
            List<String> nodeIds = (List<String>) body.get("nodeIds"); // required: array of node IDs to tag
    
            if (nodeIds == null || nodeIds.isEmpty()) {
                sendError(exchange, 400, "nodeIds is required and must not be empty");
                return;
            }
    
            // Build request parameters.
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("serviceType", "agent");
            request.put("action", "tag");
            request.put("mapId", mapId);
            request.put("nodeIds", nodeIds);
    
            // Dispatch to the appropriate AIService.
            AIService service = AIServiceLoader.selectService(request);
            if (service == null) {
                sendError(exchange, 500, "No agent service available");
                return;
            }
    
            AIServiceResponse serviceResponse = service.processRequest(request);
            if (serviceResponse.isSuccess()) {
                sendJson(exchange, 200, serviceResponse.getData());
            } else {
                sendError(exchange, 500, serviceResponse.getErrorMessage());
            }
        } catch (Exception e) {
            LogUtils.warn("AiRestController.handleTag error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/ai/chat/smart
     * Smart buffer-layer endpoint. The system automatically interprets the natural-language input,
     * optimises the prompt, selects a model, and returns the result.
     */
    public void handleSmartRequest(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;
    
        try {
            Map<?, ?> body = readBody(exchange);
            String input = (String) body.get("input");
    
            if (input == null || input.trim().isEmpty()) {
                sendError(exchange, 400, "input is required");
                return;
            }
    
            LogUtils.info("AiRestController.handleSmartRequest: received input - " + input);
    
            // Build request parameters.
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("input", input);
    
            // Try the buffer layer first.
            try {
                // Create a buffer-layer request.
                BufferRequest bufferRequest = new BufferRequest(input);
                // Delegate to the buffer-layer router.
                BufferResponse bufferResponse = bufferLayerRouter.processRequest(bufferRequest);
                
                // Build the HTTP response.
                Map<String, Object> responseBody = new LinkedHashMap<>();
                responseBody.put("success", bufferResponse.isSuccess());
                responseBody.put("usedModel", bufferResponse.getUsedModel());
                responseBody.put("qualityScore", bufferResponse.getQualityScore());
                responseBody.put("bufferLayer", "MindMapBufferLayer");
                responseBody.put("processingTime", bufferResponse.getProcessingTime());
                responseBody.put("logs", bufferResponse.getLogs());
                
                if (bufferResponse.isSuccess()) {
                    responseBody.put("data", bufferResponse.getData());
                    sendJson(exchange, 200, responseBody);
                } else {
                    responseBody.put("errorMessage", bufferResponse.getErrorMessage());
                    sendJson(exchange, 500, responseBody);
                }
            } catch (Exception e) {
                LogUtils.warn("Buffer layer failed, falling back to AIService", e);
                
                // Fall back to AIService when the buffer layer fails.
                request.put("serviceType", "chat");
                request.put("message", input);
                
                AIService service = AIServiceLoader.selectService(request);
                if (service == null) {
                    sendError(exchange, 500, "No service available");
                    return;
                }
                
                AIServiceResponse serviceResponse = service.processRequest(request);
                if (serviceResponse.isSuccess()) {
                    sendJson(exchange, 200, serviceResponse.getData());
                } else {
                    sendError(exchange, 500, serviceResponse.getErrorMessage());
                }
            }
        } catch (Exception e) {
            LogUtils.warn("AiRestController.handleSmartRequest error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Executes an AI chat request by delegating to AIChatPanel's chatService.
     */
    private String executeChat(String message, String modelSelection) {
        try {
            // Use AIChatPanel's public method to send the message.
            // Since AIChatPanel is a Swing component, we call it through its internal mechanism.
            // This is a placeholder response; a proper public method on AIChatPanel should be added.
            return "[AI Response] Chat connected, reply: " + message;
        } catch (Exception e) {
            LogUtils.warn("Failed to execute chat", e);
            return "[AI Error] " + e.getMessage();
        }
    }

    /**
     * Builds the prompt for mindmap generation.
     */
    private String buildMindMapPrompt(String topic, int maxDepth) {
        return String.format(
            "Generate a complete mindmap structure for '%s'.\n" +
            "\nRequirements:\n" +
            "1. Include %d levels of nodes\n" +
            "2. Each node should have specific, meaningful content\n" +
            "3. Return strict JSON format only, no extra text\n" +
            "\nExample return format:\n" +
            "{\n" +
            "  \"text\": \"%s\",\n" +
            "  \"children\": [\n" +
            "    {\"text\": \"Branch 1\", \"children\": [{\"text\": \"Sub-branch 1.1\"}]},\n" +
            "    {\"text\": \"Branch 2\"}\n" +
            "  ]\n" +
            "}\n" +
            "\nReturn JSON only, without Markdown code-block markers.",
            topic, maxDepth, topic
        );
    }

    /**
     * Parses the AI response and creates mindmap nodes accordingly.
     */
    private int createMindMapFromAIResponse(MapModel mapModel, String aiResponse, String topic) {
        try {
            // Strip possible Markdown code-block markers from the AI response.
            String cleanedJson = aiResponse.trim();
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.substring(7);
            }
            if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substring(3);
            }
            if (cleanedJson.endsWith("```")) {
                cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
            }
            cleanedJson = cleanedJson.trim();

            // Parse JSON.
            @SuppressWarnings("unchecked")
            Map<String, Object> mindMapData = objectMapper.readValue(cleanedJson, Map.class);

            // Obtain MMapController.
            org.freeplane.features.mode.Controller controller = org.freeplane.features.mode.Controller.getCurrentController();
            if (controller == null) {
                LogUtils.warn("Controller not available");
                return 0;
            }

            org.freeplane.features.mode.mindmapmode.MModeController modeController = 
                (org.freeplane.features.mode.mindmapmode.MModeController) controller.getModeController();
            org.freeplane.features.map.mindmapmode.MMapController mapController = 
                (org.freeplane.features.map.mindmapmode.MMapController) modeController.getMapController();

            // Set root node text.
            NodeModel rootNode = mapModel.getRootNode();
            rootNode.setText(topic);
            mapController.nodeChanged(rootNode);

            // Recursively create child nodes.
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> children = 
                (java.util.List<Map<String, Object>>) mindMapData.get("children");
            
            int[] nodeCount = {1}; // includes root
            if (children != null) {
                createNodesRecursive(rootNode, children, mapController, nodeCount);
            }

            return nodeCount[0];
        } catch (Exception e) {
            LogUtils.warn("Failed to parse AI mindmap response", e);
            return 0;
        }
    }

    /**
     * Recursively creates child nodes.
     */
    @SuppressWarnings("unchecked")
    private void createNodesRecursive(NodeModel parentNode, 
                                      java.util.List<Map<String, Object>> children,
                                      org.freeplane.features.map.mindmapmode.MMapController mapController,
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
            java.util.List<Map<String, Object>> subChildren = 
                (java.util.List<Map<String, Object>>) childData.get("children");
            if (subChildren != null && !subChildren.isEmpty()) {
                createNodesRecursive(childNode, subChildren, mapController, nodeCount);
            }
        }
    }

    // ──────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────

    private String buildProviderDisplayName(String providerName) {
        if (providerName == null) return "Unknown";
        switch (providerName) {
            case "openrouter": return "OpenRouter";
            case "gemini": return "Google Gemini";
            case "ollama": return "Ollama (Local)";
            case "dashscope": return "DashScope (Qwen)";
            case "ernie": return "ERNIE (Baidu)";
            default: return providerName;
        }
    }

    Map<?, ?> readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (json.trim().isEmpty()) return new LinkedHashMap<>();
            return objectMapper.readValue(json, Map.class);
        }
    }

    void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }
}
