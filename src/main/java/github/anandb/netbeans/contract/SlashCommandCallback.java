package github.anandb.netbeans.contract;

/** Callback for slash commands to trigger UI actions. */
public interface SlashCommandCallback {

    void expandOptionsPanel();

    void popupModelCombo();

    void popupAgentCombo();

    void popupThinkingCombo();

    void popupSessionCombo();

    void popupNewSession();

    /** Displays a simulated tool message in the chat thread. */
    void displayToolMessage(String title, String text);
}
