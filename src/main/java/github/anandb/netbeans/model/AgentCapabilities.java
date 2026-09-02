package github.anandb.netbeans.model;

/**
 * Immutable capability flags for the connected ACP agent. Derived once from the
 * agent name returned by the {@code initialize} handshake — the factory method
 * {@link #forName(String)} is the ONLY place where agent names are inspected;
 * all other code branches on these flags.
 *
 * @param supportsMessageQueue  true when messages may be queued during a turn
 *                              (envelope icon + MessageQueueManager)
 * @param sendsMcpServerConfig  true when {@code mcpServers} is sent with
 *                              session/prompt requests
 * @param injectsEditorContext  true when editor context (file path XML) is
 *                              attached to outgoing prompts
 * @param supportsTokenStats    true when the {@code <agent> stats} subprocess
 *                              is available for the TokenUsageDialog
 */
public record AgentCapabilities(
        boolean supportsMessageQueue,
        boolean sendsMcpServerConfig,
        boolean injectsEditorContext,
        boolean supportsTokenStats) {

    /** Capabilities used when the agent name is unknown (opencode-like). */
    public static final AgentCapabilities DEFAULT = forName(null);

    /**
     * Derives capabilities from the agent name reported by the ACP
     * {@code initialize} handshake. Unknown or null names default to
     * opencode-like capabilities (no queueing).
     *
     * @param agentName lowercased agent name, e.g. {@code "goose"}, {@code "pi-acp"}
     */
    public static AgentCapabilities forName(String agentName) {
        if (agentName == null) {
            return new AgentCapabilities(false, true, true, true);
        }
        return switch (agentName) {
            case "goose" -> new AgentCapabilities(true, true, true, false);
            case "pi-acp" -> new AgentCapabilities(false, false, false, false);
            default -> new AgentCapabilities(false, true, true, true);
        };
    }
}
