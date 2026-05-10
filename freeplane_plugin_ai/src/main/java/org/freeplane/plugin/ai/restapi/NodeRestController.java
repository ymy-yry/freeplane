package org.freeplane.plugin.ai.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.ai.maps.AvailableMaps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for node CRUD operations under /api/nodes/*.
 * All write operations are performed through Freeplane's MMapController
 * to keep state consistent with the Swing UI.
 */
public class NodeRestController {

    private final AvailableMaps availableMaps;
    private final ObjectMapper objectMapper;

    public NodeRestController(AvailableMaps availableMaps) {
        this.availableMaps = availableMaps;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * POST /api/nodes/search
     * Keyword search: traverses all node texts in the current map and returns matches.
     */
    public void handleSearch(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Map<?, ?> body = readBody(exchange);
            String query = (String) body.get("query");
            boolean caseSensitive = Boolean.TRUE.equals(body.get("caseSensitive"));

            MapModel mapModel = resolveMapModel(body);
            if (mapModel == null) {
                sendError(exchange, 404, "No map is currently open");
                return;
            }

            List<Map<String, Object>> results = new ArrayList<>();
            searchNodes(mapModel.getRootNode(), query, caseSensitive, results, "");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("results", results);
            response.put("totalCount", results.size());
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            LogUtils.warn("NodeRestController.handleSearch error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/nodes/create
     * Creates a new child node under the given parent via Freeplane MMapController (runs on the EDT).
     */
    public void handleCreate(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Map<?, ?> body = readBody(exchange);
            String parentId = (String) body.get("parentId");
            String text = (String) body.get("text");

            if (parentId == null || text == null) {
                sendError(exchange, 400, "parentId and text are required");
                return;
            }

            MapModel mapModel = resolveMapModel(body);
            if (mapModel == null) {
                sendError(exchange, 404, "No map is currently open");
                return;
            }

            NodeModel parentNode = mapModel.getNodeForID(parentId);
            if (parentNode == null) {
                sendError(exchange, 404, "Parent node not found: " + parentId);
                return;
            }

            // Use MMapController to create the node on the Swing EDT so the UI refreshes synchronously.
            MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
            final String finalText = text;
            NodeModel newNode = mapController.addNewNode(parentNode, parentNode.getChildCount(),
                node -> node.setText(finalText));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("nodeId", newNode.getID());
            response.put("text", text);
            response.put("parentId", parentId);
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            LogUtils.warn("NodeRestController.handleCreate error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/nodes/edit
     * Updates the text content of a node.
     */
    public void handleEdit(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Map<?, ?> body = readBody(exchange);
            String nodeId = (String) body.get("nodeId");
            String text = (String) body.get("text");

            if (nodeId == null || text == null) {
                sendError(exchange, 400, "nodeId and text are required");
                return;
            }

            MapModel mapModel = resolveMapModel(body);
            if (mapModel == null) {
                sendError(exchange, 404, "No map is currently open");
                return;
            }

            NodeModel node = mapModel.getNodeForID(nodeId);
            if (node == null) {
                sendError(exchange, 404, "Node not found: " + nodeId);
                return;
            }

            MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
            final String finalText2 = text;
            node.setText(finalText2);
            mapController.nodeChanged(node);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("nodeId", nodeId);
            response.put("text", text);
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            LogUtils.warn("NodeRestController.handleEdit error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/nodes/toggle-fold
     * Toggles the folded/expanded state of a node.
     */
    public void handleToggleFold(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Map<?, ?> body = readBody(exchange);
            String nodeId = (String) body.get("nodeId");
            Boolean folded = (Boolean) body.get("folded");

            if (nodeId == null || folded == null) {
                sendError(exchange, 400, "nodeId and folded are required");
                return;
            }

            MapModel mapModel = resolveMapModel(body);
            if (mapModel == null) {
                sendError(exchange, 404, "No map is currently open");
                return;
            }

            NodeModel node = mapModel.getNodeForID(nodeId);
            if (node == null) {
                sendError(exchange, 404, "Node not found: " + nodeId);
                return;
            }

            MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
            org.freeplane.features.filter.Filter filter = Controller.getCurrentController().getSelection().getFilter();
            mapController.setFolded(node, folded.booleanValue(), filter);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("nodeId", nodeId);
            response.put("folded", folded);
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            LogUtils.warn("NodeRestController.handleToggleFold error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/nodes/delete
     * Deletes the specified node (root node cannot be deleted).
     */
    public void handleDelete(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Map<?, ?> body = readBody(exchange);
            String nodeId = (String) body.get("nodeId");

            if (nodeId == null) {
                sendError(exchange, 400, "nodeId is required");
                return;
            }

            MapModel mapModel = resolveMapModel(body);
            if (mapModel == null) {
                sendError(exchange, 404, "No map is currently open");
                return;
            }

            NodeModel node = mapModel.getNodeForID(nodeId);
            if (node == null) {
                sendError(exchange, 404, "Node not found: " + nodeId);
                return;
            }

            if (node.isRoot()) {
                sendError(exchange, 400, "Cannot delete root node");
                return;
            }

            MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
            mapController.deleteNode(node);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("deleted", true);
            response.put("nodeId", nodeId);
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            LogUtils.warn("NodeRestController.handleDelete error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────

    /**
     * Resolves the target MapModel from the {@code mapId} field in the request body.
     * If {@code mapId} is present and valid, the matching map is returned;
     * otherwise falls back to the currently active map.
     */
    private MapModel resolveMapModel(Map<?, ?> body) {
        Object mapIdObj = body.get("mapId");
        if (mapIdObj instanceof String) {
            String mapIdStr = (String) mapIdObj;
            try {
                UUID mapId = UUID.fromString(mapIdStr);
                MapModel found = availableMaps.findMapModel(mapId);
                if (found != null) {
                    return found;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid mapId format – fall back to the current map.
            }
        }
        return availableMaps.getCurrentMapModel();
    }

    private void searchNodes(NodeModel node, String query, boolean caseSensitive,
                             List<Map<String, Object>> results, String path) {
        if (node == null) return;
        String text = node.getText();
        String searchTarget = caseSensitive ? text : text.toLowerCase();
        String searchQuery = caseSensitive ? query : query.toLowerCase();

        if (searchTarget.contains(searchQuery)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", node.getID());
            item.put("text", text);
            item.put("path", path.isEmpty() ? text : path + " > " + text);
            results.add(item);
        }

        String currentPath = path.isEmpty() ? text : path + " > " + text;
        for (NodeModel child : node.getChildren()) {
            searchNodes(child, query, caseSensitive, results, currentPath);
        }
    }

    Map<?, ?> readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
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
