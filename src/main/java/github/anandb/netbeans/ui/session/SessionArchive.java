package github.anandb.netbeans.ui.session;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.ui.assistant.ChatLayoutBuilder;
import github.anandb.netbeans.ui.assistant.MiniAssistantDialog;

/**
 * Toggles a session's archive flag. Archiving also dismisses the mini assistant when the
 * session list is not showing hidden sessions, so the archived session cannot stay on screen.
 */
public final class SessionArchive {

    private SessionArchive() {
    }

    /** Toggles {@code sessionId}; a null service or id is a no-op. */
    public static void toggle(SessionControl sessions, String sessionId) {
        if (sessions == null || sessionId == null) {
            return;
        }
        boolean newHidden = !sessions.isHidden(sessionId);
        sessions.setHidden(sessionId, newHidden);
        sessions.refreshSessions();
        if (newHidden && !ChatLayoutBuilder.isShowingHidden()) {
            MiniAssistantDialog.closeIfVisible();
        }
    }
}
