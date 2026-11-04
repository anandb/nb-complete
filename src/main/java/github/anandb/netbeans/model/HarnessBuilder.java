package github.anandb.netbeans.model;

import java.util.List;

/**
 * Fluent builder for {@link HarnessCatalog.Harness}. A catalog entry sets only
 * what differs from the defaults below, so its capabilities read as named
 * deviations instead of a positional run of booleans.
 *
 * <p>Defaults: {@code requiresMessageQueue}, {@code supportsMessageIds},
 * {@code supportsSessionSetMode} and {@code supportsSessionSetModel} are
 * {@code false}; {@code sendsMcpServerConfig}, {@code injectsEditorContext},
 * {@code supportsMcpServer}, {@code supportsSessionSetConfigOption},
 * {@code supportsModelSelection}, {@code supportsSessionList},
 * {@code supportsAgentList} and {@code acceptsMcpAuthToken} are {@code true};
 * {@code separateBubblesPerReplayChunk} is {@code false}; {@code supportTier} is
 * {@code FULL}; every text, list and install field is empty.</p>
 */
public final class HarnessBuilder {

    private final String id;
    private final String displayName;
    private final String iconBase;
    private List<String> binaryNames = List.of();
    private String launchArgs = "";
    private boolean requiresMessageQueue;
    private boolean sendsMcpServerConfig = true;
    private boolean injectsEditorContext = true;
    private boolean supportsMessageIds;
    private boolean supportsMcpServer = true;
    private boolean supportsSessionSetMode;
    private boolean supportsSessionSetConfigOption = true;
    private boolean supportsSessionSetModel;
    private boolean supportsModelSelection = true;
    private boolean supportsSessionList = true;
    private boolean supportsAgentList = true;
    private boolean separateBubblesPerReplayChunk;
    private HarnessCatalog.SupportTier supportTier = HarnessCatalog.SupportTier.FULL;
    private String limitationsKey = "";
    private boolean acceptsMcpAuthToken = true;
    private String unsupportedModelSelectionMessage = "";
    private String unsupportedModelSelectionPlaceholder = "";
    private String installWindows = "";
    private String installMac = "";
    private String installLinux = "";
    private String prerequisites = "";
    private String docsUrl = "";
    private List<String> windowsInstallSubDirs = List.of();

    /** Starts a definition; the identity fields are the only required ones. */
    public HarnessBuilder(String id, String displayName, String iconBase) {
        this.id = id;
        this.displayName = displayName;
        this.iconBase = iconBase;
    }

    public HarnessBuilder binaryNames(String... names) {
        this.binaryNames = List.of(names);
        return this;
    }

    public HarnessBuilder launchArgs(String args) {
        this.launchArgs = args;
        return this;
    }

    public HarnessBuilder requiresMessageQueue(boolean value) {
        this.requiresMessageQueue = value;
        return this;
    }

    public HarnessBuilder sendsMcpServerConfig(boolean value) {
        this.sendsMcpServerConfig = value;
        return this;
    }

    public HarnessBuilder injectsEditorContext(boolean value) {
        this.injectsEditorContext = value;
        return this;
    }

    public HarnessBuilder supportsMessageIds(boolean value) {
        this.supportsMessageIds = value;
        return this;
    }

    public HarnessBuilder supportsMcpServer(boolean value) {
        this.supportsMcpServer = value;
        return this;
    }

    public HarnessBuilder supportsSessionSetMode(boolean value) {
        this.supportsSessionSetMode = value;
        return this;
    }

    public HarnessBuilder supportsSessionSetConfigOption(boolean value) {
        this.supportsSessionSetConfigOption = value;
        return this;
    }

    public HarnessBuilder supportsSessionSetModel(boolean value) {
        this.supportsSessionSetModel = value;
        return this;
    }

    public HarnessBuilder supportsModelSelection(boolean value) {
        this.supportsModelSelection = value;
        return this;
    }

    public HarnessBuilder supportsSessionList(boolean value) {
        this.supportsSessionList = value;
        return this;
    }

    public HarnessBuilder supportsAgentList(boolean value) {
        this.supportsAgentList = value;
        return this;
    }

    public HarnessBuilder separateBubblesPerReplayChunk(boolean value) {
        this.separateBubblesPerReplayChunk = value;
        return this;
    }

    /** Marks a harness as partially supported, with a bundle key listing what it cannot do. */
    public HarnessBuilder limited(String limitationsBundleKey) {
        this.supportTier = HarnessCatalog.SupportTier.LIMITED;
        this.limitationsKey = limitationsBundleKey;
        return this;
    }

    public HarnessBuilder acceptsMcpAuthToken(boolean value) {
        this.acceptsMcpAuthToken = value;
        return this;
    }

    /** Bundle keys for the message and the dropdown placeholder shown when
     *  model selection is unavailable. */
    public HarnessBuilder unsupportedModelSelection(String messageKey, String placeholderKey) {
        this.unsupportedModelSelectionMessage = messageKey;
        this.unsupportedModelSelectionPlaceholder = placeholderKey;
        return this;
    }

    public HarnessBuilder install(String windows, String mac, String linux) {
        this.installWindows = windows;
        this.installMac = mac;
        this.installLinux = linux;
        return this;
    }

    public HarnessBuilder prerequisites(String text) {
        this.prerequisites = text;
        return this;
    }

    public HarnessBuilder docsUrl(String url) {
        this.docsUrl = url;
        return this;
    }

    public HarnessBuilder windowsInstallSubDirs(String... dirs) {
        this.windowsInstallSubDirs = List.of(dirs);
        return this;
    }

    public HarnessCatalog.Harness build() {
        return new HarnessCatalog.Harness(id, displayName, iconBase, binaryNames, launchArgs,
                requiresMessageQueue, sendsMcpServerConfig, injectsEditorContext, supportsMessageIds,
                supportsMcpServer, supportsSessionSetMode, supportsSessionSetConfigOption,
                supportsSessionSetModel, supportsModelSelection, supportsSessionList, supportsAgentList,
                separateBubblesPerReplayChunk, supportTier, limitationsKey,
                unsupportedModelSelectionMessage, unsupportedModelSelectionPlaceholder,
                installWindows, installMac, installLinux, prerequisites, docsUrl,
                windowsInstallSubDirs, acceptsMcpAuthToken);
    }
}
