package github.anandb.netbeans.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable capability flags for the connected ACP agent. Derived once from the
 * agent name returned by the {@code initialize} handshake — the factory method
 * {@link #forName(String)} is the ONLY place where agent names are inspected;
 * all other code branches on these flags.
 */
public final class AgentCapabilities {

    /** Capabilities used when the agent name is unknown (opencode-like). */
    public static final AgentCapabilities DEFAULT = forName(null);

    private final boolean supportsMessageQueue;
    private final boolean sendsMcpServerConfig;
    private final boolean injectsEditorContext;
    private final boolean supportsTokenStats;
    private final boolean supportsMessageIds;

    private AgentCapabilities(Builder builder) {
        this.supportsMessageQueue = builder.supportsMessageQueue;
        this.sendsMcpServerConfig = builder.sendsMcpServerConfig;
        this.injectsEditorContext = builder.injectsEditorContext;
        this.supportsTokenStats = builder.supportsTokenStats;
        this.supportsMessageIds = builder.supportsMessageIds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean supportsMessageQueue() { return supportsMessageQueue; }
    public boolean sendsMcpServerConfig() { return sendsMcpServerConfig; }
    public boolean injectsEditorContext() { return injectsEditorContext; }
    public boolean supportsTokenStats() { return supportsTokenStats; }
    public boolean supportsMessageIds() { return supportsMessageIds; }

    /**
     * Derives capabilities from the agent name reported by the ACP
     * {@code initialize} handshake. Unknown or null names default to
     * opencode-like capabilities.
     *
     * @param agentName agent name, e.g. {@code "goose"}, {@code "pi-acp"}
     */
    public static AgentCapabilities forName(String agentName) {
        if (agentName == null) {
            agentName = "opencode";
        }
        return switch (agentName.trim().toLowerCase(Locale.ROOT)) {
            case "goose" -> builder()
                    .supportsMessageQueue(true)
                    .sendsMcpServerConfig(true)
                    .supportsMessageIds(true)
                    .build();
            case "pi-acp" -> builder()
                    .supportsMessageQueue(true)
                    .build();
            default -> builder()
                    .sendsMcpServerConfig(true)
                    .injectsEditorContext(true)
                    .supportsTokenStats(true)
                    .supportsMessageIds(true)
                    .build();
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentCapabilities that)) return false;
        return supportsMessageQueue == that.supportsMessageQueue
                && sendsMcpServerConfig == that.sendsMcpServerConfig
                && injectsEditorContext == that.injectsEditorContext
                && supportsTokenStats == that.supportsTokenStats
                && supportsMessageIds == that.supportsMessageIds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(supportsMessageQueue, sendsMcpServerConfig,
                injectsEditorContext, supportsTokenStats, supportsMessageIds);
    }

    public static final class Builder {
        private boolean supportsMessageQueue;
        private boolean sendsMcpServerConfig;
        private boolean injectsEditorContext;
        private boolean supportsTokenStats;
        private boolean supportsMessageIds;

        private Builder() {}

        public Builder supportsMessageQueue(boolean value) {
            this.supportsMessageQueue = value;
            return this;
        }

        public Builder sendsMcpServerConfig(boolean value) {
            this.sendsMcpServerConfig = value;
            return this;
        }

        public Builder injectsEditorContext(boolean value) {
            this.injectsEditorContext = value;
            return this;
        }

        public Builder supportsTokenStats(boolean value) {
            this.supportsTokenStats = value;
            return this;
        }

        public Builder supportsMessageIds(boolean value) {
            this.supportsMessageIds = value;
            return this;
        }

        public AgentCapabilities build() {
            return new AgentCapabilities(this);
        }
    }
}
