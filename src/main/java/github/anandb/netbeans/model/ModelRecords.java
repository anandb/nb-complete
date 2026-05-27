package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.contract.SlashCommandHandler;

/**
 * Container for small model records.
 * Keeps them in one file to reduce class count without sacrificing clarity.
 */
public final class ModelRecords {
    private ModelRecords() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Agent(String name, String description) {}

    public record CommandInfo(SlashCommandHandler handler, String description) {}

    public record ConfigItem(String name, String value, String baseName) {
        public ConfigItem(String name, String value) {
            this(name, value, name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Represents a classification of a message into a role and kind.
     * Used for dynamic re-classification of messages based on their content.
     */
    public record MessageClassification(MessageType type, String kind) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanEntry(String priority, String status, String content) {}
}
