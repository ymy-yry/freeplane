package org.freeplane.plugin.ai.tools.edit;

import java.util.Objects;

import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.NodeModel;

public class HyperlinkContentEditor {
    private final MLinkController linkController;

    public HyperlinkContentEditor(MLinkController linkController) {
        this.linkController = Objects.requireNonNull(linkController, "linkController");
    }

    public void setInitialHyperlink(NodeModel nodeModel, String hyperlink) {
        if (nodeModel == null || hyperlink == null || hyperlink.trim().isEmpty()) {
            return;
        }
        linkController.setLink(nodeModel, hyperlink, linkController.linkType());
    }

    public void editHyperlink(NodeModel nodeModel, EditOperation operation, String hyperlink) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Missing edit operation.");
        }
        if (operation == EditOperation.DELETE) {
            linkController.setLink(nodeModel, (String) null, LinkController.LINK_ABSOLUTE);
            return;
        }
        if (operation != EditOperation.REPLACE) {
            throw new IllegalArgumentException("Unsupported hyperlink edit operation: " + operation);
        }
        if (hyperlink == null) {
            linkController.setLink(nodeModel, (String) null, LinkController.LINK_ABSOLUTE);
            return;
        }
        linkController.setLink(nodeModel, hyperlink, linkController.linkType());
    }
}
