package org.freeplane.plugin.ai.tools.edit;

import org.freeplane.plugin.ai.tools.content.ContentType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.model.output.structured.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeContentEditItem {
    @Description("Node ID to edit.")
    private final String nodeIdentifier;
    private final EditedElement editedElement;
    @JsonProperty(value = "originalContentType", required = false)
    @Description("From fetchNodesForEditing. Required for TEXT/DETAILS/NOTE; ignored for other elements.")
    private final ContentType originalContentType;
    @JsonProperty(required = false)
    @Description("New value. For PLAIN_TEXT formatting, use HTML; Markdown is literal unless originalContentType is MARKDOWN.")
    private final String value;
    @JsonProperty(required = false)
    @Description("List index for ATTRIBUTES/TAGS/ICONS. For ATTRIBUTES/TAGS ADD, inserts at this index when provided. For REPLACE/DELETE, index is preferred over targetKey. Ignored for TEXT/DETAILS/NOTE/STYLE/HYPERLINK and ICONS ADD.")
    private final Integer index;
    @JsonProperty(required = false)
    @Description("Optional operation (default REPLACE). Allowed values: TEXT=REPLACE only; DETAILS/NOTE=REPLACE or DELETE; ATTRIBUTES/TAGS/ICONS=ADD/REPLACE/DELETE; STYLE/HYPERLINK=REPLACE/DELETE.")
    private final EditOperation operation;
    @JsonProperty(required = false)
    @Description("Fallback selector for ATTRIBUTES/TAGS/ICONS when index is absent. ATTRIBUTES: attribute name (required for ADD). TAGS: existing tag text for REPLACE/DELETE. ICONS: existing icon description for REPLACE/DELETE. Ignored for TAGS/ICONS ADD and for TEXT/DETAILS/NOTE/STYLE/HYPERLINK.")
    private final String targetKey;

    @JsonCreator
    public NodeContentEditItem(@JsonProperty("nodeIdentifier") String nodeIdentifier,
                               @JsonProperty("editedElement") EditedElement editedElement,
                               @JsonProperty(value = "originalContentType", required = false) ContentType originalContentType,
                               @JsonProperty("value") String value,
                               @JsonProperty("index") Integer index,
                               @JsonProperty("operation") EditOperation operation,
                               @JsonProperty("targetKey") String targetKey) {
        this.nodeIdentifier = nodeIdentifier;
        this.editedElement = editedElement;
        this.originalContentType = originalContentType;
        this.value = value;
        this.index = index;
        this.operation = operation == null ? EditOperation.REPLACE : operation;
        this.targetKey = targetKey;
    }

    public EditedElement getEditedElement() {
        return editedElement;
    }

    public String getNodeIdentifier() {
        return nodeIdentifier;
    }

    public ContentType getOriginalContentType() {
        return originalContentType;
    }

    public String getValue() {
        return value;
    }

    public Integer getIndex() {
        return index;
    }

    public EditOperation getOperation() {
        return operation;
    }

    public String getTargetKey() {
        return targetKey;
    }
}
