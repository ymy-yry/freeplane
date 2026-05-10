package org.freeplane.plugin.ai.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.note.mindmapmode.MNoteController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.content.ListResponse;
import org.freeplane.plugin.ai.tools.content.ListTool;
import org.freeplane.plugin.ai.tools.content.ModifiedNodeSummaryBuilder;
import org.freeplane.plugin.ai.tools.content.NodeContentApplier;
import org.freeplane.plugin.ai.tools.content.NodeContentFactories;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.create.AnchorPlacementCalculator;
import org.freeplane.plugin.ai.tools.create.CreateNodesPreferences;
import org.freeplane.plugin.ai.tools.create.CreateNodesRequest;
import org.freeplane.plugin.ai.tools.create.CreateNodesResponse;
import org.freeplane.plugin.ai.tools.create.CreateNodesTool;
import org.freeplane.plugin.ai.tools.create.NodeCreationHierarchyBuilder;
import org.freeplane.plugin.ai.tools.create.NodeInserter;
import org.freeplane.plugin.ai.tools.create.NodeModelCreator;
import org.freeplane.plugin.ai.tools.delete.DeleteNodesRequest;
import org.freeplane.plugin.ai.tools.delete.DeleteNodesResponse;
import org.freeplane.plugin.ai.tools.delete.DeleteNodesTool;
import org.freeplane.plugin.ai.tools.edit.AttributesContentEditor;
import org.freeplane.plugin.ai.tools.edit.EditRequest;
import org.freeplane.plugin.ai.tools.edit.HyperlinkContentEditor;
import org.freeplane.plugin.ai.tools.edit.IconsContentEditor;
import org.freeplane.plugin.ai.tools.edit.NodeContentEditItem;
import org.freeplane.plugin.ai.tools.edit.NodeContentEditor;
import org.freeplane.plugin.ai.tools.edit.NoteContentWriteController;
import org.freeplane.plugin.ai.tools.edit.NoteContentWriteControllerAdapter;
import org.freeplane.plugin.ai.tools.edit.NodeStyleContentEditor;
import org.freeplane.plugin.ai.tools.edit.TagsContentEditor;
import org.freeplane.plugin.ai.tools.edit.TextContentWriteController;
import org.freeplane.plugin.ai.tools.edit.TextContentWriteControllerAdapter;
import org.freeplane.plugin.ai.tools.edit.TextualContentEditor;
import org.freeplane.plugin.ai.tools.move.CreateSummaryRequest;
import org.freeplane.plugin.ai.tools.move.CreateSummaryResponse;
import org.freeplane.plugin.ai.tools.move.CreateSummaryTool;
import org.freeplane.plugin.ai.tools.move.MoveNodesIntoSummaryRequest;
import org.freeplane.plugin.ai.tools.move.MoveNodesIntoSummaryResponse;
import org.freeplane.plugin.ai.tools.move.MoveNodesIntoSummaryTool;
import org.freeplane.plugin.ai.tools.move.MoveNodesRequest;
import org.freeplane.plugin.ai.tools.move.MoveNodesResponse;
import org.freeplane.plugin.ai.tools.move.MoveNodesTool;
import org.freeplane.plugin.ai.tools.move.SummaryNodeCreator;
import org.freeplane.plugin.ai.tools.read.FetchNodesForEditingRequest;
import org.freeplane.plugin.ai.tools.read.FetchNodesForEditingResponse;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsRequest;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsResponse;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsTool;
import org.freeplane.plugin.ai.tools.search.SearchNodesRequest;
import org.freeplane.plugin.ai.tools.search.SearchNodesResponse;
import org.freeplane.plugin.ai.tools.search.SearchNodesTool;
import org.freeplane.plugin.ai.tools.selection.SelectSingleNodeRequest;
import org.freeplane.plugin.ai.tools.selection.SelectSingleNodeTool;
import org.freeplane.plugin.ai.tools.selection.SelectedMapAndNodeIdentifiersTool;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersRequest;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryFormatter;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;
import org.freeplane.plugin.ai.tools.connectors.ConnectorEditRequest;
import org.freeplane.plugin.ai.tools.connectors.ConnectorEditResponse;
import org.freeplane.plugin.ai.tools.connectors.ConnectorEditTool;

