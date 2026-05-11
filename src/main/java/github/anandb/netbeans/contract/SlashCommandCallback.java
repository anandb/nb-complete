package github.anandb.netbeans.contract;

/**
 * Callback for slash commands to trigger UI actions in the chat window.
 */
public interface SlashCommandCallback {

    /** Expands the collapsible options panel (model, agent, thinking, mode). */
    void expandOptionsPanel();

    /** Opens the model dropdown. */
    void popupModelCombo();

    /** Opens the agent dropdown. */
    void popupAgentCombo();

    /** Opens the thinking/reasoning level dropdown. */
    void popupThinkingCombo();

    /** Opens the session selection dropdown. */
    void popupSessionCombo();

    /** Triggers the new-session flow. */
    void popupNewSession();
}
