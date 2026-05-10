package org.freeplane.plugin.ai.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Mind-map node model (lightweight in-memory representation used only during validation,
 * decoupled from Freeplane’s core NodeModel).
 */
public class MindMapNode {
    private String id;
    private String text;
    private List<MindMapNode> children;
    private MindMapNode parent;

    public MindMapNode() {
        this.children = new ArrayList<>();
    }

    public MindMapNode(String id, String text) {
        this.id = id;
        this.text = text;
        this.children = new ArrayList<>();
    }

    public MindMapNode(String id, String text, List<MindMapNode> children) {
        this.id = id;
        this.text = text;
        this.children = children != null ? children : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<MindMapNode> getChildren() { return children; }
    public void setChildren(List<MindMapNode> children) { this.children = children; }

    public MindMapNode getParent() { return parent; }
    public void setParent(MindMapNode parent) { this.parent = parent; }

    public void addChild(MindMapNode child) {
        if (child != null) {
            child.setParent(this);
            this.children.add(child);
        }
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public int getChildCount() {
        return children != null ? children.size() : 0;
    }

    @Override
    public String toString() {
        return "MindMapNode{id='" + id + "', text='" + text + "', childrenCount=" + getChildCount() + '}';
    }
}
