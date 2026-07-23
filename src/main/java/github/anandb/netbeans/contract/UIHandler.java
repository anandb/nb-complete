package github.anandb.netbeans.contract;

import java.util.List;

import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionConfigOption;

/** Receives processed content and dispatches to UI thread for rendering. */
public interface UIHandler {

    /** Renders a processed message in the chat panel. */
    void displayMessage(ProcessedMessage pm);

    /** Updates the session configuration options displayed in the UI. */
    void updateConfig(List<SessionConfigOption> options);

    /** Refreshes the list of available sessions. */
    void refreshSessions();

    /** Updates the displayed context window usage (used tokens / total). */
    void updateUsage(long used, long size);
}