import dev.langchain4j.agent.tool.Tool;

public class AIToolSet {
    private final MessageBuilder messageBuilder;
    private final ReadNodesWithDescendantsTool readNodesWithDescendantsTool;
    private final SelectedMapAndNodeIdentifiersTool selectedMapAndNodeIdentifiersTool;
    private final SelectSingleNodeTool selectSingleNodeTool;
    private final SearchNodesTool searchNodesTool;
    private final CreateNodesTool createNodesTool;
    private final MoveNodesTool moveNodesTool;
    private final DeleteNodesTool deleteNodesTool;
    private final CreateSummaryTool createSummaryTool;
    private final MoveNodesIntoSummaryTool moveNodesIntoSummaryTool;
    private final ListTool listTool;
    private final ConnectorEditTool connectorEditTool;
    private final NodeContentEditor nodeContentEditor;
    private final AvailableMaps availableMaps;
    private final AvailableMaps.MapAccessListener mapAccessListener;
    private final ToolCallSummaryHandler toolCallSummaryHandler;
    private final ToolCaller toolCaller;

    AIToolSet(ToolCallSummaryHandler toolCallSummaryHandler, AvailableMaps availableMaps,
              AvailableMaps.MapAccessListener mapAccessListener, TextController textController,
              NodeContentFactories nodeContentFactories, MMapController mapController,
              ToolCaller toolCaller) {
        Objects.requireNonNull(mapController, "mapController");
        this.availableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        this.mapAccessListener = mapAccessListener;
        NodeModelCreator nodeModelCreator = new NodeModelCreator();
        AnchorPlacementCalculator anchorPlacementCalculator = new AnchorPlacementCalculator();
        NodeInserter nodeInserter = new NodeInserter(mapController, anchorPlacementCalculator);
        SummaryNodeCreator summaryNodeCreator = new SummaryNodeCreator(mapController);
        ModifiedNodeSummaryBuilder modifiedNodeSummaryBuilder = new ModifiedNodeSummaryBuilder(textController);
        TextContentWriteController textContentWriteController = new TextContentWriteControllerAdapter(
            MTextController.getController());
        NoteContentWriteController noteContentWriteController = new NoteContentWriteControllerAdapter(
            MNoteController.getController());
        MAttributeController attributeController = MAttributeController.getController();
        MIconController iconController = (MIconController) IconController.getController();
        MLinkController linkController = requireLinkController();
        TextualContentEditor textualContentEditor = new TextualContentEditor(
            textContentWriteController, noteContentWriteController);
        AttributesContentEditor attributesContentEditor = new AttributesContentEditor(attributeController);
        TagsContentEditor tagsContentEditor = new TagsContentEditor(iconController);
        List<NamedIcon> iconCandidates = new ArrayList<>(IconStoreFactory.ICON_STORE.getMindIcons());
        iconCandidates.addAll(IconStoreFactory.ICON_STORE.getUserIcons());
        IconsContentEditor iconsContentEditor = new IconsContentEditor(
            nodeContentFactories.iconDescriptionResolver, iconCandidates, iconController);
        NodeStyleContentEditor nodeStyleContentEditor = new NodeStyleContentEditor();
        HyperlinkContentEditor hyperlinkContentEditor = new HyperlinkContentEditor(linkController);
        NodeContentApplier nodeContentApplier = new NodeContentApplier(textualContentEditor, attributesContentEditor,
            tagsContentEditor, iconsContentEditor, hyperlinkContentEditor);
        NodeCreationHierarchyBuilder nodeCreationHierarchyBuilder = new NodeCreationHierarchyBuilder(
            nodeModelCreator, nodeContentApplier, nodeStyleContentEditor);
        NodeContentEditor nodeContentEditor = new NodeContentEditor(textController, nodeContentFactories.nodeContentItemReader,
            textualContentEditor, attributesContentEditor, tagsContentEditor, iconsContentEditor,
            nodeStyleContentEditor, hyperlinkContentEditor);
        MessageBuilder messageBuilder = new MessageBuilder();
        ReadNodesWithDescendantsTool readNodesWithDescendantsTool = new ReadNodesWithDescendantsTool(
            availableMaps, mapAccessListener, nodeContentFactories.nodeContentItemReader, textController);
        SelectedMapAndNodeIdentifiersTool selectedMapAndNodeIdentifiersTool = new SelectedMapAndNodeIdentifiersTool(
            availableMaps, mapAccessListener, textController);
        SelectSingleNodeTool selectSingleNodeTool = new SelectSingleNodeTool(
            availableMaps, mapAccessListener, mapController, selectedMapAndNodeIdentifiersTool);
        SearchNodesTool searchNodesTool = new SearchNodesTool(availableMaps, mapAccessListener,
            nodeContentFactories.nodeContentItemReader, textController);
        CreateNodesPreferences createNodesPreferences = new CreateNodesPreferences();
        CreateNodesTool createNodesTool = new CreateNodesTool(availableMaps, mapAccessListener,
            nodeCreationHierarchyBuilder, nodeInserter, modifiedNodeSummaryBuilder, mapController,
            createNodesPreferences);
        MoveNodesTool moveNodesTool = new MoveNodesTool(availableMaps, mapAccessListener, mapController,
            anchorPlacementCalculator);
        DeleteNodesTool deleteNodesTool = new DeleteNodesTool(availableMaps, mapAccessListener, mapController,
            modifiedNodeSummaryBuilder);
        CreateSummaryTool createSummaryTool = new CreateSummaryTool(availableMaps, mapAccessListener,
            nodeCreationHierarchyBuilder, nodeInserter, summaryNodeCreator, modifiedNodeSummaryBuilder);
        MoveNodesIntoSummaryTool moveNodesIntoSummaryTool = new MoveNodesIntoSummaryTool(availableMaps,
            mapAccessListener, mapController, summaryNodeCreator);
        ListTool listTool = new ListTool(
            nodeContentFactories.iconDescriptionResolver, availableMaps, mapAccessListener);
        ConnectorEditTool connectorEditTool = new ConnectorEditTool(availableMaps, mapAccessListener, linkController);
        this.messageBuilder = Objects.requireNonNull(messageBuilder, "messageBuilder");
        this.readNodesWithDescendantsTool = Objects.requireNonNull(readNodesWithDescendantsTool,
            "readNodesWithDescendantsTool");
        this.selectedMapAndNodeIdentifiersTool = Objects.requireNonNull(
            selectedMapAndNodeIdentifiersTool, "selectedMapAndNodeIdentifiersTool");
        this.selectSingleNodeTool = Objects.requireNonNull(selectSingleNodeTool, "selectSingleNodeTool");
        this.searchNodesTool = Objects.requireNonNull(searchNodesTool, "searchNodesTool");
        this.createNodesTool = Objects.requireNonNull(createNodesTool, "createNodesTool");
        this.moveNodesTool = Objects.requireNonNull(moveNodesTool, "moveNodesTool");
        this.deleteNodesTool = Objects.requireNonNull(deleteNodesTool, "deleteNodesTool");
        this.createSummaryTool = Objects.requireNonNull(createSummaryTool, "createSummaryTool");
        this.moveNodesIntoSummaryTool = Objects.requireNonNull(moveNodesIntoSummaryTool, "moveNodesIntoSummaryTool");
        this.listTool = Objects.requireNonNull(listTool, "listTool");
        this.connectorEditTool = Objects.requireNonNull(connectorEditTool, "connectorEditTool");
        this.nodeContentEditor = Objects.requireNonNull(nodeContentEditor, "nodeContentEditor");
        this.toolCallSummaryHandler = toolCallSummaryHandler;
        this.toolCaller = toolCaller == null
            ? ToolCaller.CHAT
            : toolCaller;
    }

