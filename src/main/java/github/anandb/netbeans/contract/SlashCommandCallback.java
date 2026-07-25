package github.anandb.netbeans.contract;

/**
 * Callback interface for slash commands to trigger UI actions.
 * Implementations bridge from the command interceptor (background) to
 * the Swing UI thread for operations like opening combo popups, toggling
 * panels, and displaying tool messages.
 */
public interface SlashCommandCallback {

    /** Expands the options panel if it is currently collapsed. */
    void expandOptionsPanel();

    /** Opens the model selection dropdown with auto-collapse on close. */
    void popupModelCombo();

    /** Opens the agent/mode selection dropdown with auto-collapse on close. */
    void popupAgentCombo();

    /** Opens the thinking level dropdown with auto-collapse on close. */
    void popupThinkingCombo();

    /** Opens the session selection dropdown. */
    void popupSessionCombo();

    /** Creates and focuses a new session. */
    void popupNewSession();

    /** Toggles the archive state of the current session. */
    void popupArchiveSession();

    /**
     * Displays a simulated tool_call_update message in the chat thread.
     * @param title the tool title to display
     * @param text  the tool body content
     */
    void displayToolMessage(String title, String text);

    /** Transition UI to sending mode (Stop button, thinking state).
     *  Default no-op for commands that don't send messages. */
    default void onAsyncSendStarted() {}

    /** Transition UI back to idle mode after the RPC completes.
     *  Default no-op. */
    default void onAsyncSendCompleted() {}
}
