package org.freeplane.plugin.ai.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mind-map validation result.
 */
public class MindMapValidationResult {

    public enum ValidationStatus {
        /** Validation passed. */
        VALID,
        /** Validation failed — has errors. */
        INVALID,
        /** Validation warning — issues found but map is still usable. */
        WARNING
    }

    private ValidationStatus status;
    private final List<ValidationError> errors;
    private final List<ValidationWarning> warnings;
    private MindMapStatistics statistics;

    public MindMapValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.status = ValidationStatus.VALID;
    }

    public static MindMapValidationResult success() {
        return new MindMapValidationResult();
    }

    public static MindMapValidationResult error(String code, String message) {
        MindMapValidationResult result = new MindMapValidationResult();
        result.addError(code, message);
        return result;
    }

    public static MindMapValidationResult error(String code, String message, String nodeId) {
        MindMapValidationResult result = new MindMapValidationResult();
        result.addError(code, message, nodeId);
        return result;
    }

    public void addError(String code, String message) {
        this.errors.add(new ValidationError(code, message));
        this.status = ValidationStatus.INVALID;
    }

    public void addError(String code, String message, String nodeId) {
        this.errors.add(new ValidationError(code, message, nodeId));
        this.status = ValidationStatus.INVALID;
    }

    public void addWarning(String code, String message) {
        this.warnings.add(new ValidationWarning(code, message));
        if (this.status == ValidationStatus.VALID) {
            this.status = ValidationStatus.WARNING;
        }
    }

    public void addWarning(String code, String message, String nodeId) {
        this.warnings.add(new ValidationWarning(code, message, nodeId));
        if (this.status == ValidationStatus.VALID) {
            this.status = ValidationStatus.WARNING;
        }
    }

    public boolean isValid() {
        return status == ValidationStatus.VALID || status == ValidationStatus.WARNING;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<ValidationWarning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public MindMapStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(MindMapStatistics statistics) {
        this.statistics = statistics;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation result: ").append(status).append("\n");

        if (!errors.isEmpty()) {
            sb.append("Errors (").append(errors.size()).append("):\n");
            for (ValidationError error : errors) {
                sb.append("  [").append(error.getCode()).append("] ");
                if (error.getNodeId() != null) {
                    sb.append("node(").append(error.getNodeId()).append("): ");
                }
                sb.append(error.getMessage()).append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            sb.append("Warnings (").append(warnings.size()).append("):\n");
            for (ValidationWarning warning : warnings) {
                sb.append("  [").append(warning.getCode()).append("] ");
                if (warning.getNodeId() != null) {
                    sb.append("node(").append(warning.getNodeId()).append("): ");
                }
                sb.append(warning.getMessage()).append("\n");
            }
        }

        if (statistics != null) {
            sb.append("Statistics:\n");
            sb.append("  Total nodes: ").append(statistics.getTotalNodes()).append("\n");
            sb.append("  Max depth: ").append(statistics.getMaxDepth()).append("\n");
            sb.append("  Avg children/node: ").append(String.format("%.2f", statistics.getAverageChildrenPerNode())).append("\n");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** Validation error. */
    public static class ValidationError {
        private final String code;
        private final String message;
        private final String nodeId;

        public ValidationError(String code, String message) {
            this(code, message, null);
        }

        public ValidationError(String code, String message, String nodeId) {
            this.code = code;
            this.message = message;
            this.nodeId = nodeId;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
        public String getNodeId() { return nodeId; }
    }

    /** Validation warning. */
    public static class ValidationWarning {
        private final String code;
        private final String message;
        private final String nodeId;

        public ValidationWarning(String code, String message) {
            this(code, message, null);
        }

        public ValidationWarning(String code, String message, String nodeId) {
            this.code = code;
            this.message = message;
            this.nodeId = nodeId;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
        public String getNodeId() { return nodeId; }
    }

    /** Mind-map statistics. */
    public static class MindMapStatistics {
        private int totalNodes;
        private int maxDepth;
        private int maxChildrenPerNode;
        private double averageChildrenPerNode;
        private int leafNodes;
        private int internalNodes;

        public int getTotalNodes() { return totalNodes; }
        public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }

        public int getMaxChildrenPerNode() { return maxChildrenPerNode; }
        public void setMaxChildrenPerNode(int maxChildrenPerNode) { this.maxChildrenPerNode = maxChildrenPerNode; }

        public double getAverageChildrenPerNode() { return averageChildrenPerNode; }
        public void setAverageChildrenPerNode(double averageChildrenPerNode) { this.averageChildrenPerNode = averageChildrenPerNode; }

        public int getLeafNodes() { return leafNodes; }
        public void setLeafNodes(int leafNodes) { this.leafNodes = leafNodes; }

        public int getInternalNodes() { return internalNodes; }
        public void setInternalNodes(int internalNodes) { this.internalNodes = internalNodes; }
    }
}
