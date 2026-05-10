package org.freeplane.plugin.ai.tools.edit;

import org.freeplane.features.map.NodeModel;
import org.freeplane.features.styles.IStyle;
import org.freeplane.features.styles.LogicalStyleController;
import org.freeplane.features.styles.StyleNameMappingHelper;
import org.freeplane.features.styles.mindmapmode.MLogicalStyleController;

public class NodeStyleContentEditor {
    public void setInitialMainStyle(NodeModel nodeModel, String mainStyle) {
        if (nodeModel == null || mainStyle == null) {
            return;
        }
        if (mainStyle.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Invalid mainStyle: blank string. Omit mainStyle to use the default style.");
        }
        setMainStyle(nodeModel, mainStyle);
    }

    public void editMainStyle(NodeModel nodeModel, EditOperation operation, String styleName) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Missing edit operation.");
        }
        switch (operation) {
            case REPLACE:
                if (styleName == null || styleName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing style name.");
                }
                setMainStyle(nodeModel, styleName);
                return;
            case DELETE:
                clearMainStyle(nodeModel);
                return;
            default:
                throw new IllegalArgumentException("Unsupported style edit operation: " + operation);
        }
    }

    private void setMainStyle(NodeModel nodeModel, String styleName) {
        IStyle style = StyleNameMappingHelper.findStyleByNameOrThrow(nodeModel.getMap(), styleName);
        getStyleController().setStyle(nodeModel, style);
    }

    private void clearMainStyle(NodeModel nodeModel) {
        getStyleController().setStyle(nodeModel, null);
    }

    private MLogicalStyleController getStyleController() {
        return (MLogicalStyleController) LogicalStyleController.getController();
    }
}
