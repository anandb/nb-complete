package github.anandb.netbeans.contract;

import java.util.List;

import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionConfigOption;

/**
 * Receives processed content from {@link DataExtractionStrategy} implementations
 * and dispatches it to the UI thread for rendering.
 */
public interface UIHandler {

    /** Displays a processed message (thought, tool result, code block, etc.) in the chat thread. */
    void displayMessage(ProcessedMessage pm);

    /** Updates the displayed session config options (model, agent, thinking level). */
    void updateConfig(List<SessionConfigOption> options);

    /** Triggers a refresh of the session list in the UI. */
    void refreshSessions();

    /** Updates the token usage display. */
    void updateUsage(long used, long size);
}
