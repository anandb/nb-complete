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

    /** Capabilities used when the agent name is unknown (generic default). */
    public static final AgentCapabilities DEFAULT = forName(null);

    private final boolean supportsMessageQueue;
    private final boolean sendsMcpServerConfig;
    private final boolean injectsEditorContext;
    private final boolean supportsTokenStats;
    private final boolean supportsMessageIds;
    private final boolean supportsMcpServer;
    private final boolean supportsSessionSetMode;
    private final boolean supportsSessionList;
    private final String displayName;

    private AgentCapabilities(Builder builder) {
        this.supportsMessageQueue = builder.supportsMessageQueue;
        this.sendsMcpServerConfig = builder.sendsMcpServerConfig;
        this.injectsEditorContext = builder.injectsEditorContext;
        this.supportsTokenStats = builder.supportsTokenStats;
        this.supportsMessageIds = builder.supportsMessageIds;
        this.supportsMcpServer = builder.supportsMcpServer;
        this.supportsSessionSetMode = builder.supportsSessionSetMode;
        this.supportsSessionList = builder.supportsSessionList;
        this.displayName = builder.displayName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean supportsMessageQueue() { return supportsMessageQueue; }
    public boolean sendsMcpServerConfig() { return sendsMcpServerConfig; }
    public boolean injectsEditorContext() { return injectsEditorContext; }
    public boolean supportsTokenStats() { return supportsTokenStats; }
    public boolean supportsMessageIds() { return supportsMessageIds; }
    public boolean supportsMcpServer() { return supportsMcpServer; }
    public boolean supportsSessionSetMode() { return supportsSessionSetMode; }
    public boolean supportsSessionList() { return supportsSessionList; }
    public String displayName() { return displayName; }

    /**
     * Derives capabilities from the agent name reported by the ACP
     * {@code initialize} handshake. Unknown or null names default to
     * generic default capabilities.
     *
     * @param agentName agent name, e.g. {@code "goose"}, {@code "pi-acp"}, {@code "cursor"}
     */
    public static AgentCapabilities forName(String agentName) {
        String name = agentName == null ? "" : agentName.trim().toLowerCase(Locale.ROOT);
        // Handshake names include "cursor-agent-acp"; the CLI binary is "agent" / "cursor-agent".
        if (name.startsWith("cursor") || "agent".equals(name)) {
            return builder()
                    .displayName("Cursor")
                    .sendsMcpServerConfig(true)
                    .injectsEditorContext(true)
                    .supportsMcpServer(true)
                    .build();
        }
        // Exact agent name is unknown (e.g. claude, claude-acp, claude-code); match by prefix.
        if (name.startsWith("claude")) {
            return builder()
                    .displayName("Claude")
                    .sendsMcpServerConfig(true)
                    .injectsEditorContext(true)
                    .supportsTokenStats(false)
                    .supportsMessageIds(true)
                    .supportsMcpServer(true)
                    .supportsSessionSetMode(true)
                    .build();
        }
        // Hermes ACP: hermes-acp (the launchable entry point) and hermes
        // (matches by prefix so bare "hermes" also resolves). Chunk updates
        // carry no messageId, so streaming must merge by role instead.
        if (name.startsWith("hermes")) {
            return builder()
                    .displayName("Hermes")
                    .supportsMessageQueue(true)
                    .sendsMcpServerConfig(true)
                    .supportsMessageIds(false)
                    .supportsMcpServer(true)
                    .build();
        }
        // Gemini does not support session/list RPC; track sessions client-side.
        if (name.startsWith("gemini")) {
            return builder()
                    .displayName("Gemini")
                    .supportsSessionList(false)
                    .sendsMcpServerConfig(true)
                    .supportsMcpServer(true)
                    .build();
        }
        return switch (name) {
            case "goose" -> builder()
                    .displayName("Goose")
                    .supportsMessageQueue(true)
                    .sendsMcpServerConfig(true)
                    .supportsMessageIds(true)
                    .supportsMcpServer(true)
                    .build();
            case "pi", "pi-acp", "pi-agent" -> builder()
                    .displayName("Pi")
                    .supportsMessageQueue(true)
                    .supportsMcpServer(true)
                    .build();
            // OpenCode is the original default harness; give it an explicit entry
            // so unknown agents no longer present themselves as "OpenCode".
            case "opencode" -> builder()
                    .displayName("OpenCode")
                    .sendsMcpServerConfig(true)
                    .injectsEditorContext(true)
                    .supportsTokenStats(true)
                    .supportsMessageIds(true)
                    .supportsMcpServer(true)
                    .supportsSessionSetMode(false)
                    .build();
            default -> builder()
                    .displayName("Agent")
                    .sendsMcpServerConfig(true)
                    .injectsEditorContext(true)
                    .supportsTokenStats(true)
                    .supportsMessageIds(true)
                    .supportsMcpServer(true)
                    .supportsSessionSetMode(false)
                    .build();
        };
    }

    /**
     * Maps a resolved harness binary name to its toolbar icon file name
     * (theme-aware base name; the dark variant is selected by the icon loader).
     * Falls back to the plugin logo for harnesses without a dedicated icon.
     *
     * @param binaryName resolved harness binary name, e.g. {@code "cursor-agent"}
     * @return icon file name, never null
     */
    public static String harnessIconName(String binaryName) {
        if (binaryName == null) {
            return "agent.svg";
        }
        return switch (binaryName) {
            case "cursor-agent" -> "cursor.svg";
            case "claude-agent-acp" -> "claude.svg";
            case "goose" -> "goose.svg";
            case "pi-acp", "pi-agent" -> "pi-logo.svg";
            case "opencode" -> "opencode.svg";
            case "hermes" -> "hermes.svg";
            case "gemini" -> "agent.svg";
            default -> "agent.svg";
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
                && supportsMessageIds == that.supportsMessageIds
                && supportsMcpServer == that.supportsMcpServer
                && supportsSessionSetMode == that.supportsSessionSetMode
                && supportsSessionList == that.supportsSessionList
                && Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supportsMessageQueue, sendsMcpServerConfig,
                injectsEditorContext, supportsTokenStats, supportsMessageIds,
                supportsMcpServer, supportsSessionSetMode, supportsSessionList, displayName);
    }

    public static final class Builder {
        private boolean supportsMessageQueue;
        private boolean sendsMcpServerConfig;
        private boolean injectsEditorContext;
        private boolean supportsTokenStats;
        private boolean supportsMessageIds;
        private boolean supportsMcpServer;
        private boolean supportsSessionSetMode;
        private boolean supportsSessionList = true;
        private String displayName = "Agent";

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

        public Builder supportsMcpServer(boolean value) {
            this.supportsMcpServer = value;
            return this;
        }

        public Builder supportsSessionSetMode(boolean value) {
            this.supportsSessionSetMode = value;
            return this;
        }

        public Builder supportsSessionList(boolean value) {
            this.supportsSessionList = value;
            return this;
        }

        public Builder displayName(String value) {
            this.displayName = value;
            return this;
        }

        public AgentCapabilities build() {
            return new AgentCapabilities(this);
        }
    }
}
