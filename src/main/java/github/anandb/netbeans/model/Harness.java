package github.anandb.netbeans.model;

import java.util.List;

import org.openide.util.NbBundle;

/**
 * One supported harness and everything needed to detect, launch, and use it.
 * Definitions live in {@code harness-catalog.yaml}; see {@link HarnessBuilder}
 * for the defaults an entry overrides.
 */
public record Harness(
        String id,
        String displayName,
        String iconBase,
        List<String> binaryNames,
        String launchArgs,
        /** True when a second {@code session/prompt} does not steer the in-flight
         *  turn, so messages must be queued until turn end. Read it off the entry
         *  rather than a list here: it is per harness and changes across major
         *  releases (OpenCode v1 steers, v2 queues — see
         *  {@link HarnessCatalog#forAgentVersion}). A queueing harness may still
         *  steer through its own extension — see {@link #supportsSessionSteer()}. */
        boolean requiresMessageQueue,
        boolean sendsMcpServerConfig,
        boolean injectsEditorContext,
        boolean supportsMessageIds,
        boolean supportsMcpServer,
        boolean supportsSessionSetMode,
        boolean supportsSessionSetConfigOption,
        /** True when model switching must go through ACP {@code session/set_model} (Hermes). */
        boolean supportsSessionSetModel,
        boolean supportsModelSelection,
        boolean supportsSessionList,
        /** True when the agent/mode dropdown is offered; false disables it (OpenClaw). */
        boolean supportsAgentList,
        /** True when a session replay must render every {@code agent_message_chunk}
         *  as its own bubble instead of merging the chunks of one turn. Jcode
         *  replays history as independent chunks with no message ids, so merging
         *  would collapse a whole turn into a single bubble. */
        boolean separateBubblesPerReplayChunk,
        /** Completeness of the plugin's support; see {@link HarnessCatalog.SupportTier}. */
        HarnessCatalog.SupportTier supportTier,
        /** Bundle key listing what a {@code LIMITED} harness cannot do, shown as the
         *  chooser tooltip; empty for {@code FULL}. */
        String limitationsKey,
        /** Bundle key of the message shown when model selection is unavailable; empty when none. */
        String unsupportedModelSelectionMessage,
        /** Bundle key of the placeholder item text shown in the disabled model
         *  dropdown when model selection is unavailable; empty when none. */
        String unsupportedModelSelectionPlaceholder,
        String installWindows,
        String installMac,
        String installLinux,
        String prerequisites,
        String docsUrl,
        List<String> windowsInstallSubDirs,
        /** True when the agent can carry the embedded MCP server's auth token on its
         *  MCP URL. False for the PI family and Jcode, which cannot, so the server
         *  runs without token enforcement for them. */
        boolean acceptsMcpAuthToken) {

    /** Starts a fluent definition; see {@link HarnessBuilder} for the defaults an entry overrides. */
    public static HarnessBuilder builder(String id, String displayName, String iconBase) {
        return new HarnessBuilder(id, displayName, iconBase);
    }

    /**
     * Resolves the unsupported-model-selection message from the model
     * bundle. The record field holds the bundle key (empty when the
     * harness has no such message), keeping the literal text localized.
     */
    @Override
    public String unsupportedModelSelectionMessage() {
        return unsupportedModelSelectionMessage == null || unsupportedModelSelectionMessage.isEmpty()
                ? "" : NbBundle.getMessage(Harness.class, unsupportedModelSelectionMessage);
    }

    /** Resolves the placeholder item text for the disabled model dropdown
     *  (empty when the harness has none). */
    @Override
    public String unsupportedModelSelectionPlaceholder() {
        return unsupportedModelSelectionPlaceholder == null || unsupportedModelSelectionPlaceholder.isEmpty()
                ? "" : NbBundle.getMessage(Harness.class, unsupportedModelSelectionPlaceholder);
    }

    /** Resolves what this harness cannot do (empty for a {@code FULL} harness). */
    public String limitations() {
        return limitationsKey == null || limitationsKey.isEmpty()
                ? "" : NbBundle.getMessage(Harness.class, limitationsKey);
    }

    /** True when the plugin's support for this harness is partial; see {@link HarnessCatalog.SupportTier}. */
    public boolean hasLimitedSupport() {
        return supportTier == HarnessCatalog.SupportTier.LIMITED;
    }

    /**
     * Returns a copy of this harness with only the message-queue capability
     * replaced. Used to derive a runtime variant (e.g. OpenCode v2) from its
     * catalog base without duplicating the remaining launch metadata, so the
     * two variants can never drift apart on any other flag.
     */
    public Harness withRequiresMessageQueue(boolean queueMessages) {
        return new Harness(id, displayName, iconBase, binaryNames, launchArgs, queueMessages,
                sendsMcpServerConfig, injectsEditorContext, supportsMessageIds, supportsMcpServer,
                supportsSessionSetMode, supportsSessionSetConfigOption, supportsSessionSetModel,
                supportsModelSelection, supportsSessionList, supportsAgentList,
                separateBubblesPerReplayChunk, supportTier, limitationsKey,
                unsupportedModelSelectionMessage, unsupportedModelSelectionPlaceholder,
                installWindows, installMac, installLinux, prerequisites, docsUrl,
                windowsInstallSubDirs, acceptsMcpAuthToken);
    }

    /** True when the given binary basename belongs to this harness, with or
     *  without a Windows executable extension ({@code omp}, {@code omp.exe}). */
    public boolean matchesBinary(String binaryName) {
        String base = HarnessCatalog.stripBinaryExtension(binaryName);
        return base != null && binaryNames.contains(base);
    }

    /**
     * True when the harness adds input to a running turn through its own protocol
     * extension instead of a second {@code session/prompt}. Only goose does:
     * {@code _goose/unstable/session/steer}, which needs the active run id the
     * harness publishes as {@code _meta.goose.activeRunId}.
     */
    public boolean supportsSessionSteer() {
        return "goose".equals(id);
    }

    /**
     * True when the harness steers a running turn through a slash command instead
     * of a protocol extension. Hermes exposes {@code /steer <guidance>}; the prompt
     * returns {@code end_turn} immediately while the original turn keeps running.
     */
    public boolean supportsSlashSteer() {
        return "hermes".equals(id);
    }

    /** True when the harness retitles a session server-side
     *  ({@code _goose/unstable/session/rename}); ACP itself has no rename. */
    public boolean supportsSessionRename() {
        return "goose".equals(id);
    }

    /** True when the harness moves a session's working directory live
     *  ({@code _goose/unstable/session/working-dir/update}), so a cwd change needs no
     *  process restart and terminates no sessions. */
    public boolean supportsWorkingDirUpdate() {
        return "goose".equals(id);
    }

    /** True when the harness can break down its context window
     *  ({@code _goose/unstable/context/report}). */
    public boolean supportsContextReport() {
        return "goose".equals(id);
    }
}
