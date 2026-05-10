package org.freeplane.plugin.ai.tools.edit;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditRequest {
    private final String mapIdentifier;
    private final String userSummary;
    private final List<NodeContentEditItem> items;

    @JsonCreator
    public EditRequest(@JsonProperty("mapIdentifier") String mapIdentifier,
                       @JsonProperty("userSummary") String userSummary,
                       @JsonProperty("items") List<NodeContentEditItem> items) {
        this.mapIdentifier = mapIdentifier;
        this.userSummary = userSummary;
        this.items = items;
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getUserSummary() {
        return userSummary;
    }

    public List<NodeContentEditItem> getItems() {
        return items;
    }
}
