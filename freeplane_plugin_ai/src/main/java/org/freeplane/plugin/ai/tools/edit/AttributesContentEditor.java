package org.freeplane.plugin.ai.tools.edit;

import java.util.List;
import java.util.Objects;

import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.content.AttributeEntry;
import org.freeplane.plugin.ai.tools.content.AttributesContent;

public class AttributesContentEditor {
    private final MAttributeController attributeController;

    public AttributesContentEditor(MAttributeController attributeController) {
        this.attributeController = Objects.requireNonNull(attributeController, "attributeController");
    }

    public void setInitialContent(NodeModel nodeModel, AttributesContent attributesContent) {
        if (nodeModel == null || attributesContent == null) {
            return;
        }
        List<AttributeEntry> attributes = attributesContent.getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        NodeAttributeTableModel attributeTableModel = NodeAttributeTableModel.getModel(nodeModel);
        for (AttributeEntry attributeEntry : attributes) {
            if (attributeEntry == null || attributeEntry.getName() == null) {
                continue;
            }
            String value = attributeEntry.getValue();
            Attribute attribute = new Attribute(attributeEntry.getName(), value == null ? "" : value);
            attributeTableModel.silentlyAddRowNoUndo(nodeModel, attribute);
        }
    }

    public void editExistingAttributesContent(NodeModel nodeModel, EditOperation operation, String targetKey,
                                              Integer index, String value) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        EditOperation resolvedOperation = operation == null ? EditOperation.REPLACE : operation;
        NodeAttributeTableModel model = NodeAttributeTableModel.getModel(nodeModel);
        switch (resolvedOperation) {
            case ADD:
                String name = requireAttributeName(targetKey);
                Attribute attribute = new Attribute(name, value == null ? "" : value);
                if (index == null) {
                    attributeController.addAttribute(nodeModel, attribute);
                } else {
                    int boundedIndex = Math.max(0, Math.min(index, model.getRowCount()));
                    attributeController.insertAttribute(nodeModel, boundedIndex, attribute);
                }
                break;
            case REPLACE:
                int targetIndex = findAttributeIndex(model, targetKey, index);
                if (targetIndex < 0) {
                    throw new IllegalArgumentException("Missing attribute index or name for replace.");
                }
                String attributeName = targetKey;
                if (attributeName == null) {
                    Attribute existing = model.getAttribute(targetIndex);
                    attributeName = existing == null ? null : existing.getName();
                }
                attributeController.setAttribute(nodeModel, targetIndex,
                    new Attribute(attributeName, value == null ? "" : value));
                break;
            case DELETE:
                int deleteIndex = findAttributeIndex(model, targetKey, index);
                if (deleteIndex < 0) {
                    throw new IllegalArgumentException("Missing attribute index or name for delete.");
                }
                attributeController.performRemoveAttribute(nodeModel, deleteIndex);
                break;
            default:
                throw new IllegalArgumentException("Unsupported attribute operation: " + resolvedOperation);
        }
    }

    private int findAttributeIndex(NodeAttributeTableModel model, String targetKey, Integer index) {
        if (index != null && index >= 0 && index < model.getRowCount()) {
            return index;
        }
        if (targetKey != null) {
            for (int row = 0; row < model.getRowCount(); row++) {
                Attribute attribute = model.getAttribute(row);
                if (attribute != null && targetKey.equals(attribute.getName())) {
                    return row;
                }
            }
        }
        return -1;
    }

    private String requireAttributeName(String targetKey) {
        if (targetKey == null || targetKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing attribute name.");
        }
        return targetKey;
    }
}
