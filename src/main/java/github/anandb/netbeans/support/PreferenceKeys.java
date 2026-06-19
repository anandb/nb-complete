package github.anandb.netbeans.support;

/**
 * Central location for NbPreferences module anchor and preference keys.
 * Replaces the upward dependency on ACPOptionsPanel as a classloader anchor.
 */
public final class PreferenceKeys {

    private PreferenceKeys() {}

    /**
     * The class used as the NbPreferences module anchor.
     * This replaces NbPreferences.forModule(ACPOptionsPanel.class) calls
     * that created an upward dependency from manager/ to ui/.
     */
    public static final Class<?> MODULE_ANCHOR = PreferenceKeys.class;

    // Preference keys (must match what ACPOptionsPanel uses)
    public static final String ACP_EXECUTABLE_PATH = "acpExecutablePath";
    public static final String PROCESS_ARGUMENTS = "processArguments";
    public static final String ECHO_USER_INPUT = "echoUserInput";
    public static final String COMBINE_TOOL_THOUGHT = "combineToolThought";

    // Input history keys
    public static final String INPUT_HISTORY_COUNT = "inputHistory.count";
    public static final String INPUT_HISTORY_PREFIX = "inputHistory.";

    // Toolbar button visibility keys
    public static final String TOOLBAR_ARCHIVE = "toolbar.archive";
    public static final String TOOLBAR_NEW_SESSION = "toolbar.newSession";
    public static final String TOOLBAR_RENAME_SESSION = "toolbar.renameSession";
    public static final String TOOLBAR_RELOAD = "toolbar.reload";
    public static final String TOOLBAR_KEEP = "toolbar.keep";
    public static final String TOOLBAR_EXPAND_COLLAPSE = "toolbar.expandCollapse";
    public static final String TOOLBAR_FILTER = "toolbar.filter";
    public static final String TOOLBAR_EXPORT = "toolbar.export";
    public static final String TOOLBAR_RESTART = "toolbar.restart";
}
