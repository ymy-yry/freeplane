package org.freeplane.plugin.ai.validation;

import java.util.*;

/**
 * SnakeDigestGraph — a directed graph container dedicated to cycle detection.
 *
 * <h3>Naming rationale</h3>
 * <ul>
 *   <li><b>Snake</b>: the "snake" in the snake-digest metaphor, driving the entire parsing flow</li>
 *   <li><b>Digest</b>: digestion behaviour — only the three essential "nutrients" (id/text/edges) are
 *       retained; JsonNode objects (the "shell") can be GC’d immediately after parsing</li>
 *   <li><b>Graph</b>: the underlying data structure is a directed graph (adjacency list)</li>
 * </ul>
 *
 * <h3>Growth strategy (lazy-mouth expansion)</h3>
 * <ul>
 *   <li>The outer adjacency-list Map starts with {@value #INITIAL_CAPACITY} buckets;
 *       once the threshold is exceeded LinkedHashMap doubles internally.</li>
 *   <li>Each node’s child list starts with capacity <b>0</b> so leaf nodes allocate no array;
 *       the first {@code addEdge} call grows it to 1, then ArrayList grows at 1.5×
 *       (1 → 2 → 3 → 4 → 6 → 9 …)</li>
 *   <li>DFS auxiliary maps created by {@link #newColorMap()} / {@link #newParentMap()} are
 *       pre-sized to the current node count to avoid rehashing during DFS.</li>
 * </ul>
 *
 * <h3>Responsibility boundary</h3>
 * <p>This class only holds the graph structure and provides read/write operations.
 * It contains no validation logic, no DFS algorithm, and no JSON parsing — all three
 * are handled by {@link MindMapGenerationValidator}.
 */
public final class SnakeDigestGraph {

    /** Initial bucket count: designed for small mind-maps (4–8 nodes); auto-doubles after 6 nodes. */
    static final int   INITIAL_CAPACITY = 8;
    static final float LOAD_FACTOR      = 0.75f;

    private String rootId;

    /**
     * Adjacency list: nodeId → list of child node IDs (insertion-ordered).
     * Starts with 8 buckets and doubles on overflow; each child list starts with capacity 0
     * (leaf nodes allocate no array).
     */
    private final Map<String, List<String>> adjacency =
            new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR);

    /**
     * Label map: nodeId → text (used only for validation reports and root-node validity checks).
     * Grows in sync with the adjacency list.
     */
    private final Map<String, String> labels =
            new HashMap<>(INITIAL_CAPACITY, LOAD_FACTOR);

    // -------------------------------------------------------------------------
    // Write interface (called incrementally during JSON parsing / DFS traversal)
    // -------------------------------------------------------------------------

    /**
     * Registers a node in the adjacency list.
     * The child list starts with capacity 0 so leaf nodes allocate no Object[] array.
     *
     * @param id   the node ID
     * @param text the node text (used for validation reports)
     */
    public void registerNode(String id, String text) {
        adjacency.putIfAbsent(id, new ArrayList<>(0));
        labels.put(id, text);
    }

    /**
     * Adds a directed edge parentId → childId.
     * Triggers lazy ArrayList growth: first add grows from 0 to 1, then 1.5× thereafter.
     *
     * @param parentId the parent node ID
     * @param childId  the child node ID
     */
    public void addEdge(String parentId, String childId) {
        adjacency.computeIfAbsent(parentId, k -> new ArrayList<>(0)).add(childId);
    }

    /**
     * Sets the root node ID. Idempotent: only takes effect on the first call (when rootId is null)
     * to prevent accidental overwriting during DFS recursion.
     */
    public void setRootId(String id) {
        if (this.rootId == null) {
            this.rootId = id;
        }
    }

    // -------------------------------------------------------------------------
    // Read interface (called during the validation phase)
    // -------------------------------------------------------------------------

    /** Returns the root node ID, or {@code null} if not yet set. */
    public String getRootId() {
        return rootId;
    }

    /** Returns the total number of currently registered nodes. */
    public int nodeCount() {
        return adjacency.size();
    }

    /**
     * Returns an unmodifiable view of the specified node’s child list (guards against external mutation).
     * Returns an empty list when the node does not exist or is a leaf.
     */
    public List<String> getChildren(String nodeId) {
        List<String> children = adjacency.get(nodeId);
        return children == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(children);
    }

    /**
     * Returns the node’s text label, or an empty string if the node does not exist.
     */
    public String getLabel(String nodeId) {
        return labels.getOrDefault(nodeId, "");
    }

    /**
     * Returns an unmodifiable view of all adjacency-list entries (for ID-uniqueness checks and traversal).
     */
    public Set<Map.Entry<String, List<String>>> adjacencyEntries() {
        return Collections.unmodifiableSet(adjacency.entrySet());
    }

    // -------------------------------------------------------------------------
    // DFS auxiliary factory methods (pre-sized to current node count to avoid mid-DFS rehash)
    // -------------------------------------------------------------------------

    /**
     * Creates a DFS three-colour map (WHITE / GRAY / BLACK).
     * Capacity = max(INITIAL_CAPACITY, nodeCount × 2), keeping load factor below 0.5
     * so no rehash occurs during a full DFS traversal.
     */
    public Map<String, Integer> newColorMap() {
        return new HashMap<>(Math.max(INITIAL_CAPACITY, adjacency.size() * 2));
    }

    /**
     * Creates a DFS parent-tracking map (used to reconstruct the full cycle path when one is found).
     * Uses the same capacity strategy as {@link #newColorMap()}.
     */
    public Map<String, String> newParentMap() {
        return new HashMap<>(Math.max(INITIAL_CAPACITY, adjacency.size() * 2));
    }
}
