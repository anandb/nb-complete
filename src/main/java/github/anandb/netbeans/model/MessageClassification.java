package github.anandb.netbeans.model;

/**
 * Represents a classification of a message into a role and kind.
 * Used for dynamic re-classification of messages based on their content.
 */
public record MessageClassification(String role, String kind) {}
