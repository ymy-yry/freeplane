package org.freeplane.plugin.ai.tools.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.core.util.TextUtils;
import org.freeplane.features.icon.IconRegistry;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.content.IconDescriptionResolver;
import org.freeplane.plugin.ai.tools.content.IconsContent;

public class IconsContentEditor {
    private final IconDescriptionResolver iconDescriptionResolver;
    private final Iterable<NamedIcon> defaultCandidates;
    private final MIconController iconController;

    public IconsContentEditor(IconDescriptionResolver iconDescriptionResolver, Iterable<NamedIcon> defaultCandidates,
                              MIconController iconController) {
        this.iconDescriptionResolver = Objects.requireNonNull(iconDescriptionResolver, "iconDescriptionResolver");
        this.defaultCandidates = Objects.requireNonNull(defaultCandidates, "defaultCandidates");
        this.iconController = Objects.requireNonNull(iconController, "iconController");
    }

    public void setInitialContent(NodeModel nodeModel, IconsContent iconsContent) {
        if (nodeModel == null || iconsContent == null) {
            return;
        }
        List<String> descriptions = iconsContent.getDescriptions();
        if (descriptions == null || descriptions.isEmpty()) {
            return;
        }
        IconRegistry iconRegistry = nodeModel.getMap().getIconRegistry();
        List<NamedIcon> candidates = collectCandidateIcons(iconRegistry);
        if (candidates.isEmpty()) {
            return;
        }
        Set<String> addedNames = new HashSet<>();
        for (String description : descriptions) {
            if (TextUtils.isEmpty(description)) {
                continue;
            }
            NamedIcon icon = findMatchingIcon(candidates, description.trim());
            if (icon != null && addedNames.add(icon.getName())) {
                nodeModel.addIcon(icon);
            }
        }
    }

    public void editExistingIconsContent(NodeModel nodeModel, EditOperation operation, String targetKey, Integer index,
                                         String value) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        EditOperation resolvedOperation = operation == null ? EditOperation.REPLACE : operation;
        List<NamedIcon> icons = new ArrayList<>(nodeModel.getIcons());
        switch (resolvedOperation) {
            case ADD:
                NamedIcon addedIcon = findIcon(nodeModel, requireIconDescription(value));
                if (addedIcon == null) {
                    throw new IllegalArgumentException("Unknown icon description: " + value);
                }
                iconController.addIcon(nodeModel, addedIcon);
                break;
            case DELETE:
                int deleteIndex = findIconIndex(icons, targetKey, index);
                if (deleteIndex < 0) {
                    throw new IllegalArgumentException("Invalid icon index for delete.");
                }
                iconController.removeIcon(nodeModel, deleteIndex);
                break;
            case REPLACE:
                int replaceIndex = findIconIndex(icons, targetKey, index);
                if (replaceIndex < 0) {
                    throw new IllegalArgumentException("Invalid icon index for replace.");
                }
                NamedIcon replacementIcon = findIcon(nodeModel, requireIconDescription(value));
                if (replacementIcon == null) {
                    throw new IllegalArgumentException("Unknown icon description: " + value);
                }
                iconController.removeIcon(nodeModel, replaceIndex);
                iconController.addIcon(nodeModel, replacementIcon);
                break;
            default:
                throw new IllegalArgumentException("Unsupported icon operation: " + resolvedOperation);
        }
    }

    private String requireIconDescription(String value) {
        if (TextUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Missing icon description.");
        }
        return value.trim();
    }

    private List<NamedIcon> collectCandidateIcons(IconRegistry iconRegistry) {
        List<NamedIcon> candidates = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        addIcons(candidates, seenNames, defaultCandidates);
        if (iconRegistry != null) {
            Iterator<NamedIcon> iterator = iconRegistry.getIconsAsListModel().iterator();
            addIcons(candidates, seenNames, () -> iterator);
        }
        return candidates;
    }

    private void addIcons(List<NamedIcon> target, Set<String> seenNames, Iterable<NamedIcon> source) {
        if (source == null) {
            return;
        }
        for (NamedIcon icon : source) {
            addIcon(target, seenNames, icon);
        }
    }

    private void addIcon(List<NamedIcon> target, Set<String> seenNames, NamedIcon icon) {
        if (icon == null || !seenNames.add(icon.getName())) {
            return;
        }
        target.add(icon);
    }

    private NamedIcon findMatchingIcon(List<NamedIcon> candidates, String description) {
        if (description == null) {
            return null;
        }
        for (NamedIcon candidate : candidates) {
            if (iconDescriptionResolver.matchesDescription(candidate, description)) {
                return candidate;
            }
        }
        return null;
    }

    private NamedIcon findIcon(NodeModel nodeModel, String description) {
        if (description == null) {
            return null;
        }
        IconRegistry iconRegistry = nodeModel.getMap().getIconRegistry();
        List<NamedIcon> candidates = collectCandidateIcons(iconRegistry);
        return findMatchingIcon(candidates, description);
    }

    private int findIconIndex(List<NamedIcon> icons, String targetKey, Integer index) {
        if (index != null && index >= 0 && index < icons.size()) {
            return index;
        }
        if (TextUtils.isEmpty(targetKey)) {
            return -1;
        }
        for (int iconIndex = 0; iconIndex < icons.size(); iconIndex++) {
            NamedIcon icon = icons.get(iconIndex);
            if (icon != null && iconDescriptionResolver.matchesDescription(icon, targetKey)) {
                return iconIndex;
            }
        }
        return -1;
    }
}
