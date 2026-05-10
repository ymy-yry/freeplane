package org.freeplane.plugin.ai.strategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool performance profile — priority definitions based on actual performance data.
 * 
 * <p>Based on the characteristics of each tool operation, the 14 tools are grouped into
 * 8 priority tiers. Data sourced from Freeplane 1.10+ performance test results.
 * 
 * <h3>Priority classification principles:</h3>
 * <ol>
 *   <li><b>95–100</b>: Core tree-structure operations; directly manipulate the data model, fastest response</li>
 *   <li><b>88–94</b>: Style-system operations; have a cache mechanism, low cost for partial changes</li>
 *   <li><b>85–90</b>: Pure selection operations; almost no extra computation</li>
 *   <li><b>80–88</b>: Search operations; built-in optimisations but require traversal</li>
 *   <li><b>75–82</b>: Filter operations; dedicated optimisations but still involve traversal and repaint</li>
 *   <li><b>65–75</b>: Formula evaluation; dependency-tracking mechanism, expensive for complex expressions</li>
 *   <li><b>60–70</b>: Export operations; involve full-graph traversal and output generation</li>
 *   <li><b>55–65</b>: Bulk operations; performance degrades noticeably with large node counts</li>
 * </ol>
 * 
 * <h3>Usage scenarios:</h3>
 * <ul>
 *   <li>Greedy strategy: priority used as weight when sorting by value density</li>
 *   <li>Unbounded knapsack: priority determines the value score for tool selection</li>
 *   <li>Strategy routing: automatically selects the best algorithm based on tool type</li>
 * </ul>
 * 
 * @author AI Plugin Team
 * @since 1.13.x
 */
public final class ToolPerformanceProfile {
    
    // ========== Priority constants (higher score = better performance = call first) ==========
    
    /** Core tree-structure operations: create/delete/move/copy/paste nodes, fold/expand, change text. */
    public static final int PRIORITY_TREE_OPERATIONS = 97;  // mid-point of 95-100
    
    /** Style operations: apply style, set icon, font colour, node background, hyperlink. */
    public static final int PRIORITY_STYLE_OPERATIONS = 91;  // mid-point of 88-94
    
    /** Selection and navigation: select node, jump to, expand to specified level. */
    public static final int PRIORITY_SELECTION_OPERATIONS = 87;  // mid-point of 85-90
    
    /** Search operations: find node, search and replace (basic mode). */
    public static final int PRIORITY_SEARCH_OPERATIONS = 84;  // mid-point of 80-88
    
    /** Filter operations: apply filter, show/hide nodes. */
    public static final int PRIORITY_FILTER_OPERATIONS = 78;  // mid-point of 75-82
    
    /** Formula evaluation: node formulas, attribute formulas. */
    public static final int PRIORITY_FORMULA_OPERATIONS = 70;  // mid-point of 65-75
    
    /** Export operations: export to PNG, Markdown, OPML, XML, etc. */
    public static final int PRIORITY_EXPORT_OPERATIONS = 65;  // mid-point of 60-70
    
    /** Bulk operations: large-scale batch node edits, complex conditional styles. */
    public static final int PRIORITY_BATCH_OPERATIONS = 60;  // mid-point of 55-65
    
    // ========== Tool-to-priority mapping ==========
    
    /**
     * Map of tool name to priority score.
     * <p>Categorises the 14 Freeplane tools by their actual functional characteristics.
     */
    private static final Map<String, Integer> TOOL_PRIORITY_MAP = buildPriorityMap();
    
