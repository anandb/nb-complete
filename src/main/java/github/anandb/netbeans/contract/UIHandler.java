package github.anandb.netbeans.contract;

import java.util.List;

import github.anandb.netbeans.model.ModelRecords.PlanEntry;
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

    /**
     * Replaces the live plan shown in the slideout above the message thread.
     * Called with the full, priority-sorted entry list on every {@code plan}
     * update; an empty list hides the slideout. Implementations must marshal
     * onto the EDT themselves.
     */
    void updatePlan(List<PlanEntry> entries);
}
