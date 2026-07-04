package github.anandb.netbeans.ui.spec;

import github.anandb.netbeans.model.SessionItem;

/**
 * Callback contract for the chat toolbar actions. Implemented by
 * {@code AssistantTopComponent} (or a lambda) and passed to
 * {@link ChatLayoutSpec#build} so the spec builder can wire Swing listeners
 * without depending on the top component directly.
 * <p>
 * All methods default to no-op so partial implementations (e.g. a headless
 * test harness) are valid. This is the DSL-ready action seam: a future
 * swingtree view receives this interface and binds {@code .onClick(...)} /
 * {@code .onChange(...)} calls to it.
 * <p>
 * <b>Swing-free</b> — only carries model types ({@link SessionItem}) and
 * primitives, so it can live across the imperative/declarative boundary.
 */
public interface ChatToolbarActions {

    /** Send the current input draft. */
    default void onSend() {}

    /** Stop the active stream. */
    default void onStop() {}

    /** Create a new session (debounced in the imperative shell). */
    default void onNewSession() {}

    /** Rename the current session. */
    default void onRenameSession() {}

    /** Archive/unarchive (hide) the current session. */
    default void onArchive() {}

    /** Toggle the "show hidden sessions" preference. */
    default void onShowHiddenToggle() {}

    /** Toggle the agent/model/thinking options panel visibility. */
    default void onToggleOptions() {}

    /** Toggle expand/collapse of all code/activity blocks. */
    default void onToggleBlocks() {}

    /** Toggle "pin to keep older messages". */
    default void onKeep() {}

    /** Open the message-type filter popup. */
    default void onFilter() {}

    /** Open the keyboard-shortcuts help dialog. */
    default void onHelp() {}

    /** Restart the ACP server process. */
    default void onRestartServer() {}

    /** Reload the current session from server. */
    default void onRefresh() {}

    /** Export the current conversation. */
    default void onExport() {}

    /** Session dropdown selection changed. */
    default void onSessionSelected(SessionItem item) {}
}