    public String systemMessageForChat(@SuppressWarnings("unused") Object input) {
        return messageBuilder.buildForChat();
    }

    @Tool("Read nodes with descendants.")
    public ReadNodesWithDescendantsResponse readNodesWithDescendants(ReadNodesWithDescendantsRequest request) {
        try {
            ReadNodesWithDescendantsResponse response = readNodesWithDescendantsTool.readNodesWithDescendants(request);
            publishToolCallSummary(readNodesWithDescendantsTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(readNodesWithDescendantsTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Fetch nodes for editing. editableContentFields (required): TEXT, DETAILS, NOTE, ATTRIBUTES, TAGS, ICONS. "
        + "Returns editable text/details/note/attributes/tags/icons; text/details/note include contentType for "
        + "edit.originalContentType. Style is in content.mainStyle. Read hyperlinks via readNodesWithDescendants "
        + "with ContextSection.HYPERLINK.")
    public FetchNodesForEditingResponse fetchNodesForEditing(FetchNodesForEditingRequest request) {
        try {
            FetchNodesForEditingResponse response = readNodesWithDescendantsTool.fetchNodesForEditing(request);
            publishToolCallSummary(readNodesWithDescendantsTool.buildFetchToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(readNodesWithDescendantsTool.buildFetchToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Get identifiers for the currently selected map and node, with configurable selection collection mode.")
    public SelectionIdentifiersResponse getSelectedMapAndNodeIdentifiers(SelectionIdentifiersRequest request) {
        try {
            SelectionIdentifiersResponse response = selectedMapAndNodeIdentifiersTool.getSelectedMapAndNodeIdentifiers(request);
            publishToolCallSummary(selectedMapAndNodeIdentifiersTool.buildToolCallSummary(response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(selectedMapAndNodeIdentifiersTool.buildToolCallErrorSummary(error));
            throw error;
        }
    }

    @Tool("Select a single node by identifier and make it visible in the current view. This updates the node shown "
        + "to the user in the UI and should be used only for user communication; unexpected use can disrupt user "
        + "workflow and reduce AI acceptance.")
    public SelectionIdentifiersResponse selectSingleNode(SelectSingleNodeRequest request) {
        try {
            SelectionIdentifiersResponse response = selectSingleNodeTool.selectSingleNode(request);
            publishToolCallSummary(selectSingleNodeTool.buildToolCallSummary(response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(selectSingleNodeTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Search nodes by content.")
    public SearchNodesResponse searchNodes(SearchNodesRequest request) {
        try {
            SearchNodesResponse response = searchNodesTool.searchNodes(request);
            publishToolCallSummary(searchNodesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(searchNodesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Delete nodes by identifier.")
    public DeleteNodesResponse deleteNodes(DeleteNodesRequest request) {
        try {
            DeleteNodesResponse response = deleteNodesTool.deleteNodes(request);
            publishToolCallSummary(deleteNodesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(deleteNodesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Lists application-wide icons (not map-specific). Emoji icons are referenced by the emoji character itself "
        + "and are not listed here.")
    public ListResponse listAvailableIcons() {
        try {
            ListResponse response = listTool.listAvailableIcons();
            publishToolCallSummary(listTool.buildToolCallSummary("listAvailableIcons", response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(listTool.buildToolCallErrorSummary("listAvailableIcons", error));
            throw error;
        }
    }

    @Tool("Lists styles defined in the target map.")
    public ListResponse listMapStyles(String mapIdentifier) {
        try {
            ListResponse response = listTool.listMapStyles(mapIdentifier);
            publishToolCallSummary(listTool.buildToolCallSummary("listMapStyles", response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(listTool.buildToolCallErrorSummary("listMapStyles", error));
            throw error;
        }
    }

    @Tool("Edit connectors by source and target node identifier.\n"
        + "For REPLACE/DELETE, matchSourceLabel/matchMiddleLabel/matchTargetLabel select the connector; "
        + "sourceLabel/middleLabel/targetLabel are replacement values.\n"
        + "For ADD, match* fields are ignored.\n"
        + "In match* fields, null is wildcard and empty string matches an empty label.")
    public ConnectorEditResponse editConnectors(ConnectorEditRequest request) {
        try {
            ConnectorEditResponse response = connectorEditTool.editConnectors(request);
            publishToolCallSummary(connectorEditTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(connectorEditTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Edit node content through undo-aware controllers.\n"
        + "Before TEXT/DETAILS/NOTE edits, call fetchNodesForEditing and pass originalContentType from that response.\n"
        + "For TEXT/DETAILS/NOTE, values starting with <html> are HTML; all others are plain text.\n"
        + "TEXT supports REPLACE only; to clear TEXT, use REPLACE with an empty value.\n"
        + "STYLE: REPLACE with a style from listMapStyles (same map), or DELETE.\n"
        + "HYPERLINK: REPLACE uses value as URL; DELETE clears it.\n"
        + "ATTRIBUTES/TAGS/ICONS REPLACE/DELETE: use index first, then targetKey.\n"
        + "ATTRIBUTES ADD: targetKey is attribute name; value is attribute value.\n"
        + "TAGS ADD: value is tag text; optional index inserts at position.\n"
        + "ICONS ADD: value is icon description from listAvailableIcons (or emoji); icons append, so index/targetKey ignored.\n"
        + "originalContentType is required for TEXT/DETAILS/NOTE and ignored otherwise.\n"
        + "For TEXT/DETAILS/NOTE, Markdown/LaTeX formatting applies only when originalContentType is MARKDOWN/LATEX; "
        + "otherwise it is literal text.")
    public List<NodeContentItem> edit(EditRequest request) {
        try {
            List<NodeContentItem> response = editNodes(request);
            publishToolCallSummary(buildEditToolSummary(request, response, false, null));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(buildEditToolSummary(request, null, true, error.getMessage()));
            throw error;
        }
    }

    private void publishToolCallSummary(ToolCallSummary summary) {
        if (summary == null) {
            return;
        }
        LogUtils.info(summary.getSummaryText());
        if (toolCallSummaryHandler != null) {
            toolCallSummaryHandler.handleToolCallSummary(
                summary.withToolCaller(toolCaller));
        }
    }

    private List<NodeContentItem> editNodes(EditRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request");
        }
        String mapIdentifierValue = requireValue(request.getMapIdentifier(), "mapIdentifier");
        UUID mapIdentifier = parseMapIdentifier(mapIdentifierValue);
        MapModel mapModel = availableMaps.findMapModel(mapIdentifier, mapAccessListener);
        if (mapModel == null) {
            throw new IllegalArgumentException("Unknown map identifier: " + mapIdentifierValue);
        }
        List<NodeContentEditItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Missing edit items");
        }
        Map<String, List<NodeContentEditItem>> itemsByNode = new LinkedHashMap<>();
        for (NodeContentEditItem item : items) {
            if (item == null) {
                continue;
            }
            String nodeIdentifier = requireValue(item.getNodeIdentifier(), "nodeIdentifier");
            itemsByNode.computeIfAbsent(nodeIdentifier, key -> new ArrayList<>()).add(item);
        }
        List<NodeContentItem> results = new ArrayList<>(itemsByNode.size());
        List<String> unknownNodeIdentifiers = new ArrayList<>();
        for (Map.Entry<String, List<NodeContentEditItem>> entry : itemsByNode.entrySet()) {
            String nodeIdentifier = entry.getKey();
            NodeModel nodeModel = mapModel.getNodeForID(nodeIdentifier);
            if (nodeModel == null) {
                unknownNodeIdentifiers.add(nodeIdentifier);
                continue;
            }
            results.add(nodeContentEditor.edit(nodeModel, entry.getValue()));
        }
        if (!unknownNodeIdentifiers.isEmpty()) {
            throw new IllegalArgumentException("Invalid node identifiers: " + String.join(", ", unknownNodeIdentifiers));
        }
        return results;
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName + ".");
        }
        return value;
    }

    private UUID parseMapIdentifier(String mapIdentifier) {
        try {
            return UUID.fromString(mapIdentifier);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid map identifier: " + mapIdentifier);
        }
    }

    private ToolCallSummary buildEditToolSummary(EditRequest request, List<NodeContentItem> response,
                                                 boolean hasError, String errorMessage) {
        if (request == null) {
            return null;
        }
        int itemCount = request.getItems() == null ? 0 : request.getItems().size();
        int nodeCount = response == null ? 0 : response.size();
        String summaryText = "edit: nodes=" + nodeCount + ", items=" + itemCount;
        if (request.getUserSummary() != null && !request.getUserSummary().isEmpty()) {
            summaryText = summaryText + ", userSummary=\"" + request.getUserSummary() + "\"";
        }
        if (hasError) {
            String safeMessage = ToolCallSummaryFormatter.sanitizeValue(errorMessage);
            if (!safeMessage.isEmpty()) {
                summaryText = summaryText + ", error=\"" + safeMessage + "\"";
            } else {
                summaryText = summaryText + ", error=true";
            }
        }
        return new ToolCallSummary("edit", summaryText, hasError);
    }

    private MLinkController requireLinkController() {
        ModeController modeController = Controller.getCurrentModeController();
        if (modeController == null) {
            throw new IllegalStateException("Current mode controller is not available.");
        }
        LinkController linkController = LinkController.getController(modeController);
        if (!(linkController instanceof MLinkController)) {
            throw new IllegalStateException("Link controller is not available.");
        }
        return (MLinkController) linkController;
    }

    @Tool("Create nodes and subtrees relative to an anchor node.\n"
        + "Optional fields override defaults. Omit them to keep defaults.\n"
        + "Each optional field is an intentional override. Include it only when the specific value is justified; "
        + "otherwise omit it. Never send empty strings, empty arrays, or null.\n"
        + "For content.text/content.details/content.note, only values starting with <html> are treated as HTML; all "
        + "other values are treated as plain text.\n"
        + "textContentType/detailsContentType/noteContentType control conversion/validation only; HTML input still "
        + "requires the <html> prefix.\n"
        + "Omit optional textual fields such as details and note when they are empty instead of sending empty strings "
        + "so the tool leaves those values untouched.")
    public CreateNodesResponse createNodes(CreateNodesRequest request) {
        try {
            CreateNodesResponse response = createNodesTool.createNodes(request);
            publishToolCallSummary(createNodesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(createNodesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Move nodes relative to an anchor node.")
    public MoveNodesResponse moveNodes(MoveNodesRequest request) {
        try {
            MoveNodesResponse response = moveNodesTool.moveNodes(request);
            publishToolCallSummary(moveNodesTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(moveNodesTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Create summary content and a summary bracket for a summarized range. "
        + "Optional fields override defaults. Omit them to keep defaults. "
        + "Summary anchor nodes must share the same parent node. Textual field rules are the same as createNodes. "
        + "Omit optional textual fields such as details and note when they are empty instead of sending empty strings "
        + "so the tool leaves those values untouched. Tip: to create a summary of summaries, choose summary anchor "
        + "nodes that already exist at the same summary level under the same parent node, regardless of how those "
        + "summary nodes were created.")
    public CreateSummaryResponse createSummary(CreateSummaryRequest request) {
        try {
            CreateSummaryResponse response = createSummaryTool.createSummary(request);
            publishToolCallSummary(createSummaryTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(createSummaryTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }

    @Tool("Move existing nodes to become summary content for a summarized range.")
    public MoveNodesIntoSummaryResponse moveNodesIntoSummary(MoveNodesIntoSummaryRequest request) {
        try {
            MoveNodesIntoSummaryResponse response = moveNodesIntoSummaryTool.moveNodesIntoSummary(request);
            publishToolCallSummary(moveNodesIntoSummaryTool.buildToolCallSummary(request, response));
            return response;
        } catch (RuntimeException error) {
            publishToolCallSummary(moveNodesIntoSummaryTool.buildToolCallErrorSummary(request, error));
            throw error;
        }
    }
}
