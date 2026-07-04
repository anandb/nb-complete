package github.anandb.netbeans.ui.vm;

import java.util.List;

import github.anandb.netbeans.model.SessionItem;

/**
 * Swing-free view-model for the chat toolbar (header strip + status row).
 * Mirrors the mutable {@code JButton}/{@code JLabel} state today scattered
 * across {@code AssistantTopComponent} and {@code ChatLayoutBuilder}.
 * <p>
 * This is the seam a future declarative DSL (swingtree) binds to: a view-model
 * record with withers, no {@code javax.swing} imports. Today it is used as a
 * thin indirection layer by the imperative code; the DSL migration replaces the
 * imperative construction with {@code UI.of(this).add(...)} bound to lenses
 * over this record.
 * <p>
 * <b>DSL-ready:</b> immutable record + withers, Swing-free.
 */
public record ChatToolbarVM(
        List<SessionItem> sessionItems,
        SessionItem selectedSession,
        String statusText,
        String versionText,
        String cwdText,
        boolean sendEnabled,
        boolean stopEnabled,
        boolean sessionControlsEnabled,
        boolean newSessionEnabled,
        boolean restartServerEnabled,
        boolean keepOlderMessages,
        boolean optionsPanelVisible,
        boolean blocksExpanded
) {
    public static ChatToolbarVM empty() {
        return new ChatToolbarVM(
                List.of(), null, "", "", "",
                false, false, false, true, false,
                false, false, true);
    }

    public ChatToolbarVM withSessionItems(List<SessionItem> v) {
        return new ChatToolbarVM(v, selectedSession, statusText, versionText, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withSelectedSession(SessionItem v) {
        return new ChatToolbarVM(sessionItems, v, statusText, versionText, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withStatusText(String v) {
        return new ChatToolbarVM(sessionItems, selectedSession, v, versionText, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withVersionText(String v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, v, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withCwdText(String v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, v,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withSendEnabled(boolean v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, cwdText,
                v, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withStopEnabled(boolean v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, cwdText,
                sendEnabled, v, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withSessionControlsEnabled(boolean v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, cwdText,
                sendEnabled, stopEnabled, v, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withNewSessionEnabled(boolean v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, v,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withRestartServerEnabled(boolean v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                v, keepOlderMessages, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withKeepOlderMessages(boolean v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, v, optionsPanelVisible, blocksExpanded);
    }
    public ChatToolbarVM withOptionsPanelVisible(boolean v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, v, blocksExpanded);
    }
    public ChatToolbarVM withBlocksExpanded(boolean v) {
        return new ChatToolbarVM(sessionItems, selectedSession, statusText, versionText, cwdText,
                sendEnabled, stopEnabled, sessionControlsEnabled, newSessionEnabled,
                restartServerEnabled, keepOlderMessages, optionsPanelVisible, v);
    }

    /** The currently selected session id, or null if none. */
    public String selectedSessionId() {
        return selectedSession == null ? null : selectedSession.getSession().id();
    }
}