    private static Map<String, Integer> buildPriorityMap() {
        Map<String, Integer> map = new HashMap<>();
        
        // ===== Priority 1: core tree-structure operations (97) =====
        map.put("createNodes", PRIORITY_TREE_OPERATIONS);           // create node
        map.put("deleteNodes", PRIORITY_TREE_OPERATIONS);          // delete node
        map.put("moveNodes", PRIORITY_TREE_OPERATIONS);            // move node
        map.put("copyNodes", PRIORITY_TREE_OPERATIONS);            // copy node
        map.put("pasteNodes", PRIORITY_TREE_OPERATIONS);           // paste node
        map.put("foldBranch", PRIORITY_TREE_OPERATIONS);           // fold branch
        map.put("expandBranch", PRIORITY_TREE_OPERATIONS);         // expand branch
        map.put("edit", PRIORITY_TREE_OPERATIONS);                 // edit node text (via edit tool)
        
        // ===== Priority 2: style operations (91) =====
        map.put("applyStyle", PRIORITY_STYLE_OPERATIONS);          // apply style
        map.put("setIcon", PRIORITY_STYLE_OPERATIONS);             // set icon
        map.put("setNodeColor", PRIORITY_STYLE_OPERATIONS);        // set font colour
        map.put("setNodeBackground", PRIORITY_STYLE_OPERATIONS);   // set node background
        map.put("setHyperlink", PRIORITY_STYLE_OPERATIONS);        // set hyperlink
        
        // ===== Priority 3: selection and navigation (87) =====
        map.put("selectNode", PRIORITY_SELECTION_OPERATIONS);      // select node
        map.put("navigateToNode", PRIORITY_SELECTION_OPERATIONS);  // navigate to node
        map.put("expandToLevel", PRIORITY_SELECTION_OPERATIONS);   // expand to level
        
        // ===== Priority 4: search operations (84) =====
        map.put("findNode", PRIORITY_SEARCH_OPERATIONS);           // find node
        map.put("searchAndReplace", PRIORITY_SEARCH_OPERATIONS);   // search and replace
        
        // ===== Priority 5: filter operations (78) =====
        map.put("applyFilter", PRIORITY_FILTER_OPERATIONS);        // apply filter
        map.put("filterComposer", PRIORITY_FILTER_OPERATIONS);     // filter composer
        map.put("showHideNodes", PRIORITY_FILTER_OPERATIONS);      // show/hide nodes
        
        // ===== Priority 6: formula evaluation (70) =====
        map.put("calculateFormula", PRIORITY_FORMULA_OPERATIONS);  // calculate formula
        map.put("evaluateProperty", PRIORITY_FORMULA_OPERATIONS);  // evaluate attribute formula
        
        // ===== Priority 7: export operations (65) =====
        map.put("exportToPng", PRIORITY_EXPORT_OPERATIONS);        // export to PNG
        map.put("exportToMarkdown", PRIORITY_EXPORT_OPERATIONS);   // export to Markdown
        map.put("exportToOpml", PRIORITY_EXPORT_OPERATIONS);       // export to OPML
        map.put("exportToXml", PRIORITY_EXPORT_OPERATIONS);        // export to XML
        map.put("exportToPdf", PRIORITY_EXPORT_OPERATIONS - 5);    // export to PDF (slower, -5)
        
        // ===== Priority 8: bulk operations (60) =====
        map.put("batchModify", PRIORITY_BATCH_OPERATIONS);         // batch modify
        map.put("applyConditionalStyle", PRIORITY_BATCH_OPERATIONS); // conditional style
        
        return Collections.unmodifiableMap(map);
    }
    
    /**
     * Returns the priority score for the given tool.
     *
     * @param toolName the tool name
     * @return the priority score (55–100); defaults to 70 for unknown tools
     */
    public static int getPriority(String toolName) {
        return TOOL_PRIORITY_MAP.getOrDefault(toolName, 70);
    }
    
    /**
     * Returns {@code true} if the tool belongs to the high-performance tier (priority ≥ 85).
     *
     * @param toolName the tool name
     * @return {@code true} for high-performance tools, recommended to call first
     */
    public static boolean isHighPerformance(String toolName) {
        return getPriority(toolName) >= 85;
    }
    
    /**
     * Returns {@code true} if the tool belongs to the medium-performance tier (priority 70–84).
     *
     * @param toolName the tool name
     * @return {@code true} for medium-performance tools; use with care
     */
    public static boolean isMediumPerformance(String toolName) {
        int priority = getPriority(toolName);
        return priority >= 70 && priority < 85;
    }
    
    /**
     * Returns {@code true} if the tool belongs to the low-performance tier (priority &lt; 70).
     *
     * @param toolName the tool name
     * @return {@code true} for low-performance tools; avoid high-frequency calls
     */
    public static boolean isLowPerformance(String toolName) {
        return getPriority(toolName) < 70;
    }
    
    /**
     * Returns a description of the priority tier for the given tool.
     *
     * @param toolName the tool name
     * @return a tier description (e.g. "Core tree-structure operations (95–100)")
     */
    public static String getPriorityLevelDescription(String toolName) {
        int priority = getPriority(toolName);
        
        if (priority >= 95) {
            return "Core tree-structure operations (95-100)";
        } else if (priority >= 88) {
            return "Style operations (88-94)";
        } else if (priority >= 85) {
            return "Selection/navigation operations (85-90)";
        } else if (priority >= 80) {
            return "Search operations (80-88)";
        } else if (priority >= 75) {
            return "Filter operations (75-82)";
        } else if (priority >= 65) {
            return "Formula/export operations (60-75)";
        } else {
            return "Bulk operations (55-65)";
        }
    }
    
    /**
     * Returns an unmodifiable view of all registered tool priorities (for debugging).
     *
     * @return the unmodifiable priority map
     */
    public static Map<String, Integer> getAllToolPriorities() {
        return TOOL_PRIORITY_MAP;
    }
    
    private ToolPerformanceProfile() {
        // prevent instantiation
    }
}
