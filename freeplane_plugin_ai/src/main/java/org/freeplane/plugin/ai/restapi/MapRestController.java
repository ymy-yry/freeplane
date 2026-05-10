package org.freeplane.plugin.ai.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.MapWriter;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.MMapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.validation.MindMapGenerationValidator;
import org.freeplane.plugin.ai.validation.MindMapValidationResult;
import org.freeplane.plugin.ai.validation.source.FileValidationSource;
import org.freeplane.plugin.ai.validation.source.ValidationSource;

import javax.swing.SwingUtilities;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for mindmap data endpoints under /api/map/* and /api/maps/*.
 * Serializes Freeplane in-memory MapModel/NodeModel objects to JSON for the frontend.
 */
public class MapRestController {

    private final AvailableMaps availableMaps;
    private final ObjectMapper objectMapper;

    public MapRestController(AvailableMaps availableMaps) {
        this.availableMaps = availableMaps;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * GET /api/map/current
     * Returns the node tree of the currently open mindmap in JSON format.
     */
    public void handleCurrentMap(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            MapModel mapModel = availableMaps.getCurrentMapModel();
            if (mapModel == null) {
                sendError(exchange, 404, "No map is currently open in Freeplane");
                return;
            }

            UUID mapId = availableMaps.getCurrentMapIdentifier();
            NodeModel rootNode = mapModel.getRootNode();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mapId", mapId != null ? mapId.toString() : "unknown");
            result.put("title", rootNode != null ? rootNode.getText() : "");
            result.put("root", rootNode != null ? serializeNode(rootNode) : null);

            sendJson(exchange, 200, result);
        } catch (Exception e) {
            LogUtils.warn("MapRestController.handleCurrentMap error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * GET /api/maps
     * Returns all open mindmaps as a list (metadata only, no node tree).
     */
    public void handleGetAllMaps(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            List<UUID> mapIds = availableMaps.getAvailableMapIdentifiers();
            List<Map<String, Object>> maps = new ArrayList<>();

            for (UUID mapId : mapIds) {
                MapModel mapModel = availableMaps.findMapModel(mapId);
                if (mapModel != null) {
                    NodeModel rootNode = mapModel.getRootNode();
                    Map<String, Object> mapInfo = new LinkedHashMap<>();
                    mapInfo.put("mapId", mapId.toString());
                    mapInfo.put("title", rootNode != null ? rootNode.getText() : "Untitled");
                    mapInfo.put("isCurrent", mapId.equals(availableMaps.getCurrentMapIdentifier()));
                    maps.add(mapInfo);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("maps", maps);
            result.put("count", maps.size());

            sendJson(exchange, 200, result);
        } catch (Exception e) {
            LogUtils.warn("MapRestController.handleGetAllMaps error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/maps/create
     * Creates a new empty mindmap with a default root node.
     * Optional request body: { "title": "New Map" }
     */
    public void handleCreateMap(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            Controller controller = Controller.getCurrentController();
            if (controller == null) {
                sendError(exchange, 500, "Freeplane controller not available");
                return;
            }

            ModeController modeController = controller.getModeController(MModeController.MODENAME);
            if (modeController == null) {
                sendError(exchange, 500, "Mindmap mode not available");
                return;
            }

            MapController mapController = modeController.getMapController();
            MapModel newMap = mapController.newMap();

            // parse optional title from request body
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body != null && !body.isEmpty()) {
                try {
                    Map<String, String> request = objectMapper.readValue(body, Map.class);
                    String title = request.get("title");
                    if (title != null && !title.isEmpty()) {
                        NodeModel rootNode = newMap.getRootNode();
                        if (rootNode != null) {
                            rootNode.setText(title);
                        }
                    }
                } catch (Exception e) {
                    LogUtils.warn("Failed to parse create map request body", e);
                }
            }

            UUID mapId = availableMaps.getOrCreateMapIdentifier(newMap);
            NodeModel rootNode = newMap.getRootNode();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mapId", mapId.toString());
            result.put("title", rootNode != null ? rootNode.getText() : "Untitled");
            result.put("success", true);

            sendJson(exchange, 201, result);
        } catch (Exception e) {
            LogUtils.warn("MapRestController.handleCreateMap error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/maps/switch
     * Switches focus to the specified mindmap.
     * Request body: { "mapId": "uuid-string" }
     */
    public void handleSwitchMap(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> request = objectMapper.readValue(body, Map.class);
            String mapIdStr = request.get("mapId");

            if (mapIdStr == null || mapIdStr.isEmpty()) {
                sendError(exchange, 400, "mapId is required");
                return;
            }

            UUID mapId;
            try {
                mapId = UUID.fromString(mapIdStr);
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid mapId format");
                return;
            }

            MapModel targetMap = availableMaps.findMapModel(mapId);
            if (targetMap == null) {
                sendError(exchange, 404, "Map not found: " + mapIdStr);
                return;
            }

            Controller controller = Controller.getCurrentController();
            if (controller == null) {
                sendError(exchange, 500, "Freeplane controller not available");
                return;
            }

            IMapViewManager mapViewManager = controller.getMapViewManager();
            if (mapViewManager == null) {
                sendError(exchange, 500, "Map view manager not available");
                return;
            }

            mapViewManager.changeToMap(targetMap);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mapId", mapId.toString());
            result.put("title", targetMap.getRootNode() != null ? targetMap.getRootNode().getText() : "Untitled");
            result.put("success", true);

            sendJson(exchange, 200, result);
        } catch (Exception e) {
            LogUtils.warn("MapRestController.handleSwitchMap error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * POST /api/maps/import
     * Imports a mindmap from a .mm XML string and switches to it.
     * Request body: { "content": "<map>...", "filename": "example.mm" }
     */
    public void handleImportMap(HttpExchange exchange) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> request = objectMapper.readValue(body, Map.class);

            String content = (String) request.get("content");
            String filename = request.containsKey("filename") ? (String) request.get("filename") : "imported.mm";

            if (content == null || content.trim().isEmpty()) {
                sendError(exchange, 400, "content is required");
                return;
            }

            Controller controller = Controller.getCurrentController();
            if (controller == null) {
                sendError(exchange, 500, "Freeplane controller not available");
                return;
            }

            ModeController modeController = controller.getModeController(MModeController.MODENAME);
            if (!(modeController instanceof MModeController)) {
                sendError(exchange, 500, "Mindmap mode not available");
                return;
            }
            MModeController mmodeController = (MModeController) modeController;
            MMapController mapController = (MMapController) mmodeController.getMapController();

            // Step 0: pre-validate for circular dependencies before parsing XML
            MindMapGenerationValidator validator = new MindMapGenerationValidator();
            ValidationSource source = new FileValidationSource(content, filename);
            MindMapValidationResult preValidation = validator.validate(source);

            if (preValidation.getErrors().stream()
                .anyMatch(e -> "CIRCULAR_DEPENDENCY".equals(e.getCode()))) {
                LogUtils.warn("MapRestController: import rejected due to circular dependency in " + filename);
                sendError(exchange, 400,
                    "Import failed: circular dependency detected - " +
                    preValidation.getErrors().stream()
                        .filter(e -> "CIRCULAR_DEPENDENCY".equals(e.getCode()))
                        .findFirst().map(e -> e.getMessage()).orElse("unknown cycle"));
                return;
            }

            // Step 1: parse XML on current thread (createNodeTreeFromXml is internally synchronized)
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(contentBytes);
            final MMapModel[] mapHolder = {null};
            final Exception[] errorHolder = {null};

            try {
                MMapModel parsedMap = new MMapModel(mapController.duplicator());
                try (java.io.InputStreamReader reader = new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    mapController.getMapReader().createNodeTreeFromXml(
                        parsedMap,
                        reader,
                        MapWriter.Mode.FILE
                    );
                }
                if (parsedMap.getRootNode() == null) {
                    parsedMap.createNewRoot();
                }
                mapHolder[0] = parsedMap;
            } catch (Exception e) {
                errorHolder[0] = e;
            }

            if (errorHolder[0] != null) {
                sendError(exchange, 500, "Failed to parse map content: " + errorHolder[0].getMessage());
                return;
            }

            final MMapModel newMap = mapHolder[0];

            // Step 2: register map on EDT (fireMapCreated / addLoadedMap / createMapView require EDT)
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        mapController.fireMapCreated(newMap);
                        mapController.addLoadedMap(newMap);
                        mapController.createMapView(newMap);
                    } catch (Exception e) {
                        errorHolder[0] = e;
                    }
                });
            } catch (Exception e) {
                sendError(exchange, 500, "Failed to create map view: " + e.getMessage());
                return;
            }

            if (errorHolder[0] != null) {
                sendError(exchange, 500, "Failed to register map: " + errorHolder[0].getMessage());
                return;
            }

            // set title: prefer root node text, fall back to filename without extension
            NodeModel rootNode = newMap.getRootNode();
            String title = (rootNode != null && !rootNode.getText().isEmpty())
                ? rootNode.getText()
                : filename.replaceAll("\\.mm$", "");

            UUID mapId = availableMaps.getOrCreateMapIdentifier(newMap);

            // Step 3: switch view to the newly imported map
            IMapViewManager mapViewManager = controller.getMapViewManager();
            if (mapViewManager != null) {
                SwingUtilities.invokeLater(() -> mapViewManager.changeToMap(newMap));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("mapId", mapId.toString());
            result.put("title", title);
            result.put("filename", filename);

            sendJson(exchange, 200, result);
        } catch (Exception e) {
            LogUtils.warn("MapRestController.handleImportMap error", e);
            sendError(exchange, 500, "Import failed: " + e.getMessage());
        }
    }

    /**
     * GET /api/maps/{mapId}
     * Returns the full node tree of the specified mindmap.
     */
    public void handleGetMapById(HttpExchange exchange, String mapId) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            UUID mapUuid;
            try {
                mapUuid = UUID.fromString(mapId);
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid mapId format");
                return;
            }

            MapModel mapModel = availableMaps.findMapModel(mapUuid);
            if (mapModel == null) {
                sendError(exchange, 404, "Map not found: " + mapId);
                return;
            }

            NodeModel rootNode = mapModel.getRootNode();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mapId", mapId);
            result.put("title", rootNode != null ? rootNode.getText() : "");
            result.put("root", rootNode != null ? serializeNode(rootNode) : null);

            sendJson(exchange, 200, result);
        } catch (Exception e) {
            LogUtils.warn("MapRestController.handleGetMapById error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * GET /api/nodes/{nodeId}
     * Returns detail information for a single node (attributes, notes, child list).
     */
    public void handleGetNode(HttpExchange exchange, String nodeId) throws IOException {
        CorsFilter.addCorsHeaders(exchange);
        if (CorsFilter.handlePreflight(exchange)) return;

        try {
            MapModel mapModel = availableMaps.getCurrentMapModel();
            if (mapModel == null) {
                sendError(exchange, 404, "No map is currently open");
                return;
            }

            NodeModel node = mapModel.getNodeForID(nodeId);
            if (node == null) {
                sendError(exchange, 404, "Node not found: " + nodeId);
                return;
            }

            sendJson(exchange, 200, serializeNodeDetail(node));
        } catch (Exception e) {
            LogUtils.warn("MapRestController.handleGetNode error", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Recursively serializes a node to a Map for JSON output, including the full child subtree.
     * Fields correspond exactly to the MindMapNode interface in the frontend types/mindmap.ts.
     */
    private Map<String, Object> serializeNode(NodeModel node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.getID());
        map.put("text", node.getText());
        map.put("parentId", node.getParentNode() != null ? node.getParentNode().getID() : null);
        map.put("folded", node.isFolded());
        map.put("note", ""); // note field requires NoteModel extension; simplified here

        List<Map<String, Object>> children = new ArrayList<>();
        for (NodeModel child : node.getChildren()) {
            children.add(serializeNode(child));
        }
        map.put("children", children);
        return map;
    }

    /**
     * Serializes node detail including attribute key-value pairs.
     */
    private Map<String, Object> serializeNodeDetail(NodeModel node) {
        Map<String, Object> map = serializeNode(node);
        // attributes: returning empty array for now; extend to read NodeAttributeTableModel as needed
        map.put("attributes", new ArrayList<>());
        return map;
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

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
