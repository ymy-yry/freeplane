package org.freeplane.plugin.ai.tools.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.core.util.TextUtils;
import org.freeplane.features.icon.TagCategories;
import org.freeplane.features.icon.TagReference;
import org.freeplane.features.icon.Tags;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.content.TagsContent;

public class TagsContentEditor {
    private final MIconController iconController;

    public TagsContentEditor(MIconController iconController) {
        this.iconController = Objects.requireNonNull(iconController, "iconController");
    }

    public void setInitialContent(NodeModel nodeModel, TagsContent tagsContent) {
        if (nodeModel == null || tagsContent == null) {
            return;
        }
        List<String> tags = tagsContent.getTags();
        if (tags == null || tags.isEmpty()) {
            return;
        }
        TagCategories tagCategories = nodeModel.getMap().getIconRegistry().getTagCategories();
        if (tagCategories == null) {
            return;
        }
        List<TagReference> references = new ArrayList<>();
        Set<String> texts = new HashSet<>();
        for (String tagText : tags) {
            if (TextUtils.isEmpty(tagText) || !texts.add(tagText.trim())) {
                continue;
            }
            references.add(tagCategories.createTagReference(tagText));
        }
        if (!references.isEmpty()) {
            Tags.setTagReferences(nodeModel, references);
        }
    }

    public void editExistingTagsContent(NodeModel nodeModel, EditOperation operation, String targetKey, Integer index,
                                        String value) {
        if (nodeModel == null) {
            throw new IllegalArgumentException("Missing node model.");
        }
        EditOperation resolvedOperation = operation == null ? EditOperation.REPLACE : operation;
        List<String> tags = new ArrayList<>(Tags.getTagReferences(nodeModel).size());
        for (TagReference reference : Tags.getTagReferences(nodeModel)) {
            tags.add(reference == null ? null : reference.getContent());
        }
        switch (resolvedOperation) {
            case ADD:
                String addedValue = requireTagValue(value);
                int addIndex = resolveAddIndex(tags.size(), index);
                insertTag(tags, addIndex, addedValue);
                setTagReferences(nodeModel, tags);
                break;
            case REPLACE:
                String replacementValue = requireTagValue(value);
                int replaceIndex = findTagIndex(tags, targetKey, index);
                if (replaceIndex < 0) {
                    throw new IllegalArgumentException("Invalid tag index for replace.");
                }
                tags.set(replaceIndex, replacementValue);
                setTagReferences(nodeModel, tags);
                break;
            case DELETE:
                int deleteIndex = findTagIndex(tags, targetKey, index);
                if (deleteIndex < 0) {
                    throw new IllegalArgumentException("Invalid tag index for delete.");
                }
                tags.remove(deleteIndex);
                setTagReferences(nodeModel, tags);
                break;
            default:
                throw new IllegalArgumentException("Unsupported tag operation: " + resolvedOperation);
        }
    }

    private String requireTagValue(String value) {
        if (TextUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Missing tag value.");
        }
        return value;
    }

    private int resolveAddIndex(int size, Integer index) {
        if (index == null) {
            return size;
        }
        if (index < 0) {
            return 0;
        }
        return Math.min(index, size);
    }

    private void insertTag(List<String> tags, int index, String value) {
        if (index < 0 || index > tags.size()) {
            tags.add(value);
        } else {
            tags.add(index, value);
        }
    }

    private int findTagIndex(List<String> tags, String targetKey, Integer index) {
        if (index != null && index >= 0 && index < tags.size()) {
            return index;
        }
        if (TextUtils.isEmpty(targetKey)) {
            return -1;
        }
        for (int tagIndex = 0; tagIndex < tags.size(); tagIndex++) {
            if (targetKey.equals(tags.get(tagIndex))) {
                return tagIndex;
            }
        }
        return -1;
    }

    private void setTagReferences(NodeModel nodeModel, List<String> tags) {
        TagCategories tagCategories = nodeModel.getMap().getIconRegistry().getTagCategories();
        if (tagCategories == null) {
            return;
        }
        List<TagReference> references = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            references.add(tagCategories.createTagReference(tag));
        }
        iconController.setTagReferences(nodeModel, references);
    }
}
