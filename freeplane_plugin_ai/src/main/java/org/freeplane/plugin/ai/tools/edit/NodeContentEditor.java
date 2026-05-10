package org.freeplane.plugin.ai.tools.edit;

import java.util.List;

import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.tools.content.NodeContentItem;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentPreset;

public class NodeContentEditor {
    private final TextController textController;
    private final NodeContentItemReader nodeContentItemReader;
    private final TextualContentEditor textualContentEditor;
    private final AttributesContentEditor attributesContentEditor;
    private final TagsContentEditor tagsContentEditor;
    private final IconsContentEditor iconsContentEditor;
    private final NodeStyleContentEditor nodeStyleContentEditor;
    private final HyperlinkContentEditor hyperlinkContentEditor;
    private final AiEditsMarker aiEditsMarker;

    public NodeContentEditor(TextController textController, NodeContentItemReader nodeContentItemReader,
                             TextualContentEditor textualContentEditor,
                             AttributesContentEditor attributesContentEditor,
                             TagsContentEditor tagsContentEditor,
                             IconsContentEditor iconsContentEditor,
                             NodeStyleContentEditor nodeStyleContentEditor,
                             HyperlinkContentEditor hyperlinkContentEditor) {
        this.textController = textController;
        this.nodeContentItemReader = nodeContentItemReader;
        this.textualContentEditor = textualContentEditor;
        this.attributesContentEditor = attributesContentEditor;
        this.tagsContentEditor = tagsContentEditor;
        this.iconsContentEditor = iconsContentEditor;
        this.nodeStyleContentEditor = nodeStyleContentEditor;
        this.hyperlinkContentEditor = hyperlinkContentEditor;
        this.aiEditsMarker = new AiEditsMarker();
    }

    public NodeContentItem edit(NodeModel nodeModel, List<NodeContentEditItem> items) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        if (items == null || items.isEmpty()) {
            return nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL);
        }
        for (NodeContentEditItem edit : items) {
            applyEdit(nodeModel, edit);
        }
        aiEditsMarker.addAiEditsMarkerWithUndo(nodeModel);
        return nodeContentItemReader.readNodeContentItem(nodeModel, NodeContentPreset.FULL, true, true, true);
    }

    private void applyEdit(NodeModel nodeModel, NodeContentEditItem edit) {
        if (edit == null) {
            return;
        }
        EditedElement editedElement = edit.getEditedElement();
        if (editedElement == null) {
            throw new IllegalArgumentException("Missing edited element.");
        }
        switch (editedElement) {
            case TEXT:
                applyTextualContent(nodeModel, edit);
                break;
            case DETAILS:
                applyTextualContent(nodeModel, edit);
                break;
            case NOTE:
                applyTextualContent(nodeModel, edit);
                break;
            case ATTRIBUTES:
                applyAttributes(nodeModel, edit);
                break;
            case TAGS:
                applyTags(nodeModel, edit);
                break;
            case ICONS:
                applyIcons(nodeModel, edit);
                break;
            case STYLE:
                applyStyle(nodeModel, edit);
                break;
            case HYPERLINK:
                applyHyperlink(nodeModel, edit);
                break;
            default:
                throw new IllegalArgumentException("Unknown edited element: " + editedElement);
        }
    }

    private void applyTextualContent(NodeModel nodeModel, NodeContentEditItem edit) {
        if (edit.getOriginalContentType() == null) {
            throw new IllegalArgumentException("Missing originalContentType for textual content edits.");
        }
        String value = resolveTextualValue(edit);
        textualContentEditor.editExistingTextualContent(
            nodeModel,
            edit.getEditedElement(),
            edit.getOriginalContentType(),
            value,
            textController);
    }

    private void applyAttributes(NodeModel nodeModel, NodeContentEditItem edit) {
        attributesContentEditor.editExistingAttributesContent(
            nodeModel,
            edit.getOperation(),
            edit.getTargetKey(),
            edit.getIndex(),
            edit.getValue());
    }

    private void applyTags(NodeModel nodeModel, NodeContentEditItem edit) {
        tagsContentEditor.editExistingTagsContent(
            nodeModel,
            edit.getOperation(),
            edit.getTargetKey(),
            edit.getIndex(),
            edit.getValue());
    }

    private void applyIcons(NodeModel nodeModel, NodeContentEditItem edit) {
        iconsContentEditor.editExistingIconsContent(
            nodeModel,
            edit.getOperation(),
            edit.getTargetKey(),
            edit.getIndex(),
            edit.getValue());
    }

    private void applyStyle(NodeModel nodeModel, NodeContentEditItem edit) {
        nodeStyleContentEditor.editMainStyle(nodeModel, edit.getOperation(), edit.getValue());
    }

    private void applyHyperlink(NodeModel nodeModel, NodeContentEditItem edit) {
        hyperlinkContentEditor.editHyperlink(nodeModel, edit.getOperation(), edit.getValue());
    }

    private String resolveTextualValue(NodeContentEditItem edit) {
        EditOperation operation = edit.getOperation();
        EditedElement editedElement = edit.getEditedElement();
        if (operation == EditOperation.DELETE
            && (editedElement == EditedElement.DETAILS || editedElement == EditedElement.NOTE)) {
            return "";
        }
        if (operation != EditOperation.REPLACE) {
            throw new IllegalArgumentException("Only REPLACE operations are supported for this element.");
        }
        return edit.getValue();
    }
}
