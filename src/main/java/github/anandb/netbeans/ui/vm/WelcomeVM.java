package github.anandb.netbeans.ui.vm;

import java.util.List;

import github.anandb.netbeans.model.Session;

/**
 * Swing-free view-model for the welcome/empty-state screen (session list
 * shown when no conversation is active). Mirrors the data passed to
 * {@code WelcomeScreen.show(...)}.
 * <p>
 * The session list is supplied already-filtered by the caller (hidden-session
 * filtering is a {@code SessionControl.isHidden(...)} query, which belongs to
 * the future {@code ui/PlatformBridge} {@code SessionService} — not to a
 * Swing-free VM). {@code showHidden} here is the user's toggle preference,
 * carried so the DSL can render the toggle state; it does not drive filtering
 * inside the VM.
 * <p>
 * <b>DSL-ready:</b> immutable record + withers, Swing-free.
 */
public record WelcomeVM(
        List<Session> sessions,
        boolean showHidden
) {
    public static WelcomeVM empty() {
        return new WelcomeVM(List.of(), false);
    }

    public WelcomeVM withSessions(List<Session> v) { return new WelcomeVM(v, showHidden); }
    public WelcomeVM withShowHidden(boolean v) { return new WelcomeVM(sessions, v); }
}
