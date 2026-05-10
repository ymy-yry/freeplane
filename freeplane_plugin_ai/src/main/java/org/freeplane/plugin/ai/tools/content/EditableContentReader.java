package org.freeplane.plugin.ai.tools.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.icon.TagReference;
import org.freeplane.features.icon.Tags;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailModel;
import org.freeplane.features.text.TextController;

public class EditableContentReader {
    private final TextController textController;
    private final IconDescriptionResolver iconDescriptionResolver;
    private final ContentTypeConverter contentTypeConverter;

    public EditableContentReader(TextController textController, IconDescriptionResolver iconDescriptionResolver,
                                 ContentTypeConverter contentTypeConverter) {
        this.textController = Objects.requireNonNull(textController, "textController");
        this.iconDescriptionResolver = Objects.requireNonNull(iconDescriptionResolver, "iconDescriptionResolver");
        this.contentTypeConverter = Objects.requireNonNull(contentTypeConverter, "contentTypeConverter");
    }

    public EditableContent readEditableContent(NodeModel nodeModel, EditableContentRequest request) {
        if (nodeModel == null || request == null) {
            return null;
        }
        EditableText editableText = request.includesField(EditableContentField.TEXT)
            ? buildEditableTextForNodeText(nodeModel)
            : null;
        EditableText editableDetails = request.includesField(EditableContentField.DETAILS)
            ? buildEditableText(DetailModel.getDetailText(nodeModel),
                DetailModel.getDetailContentType(nodeModel), nodeModel, DetailModel.getDetail(nodeModel))
            : null;
        EditableText editableNote = request.includesField(EditableContentField.NOTE)
            ? buildEditableText(NoteModel.getNoteText(nodeModel),
                NoteModel.getNoteContentType(nodeModel), nodeModel, NoteModel.getNote(nodeModel))
            : null;
        List<EditableAttribute> editableAttributes = request.includesField(EditableContentField.ATTRIBUTES)
            ? buildEditableAttributes(nodeModel)
            : null;
        List<EditableTag> editableTags = request.includesField(EditableContentField.TAGS)
            ? buildEditableTags(nodeModel)
            : null;
        List<EditableIcon> editableIcons = request.includesField(EditableContentField.ICONS)
            ? buildEditableIcons(nodeModel)
            : null;
        if (editableText == null && editableDetails == null && editableNote == null
            && editableAttributes == null && editableTags == null && editableIcons == null) {
            return null;
        }
        return new EditableContent(editableText, editableDetails, editableNote,
            editableAttributes, editableTags, editableIcons);
    }

    private EditableText buildEditableTextForNodeText(NodeModel nodeModel) {
        Object rawObject = nodeModel.getUserObject();
        String rawValue = rawObject == null ? null : String.valueOf(rawObject);
        String transformedValue = null;
        if (rawObject != null) {
            Object transformed = textController.getTransformedTextForClipboard(nodeModel, nodeModel, rawObject);
            transformedValue = transformed == null ? null : String.valueOf(transformed);
        }
        String plainValue = null;
        if (transformedValue != null) {
            plainValue = HtmlUtils.htmlToPlain(transformedValue);
        }
        boolean formulaDetected = rawObject != null && textController.isFormula(rawObject);
        ContentType contentType = rawObject == null
            ? null
            : (formulaDetected
                ? ContentType.FORMULA
                : contentTypeConverter.toTextContentTypeForNode(textController.getNodeFormat(nodeModel), rawValue));
        Boolean isEditable = rawObject == null ? null : !formulaDetected;
        return new EditableText(rawValue, transformedValue, plainValue, contentType, isEditable);
    }

    private EditableText buildEditableText(Object rawObject, String freeplaneContentType, NodeModel nodeModel,
                                           Object nodeProperty) {
        String rawValue = rawObject == null ? null : String.valueOf(rawObject);
        String transformedValue = null;
        if (rawObject != null) {
            Object transformed = textController.getTransformedTextForClipboard(nodeModel, nodeProperty, rawObject);
            transformedValue = transformed == null ? null : String.valueOf(transformed);
        }
        String plainValue = null;
        if (transformedValue != null) {
            plainValue = HtmlUtils.htmlToPlain(transformedValue);
        }
        boolean formulaDetected = rawObject != null && textController.isFormula(rawObject);
        ContentType contentType = rawObject == null
            ? null
            : contentTypeConverter.toContentType(freeplaneContentType, formulaDetected, rawValue);
        Boolean isEditable = rawObject == null ? null : !formulaDetected;
        return new EditableText(rawValue, transformedValue, plainValue, contentType, isEditable);
    }

    private List<EditableAttribute> buildEditableAttributes(NodeModel nodeModel) {
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        int rowCount = attributeTableModel.getRowCount();
        if (rowCount == 0) {
            return null;
        }
        List<EditableAttribute> attributes = new ArrayList<>(rowCount);
        for (int row = 0; row < rowCount; row++) {
            Attribute attribute = attributeTableModel.getAttribute(row);
            if (attribute == null) {
                continue;
            }
            String rawValue = Objects.toString(attribute.getValue(), null);
            String transformedValue = null;
            if (rawValue != null) {
                Object transformed = textController.getTransformedObjectNoFormattingNoThrow(
                    nodeModel, attributeTableModel, rawValue);
                transformedValue = transformed == null ? null : String.valueOf(transformed);
            }
            String plainValue = null;
            if (transformedValue != null) {
                plainValue = HtmlUtils.htmlToPlain(transformedValue);
            }
            boolean formulaDetected = rawValue != null && textController.isFormula(rawValue);
            Boolean isEditable = rawValue == null ? null : !formulaDetected;
            attributes.add(new EditableAttribute(attribute.getName(),
                rawValue,
                transformedValue,
                plainValue,
                isEditable,
                row));
        }
        return attributes.isEmpty() ? null : attributes;
    }

    private List<EditableTag> buildEditableTags(NodeModel nodeModel) {
        List<TagReference> tagReferences = Tags.getTagReferences(nodeModel);
        if (tagReferences == null || tagReferences.isEmpty()) {
            return null;
        }
        List<EditableTag> tags = new ArrayList<>(tagReferences.size());
        for (int index = 0; index < tagReferences.size(); index++) {
            TagReference reference = tagReferences.get(index);
            if (reference == null) {
                continue;
            }
            tags.add(new EditableTag(reference.getContent(), index));
        }
        return tags.isEmpty() ? null : tags;
    }

    private List<EditableIcon> buildEditableIcons(NodeModel nodeModel) {
        List<NamedIcon> icons = nodeModel.getIcons();
        if (icons.isEmpty()) {
            return null;
        }
        List<EditableIcon> editableIcons = new ArrayList<>(icons.size());
        for (int index = 0; index < icons.size(); index++) {
            NamedIcon icon = icons.get(index);
            if (icon == null) {
                continue;
            }
            editableIcons.add(new EditableIcon(iconDescriptionResolver.resolveDescription(icon), index));
        }
        return editableIcons.isEmpty() ? null : editableIcons;
    }
}
