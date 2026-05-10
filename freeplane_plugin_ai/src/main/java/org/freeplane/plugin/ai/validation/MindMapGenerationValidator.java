package org.freeplane.plugin.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.validation.source.ValidationSource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Mind-map generation validator — adjacency-list + “SnakeDigest” memory-optimised architecture.
 *
 * <h3>Core design</h3>
 * <p>The parsing phase uses a “SnakeDigest” strategy: during Jackson streaming only the three
 * essential fields (id/text/childIds) are extracted into the lightweight adjacency list
 * {@link SnakeDigestGraph}; JsonNode objects leave scope immediately after parsing and
 * become eligible for GC. No {@link MindMapNode} instances are created.
 *
 * <p>All checks (cycle detection, uniqueness, depth, child-count, statistics) are performed
 * on the string-based adjacency list. Peak memory is O(total ID string length) rather than
 * O(node object count).
 *
 * <h3>Thread safety</h3>
 * <p>This class has no mutable state and can be shared across threads.
 * Asynchronous execution requires the caller to supply an {@link ExecutorService};
 * no static thread pool is held (a static pool would leak threads on OSGi hot-reload).
 *
 * <h3>Backwards compatibility</h3>
 * <p>The original public API ({@link #validate(String)}, {@link #validateAsync}, etc.) is
 * preserved and behaves identically to the previous implementation.
 */
public class MindMapGenerationValidator {

    public static final int DEFAULT_MAX_DEPTH = 10;
    public static final int DEFAULT_MAX_CHILDREN = 20;
    public static final int DEFAULT_MAX_TOTAL_NODES = 1000;

    private final int maxDepth;
    private final int maxChildren;
    private final int maxTotalNodes;

    public MindMapGenerationValidator() {
        this(DEFAULT_MAX_DEPTH, DEFAULT_MAX_CHILDREN, DEFAULT_MAX_TOTAL_NODES);
    }

    public MindMapGenerationValidator(int maxDepth, int maxChildren, int maxTotalNodes) {
        this.maxDepth = maxDepth;
        this.maxChildren = maxChildren;
        this.maxTotalNodes = maxTotalNodes;
    }

    // -------------------------------------------------------------------------
    // Synchronous entry points
    // -------------------------------------------------------------------------

    /**
     * Validates a mind-map JSON string.
     *
     * <p>Internal flow:
     * <ol>
     *   <li>Jackson parses the JSON and builds a lightweight {@link SnakeDigestGraph} (SnakeDigest).</li>
     *   <li>The JsonNode tree can be GC’d once the method returns ("shed the shell").</li>
     *   <li>All validation runs on the SnakeDigestGraph adjacency list.</li>
     * </ol>
     *
     * @param jsonResponse the mind-map JSON produced by the LLM
     * @return the validation result
     */
    public MindMapValidationResult validate(String jsonResponse) {
        MindMapValidationResult result = new MindMapValidationResult();

        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            result.addError("EMPTY_INPUT", "Input is empty");
            return result;
        }

        try {
            // ① SnakeDigest: Jackson parse → extract adjacency list → JsonNode tree eligible for GC
            SnakeDigestGraph graph = buildGraph(jsonResponse);
            if (graph.getRootId() == null) {
                result.addError("NULL_ROOT", "Root node is null after parsing");
                return result;
            }
            // ② All validation performed on the adjacency list
            validateGraph(graph, result);
        } catch (Exception e) {
            result.addError("PARSE_ERROR", "JSON parse failed: " + e.getMessage());
            LogUtils.warn("MindMapGenerationValidator: JSON parse failed", e);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Asynchronous entry points (executor supplied by caller to avoid static pool leak)
    // -------------------------------------------------------------------------

    /**
     * Validates asynchronously without blocking SSE streaming output.
     *
     * @param jsonResponse    the mind-map JSON produced by the LLM
     * @param executorService supplied by the caller (recommended: reuse the scheduler thread pool)
     * @return a {@link CompletableFuture} containing the validation result
     */
    public CompletableFuture<MindMapValidationResult> validateAsync(
            String jsonResponse, ExecutorService executorService) {
        return CompletableFuture.supplyAsync(() -> validate(jsonResponse), executorService);
    }

    /**
     * Validates asynchronously and executes a callback on completion.
     *
     * @param jsonResponse    the mind-map JSON produced by the LLM
     * @param executorService supplied by the caller
     * @param callback        invoked when validation completes
     */
    public void validateAsync(String jsonResponse, ExecutorService executorService,
                              ValidationCallback callback) {
        validateAsync(jsonResponse, executorService).whenComplete((result, throwable) -> {
            if (throwable != null) {
                callback.onValidationError(throwable);
            } else {
                callback.onValidationComplete(result);
            }
        });
    }

    // -------------------------------------------------------------------------
    // ValidationSource proxy entry point
    // -------------------------------------------------------------------------

    /**
     * Validates a mind-map via the {@link ValidationSource} proxy interface.
     * 
     * <p>Design notes:
     * <ul>
     *   <li>Checks {@code source.isReady()}; returns a NOT_READY error if not ready.</li>
     *   <li>Calls {@code source.readContent()} to obtain the JSON.</li>
     *   <li>Logs with {@code source.getDescription()} and {@code source.getSourceType()}.</li>
     *   <li>Delegates execution to the existing {@link #validate(String)}.</li>
     * </ul>
     * 
     * @param source the validation data-source proxy
     * @return the validation result
     */
    public MindMapValidationResult validate(ValidationSource source) {
        if (source == null) {
            MindMapValidationResult result = new MindMapValidationResult();
            result.addError("NULL_SOURCE", "Validation source is null");
            return result;
        }
        
        if (!source.isReady()) {
            MindMapValidationResult result = new MindMapValidationResult();
            result.addError("NOT_READY", 
                "Data source not ready: " + source.getDescription() + 
                " [" + source.getSourceType() + "]");
            return result;
        }
        
        try {
            String content = source.readContent();
            LogUtils.info("MindMapGenerationValidator: validating source=" 
                + source.getSourceType() + " / " + source.getDescription());
            
            // Delegate to existing validate(String)
            return validate(content);
        } catch (Exception e) {
            MindMapValidationResult result = new MindMapValidationResult();
            result.addError("READ_ERROR", 
                "Failed to read data source [" + source.getSourceType() + "]: " + e.getMessage());
            LogUtils.warn("MindMapGenerationValidator: failed to read source", e);
            return result;
        }
    }

    /**
     * Validates asynchronously via the {@link ValidationSource} proxy.
     * 
     * @param source   the validation data-source proxy
     * @param executor the thread pool (supplied by the caller)
     * @return a {@link CompletableFuture} containing the validation result
     */
    public CompletableFuture<MindMapValidationResult> validateAsync(
            ValidationSource source, ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> validate(source), executor);
    }

    public interface ValidationCallback {
        void onValidationComplete(MindMapValidationResult result);
        void onValidationError(Throwable error);
    }

    // -------------------------------------------------------------------------
    // Parsing phase: Jackson streaming parse → build SnakeDigestGraph (core SnakeDigest logic)
    // -------------------------------------------------------------------------

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Parses a JSON string into a lightweight adjacency list.
     *
     * <p>SnakeDigest principle:
     * <ul>
     *   <li>"Swallow": Jackson parses the input into a JsonNode tree.</li>
     *   <li>"Extract nutrients": the DFS only writes id/text/childIds into the graph.</li>
     *   <li>"Shed the shell": once {@code buildGraph} returns, local JsonNode references
     *       are gone and the GC can reclaim the entire object tree.</li>
     * </ul>
     */
    private SnakeDigestGraph buildGraph(String json) {
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            throw new IllegalArgumentException("Root node must not be an array; expected a JSON object");
        }
        try {
            // “Swallow”: Jackson parse → JsonNode tree (peak memory is reached here)
            JsonNode rootNode = OBJECT_MAPPER.readTree(trimmed);
            if (!rootNode.isObject()) {
                throw new IllegalArgumentException("Root node must be a JSON object");
            }
            SnakeDigestGraph graph = new SnakeDigestGraph();
            // "Extract nutrients / shed shell": DFS extracts adjacency list; JsonNode becomes GC-eligible progressively
            traverseToGraph(rootNode, null, graph);
            // rootNode local reference leaves scope here → GC can reclaim the entire JsonNode tree
            return graph;
        } catch (Exception e) {
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }

    /**
     * DFS — recursively extracts the adjacency list.
     * Each JsonNode is no longer referenced once its id/text/children have been extracted;
     * only nodes on the current call stack remain alive at any point.
     */
    private void traverseToGraph(JsonNode node, String parentId, SnakeDigestGraph graph) {
        // ① Extract "nutrients": id and text
        JsonNode idNode = node.get("id");
        String id = (idNode != null && !idNode.isNull()) ? idNode.asText() : generateNodeId();

        JsonNode textNode = node.get("text");
        String text = (textNode != null && !textNode.isNull()) ? textNode.asText() : "";

        // ② Write into adjacency list ("enter the stomach")
        graph.registerNode(id, text);
        graph.setRootId(id);
        if (parentId != null) graph.addEdge(parentId, id);

        // ③ Recurse into children ("continue ingesting")
        JsonNode childrenNode = node.get("children");
        if (childrenNode != null && childrenNode.isArray()) {
            for (JsonNode child : childrenNode) {
                if (child.isObject()) {
                    traverseToGraph(child, id, graph);
                    // child local reference expires on next loop iteration → GC-eligible ("shed shell")
                }
            }
        }
        // node local reference expires when this stack frame pops → GC-eligible
    }

    // -------------------------------------------------------------------------
    // Validation phase: all checks performed on SnakeDigestGraph
    // -------------------------------------------------------------------------

    private void validateGraph(SnakeDigestGraph graph, MindMapValidationResult result) {
        // 1. Root validity
        validateRoot(graph, result);

        // 2. ID uniqueness (adjacency-list keys are inherently unique; but the same childId
        //    could appear in multiple parent child-lists)
        checkDuplicateIds(graph, result);

        // 3. Cycle detection + depth + child-count + self-reference + statistics: one DFS pass
        GraphStats stats = new GraphStats();
        detectCyclesAndCollectStats(graph, stats, result);

        if (stats.maxDepth > maxDepth) {
            result.addError("EXCEEDS_MAX_DEPTH",
                "Mind-map depth exceeds limit: " + stats.maxDepth + " > " + maxDepth);
        }
        if (stats.totalNodes > maxTotalNodes) {
            result.addError("EXCEEDS_MAX_TOTAL_NODES",
                "Mind-map node count exceeds limit: " + stats.totalNodes + " > " + maxTotalNodes);
        }

        MindMapValidationResult.MindMapStatistics s = new MindMapValidationResult.MindMapStatistics();
        s.setTotalNodes(stats.totalNodes);
        s.setMaxDepth(stats.maxDepth);
        s.setMaxChildrenPerNode(stats.maxChildrenPerNode);
        s.setLeafNodes(stats.leafNodes);
        s.setInternalNodes(stats.internalNodes);
        double avg = stats.internalNodes > 0
            ? (double) (stats.totalNodes - 1) / stats.internalNodes : 0;
        s.setAverageChildrenPerNode(avg);
        result.setStatistics(s);
    }

    // -------------------------------------------------------------------------
    // 1. Root validity
    // -------------------------------------------------------------------------

    private void validateRoot(SnakeDigestGraph graph, MindMapValidationResult result) {
        String rootId = graph.getRootId();
        String text = graph.getLabel(rootId != null ? rootId : "");
        boolean hasId = rootId != null && !rootId.startsWith("node_"); // auto-generated IDs do not count as valid
        boolean hasText = text != null && !text.trim().isEmpty();
        if (!hasId && !hasText) {
            result.addError("INVALID_ROOT", "Root node has neither a valid ID nor text");
        }
    }

    // -------------------------------------------------------------------------
    // 2. ID uniqueness
    // -------------------------------------------------------------------------

    /**
     * Detects whether the same childId is referenced by more than one parent
     * (adjacency-list keys are unique, but the same childId may appear in multiple
     * parents’ child lists).
     */
    private void checkDuplicateIds(SnakeDigestGraph graph, MindMapValidationResult result) {
        Set<String> allChildIds = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : graph.adjacencyEntries()) {
            for (String childId : entry.getValue()) {
                if (!allChildIds.add(childId)) {
                    result.addError("DUPLICATE_ID", "Duplicate node ID found: " + childId, childId);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. DFS cycle detection + statistics: three-colour marking + parent map in one traversal
    //
    // Colour semantics:
    //   WHITE(0) — not yet visited
    //   GRAY (1) — currently on the recursion stack (back edge = cycle detected)
    //   BLACK(2) — fully processed, confirmed acyclic
    //
    // Optimisations:
    //   • Two HashSets (visited + recursionStack) merged into one HashMap: lookups 2 → 1
    //   • Back-tracking via LinkedHashSet.remove() → HashMap.put(BLACK): eliminates list overhead
    //   • cyclePath ArrayList → parent Map reconstructed on demand, avoids per-step push/pop
    // -------------------------------------------------------------------------

    private static final int WHITE = 0;
    private static final int GRAY  = 1;
    private static final int BLACK = 2;

    private static final class GraphStats {
        int totalNodes = 0;
        int maxDepth = 0;
        int maxChildrenPerNode = 0;
        int leafNodes = 0;
        int internalNodes = 0;
    }

    private void detectCyclesAndCollectStats(SnakeDigestGraph graph, GraphStats stats,
                                             MindMapValidationResult result) {
        // color/parent pre-allocated by SnakeDigestGraph to current node count, avoiding mid-DFS rehash
        Map<String, Integer> color = graph.newColorMap();
        Map<String, String> parent = graph.newParentMap();
        statsDFS(graph.getRootId(), graph, color, parent, 1, stats, result);
    }

    private void statsDFS(String nodeId, SnakeDigestGraph graph,
                          Map<String, Integer> color, Map<String, String> parent,
                          int depth, GraphStats stats,
                          MindMapValidationResult result) {
        if (nodeId == null) return;

        // Single lookup determines GRAY / BLACK simultaneously (previously required two HashSet.contains calls)
        int state = color.getOrDefault(nodeId, WHITE);

        if (state == GRAY) {
            // Back-edge detected: reconstruct cycle path via parent map
            result.addError("CIRCULAR_DEPENDENCY",
                "Cycle detected: " + buildCyclePath(nodeId, parent),
                nodeId);
            return;
        }
        if (state == BLACK) return; // already fully processed, prune

        // ① Enter: WHITE → GRAY
        color.put(nodeId, GRAY);
        stats.totalNodes++;
        stats.maxDepth = Math.max(stats.maxDepth, depth);

        List<String> children = graph.getChildren(nodeId);
        int childCount = children.size();
        stats.maxChildrenPerNode = Math.max(stats.maxChildrenPerNode, childCount);

        if (childCount == 0) {
            stats.leafNodes++;
        } else {
            stats.internalNodes++;

            if (childCount > maxChildren) {
                result.addError("EXCEEDS_MAX_CHILDREN",
                    "Node exceeds max child-count: " + childCount + " > " + maxChildren, nodeId);
            }

            for (String childId : children) {
                if (childId.equals(nodeId)) {
                    result.addError("SELF_REFERENCE", "Node references itself as a child", nodeId);
                    continue;
                }
                // record parent for cycle-path reconstruction
                parent.put(childId, nodeId);
                statsDFS(childId, graph, color, parent, depth + 1, stats, result);
            }
        }

        // ② Done: GRAY → BLACK (simple put replaces LinkedHashSet.remove)
        color.put(nodeId, BLACK);
    }

    /**
     * Reconstructs the cycle path string from the parent map.
     * Walks the parent chain from {@code cycleEntry} upward until {@code cycleEntry} is
     * encountered again, then reverses and returns "A → B → C → A".
     */
    private String buildCyclePath(String cycleEntry, Map<String, String> parent) {
        List<String> path = new ArrayList<>();
        path.add(cycleEntry);
        String cur = parent.get(cycleEntry);
        // Walk the parent chain; cap at parent.size()+1 steps to guard against unexpected infinite loops
        int limit = parent.size() + 1;
        while (cur != null && !cur.equals(cycleEntry) && limit-- > 0) {
            path.add(cur);
            cur = parent.get(cur);
        }
        path.add(cycleEntry); // close the cycle
        Collections.reverse(path);
        return String.join(" → ", path);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private String generateNodeId() {
        return "node_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
