package github.anandb.netbeans.ui.editor;

import github.anandb.netbeans.support.Logger;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Shared runtime-hook machinery for adding an item to a popup that an IDE
 * module builds itself. Several IDE popups have no layer/SPI extension point,
 * so the hook watches for popup-trigger clicks inside its window, then polls
 * briefly for the popup to appear on screen and lets the caller inject into it.
 *
 * <p>Usage: one instance per hook, constructed with a scope predicate (is this
 * click inside my window?), an acceptance predicate (is this popup mine?) and
 * an injector. {@link #start()} must run on the EDT; the caller must keep a
 * strong reference to the instance, because the toolkit holds AWT listeners
 * weakly.</p>
 */
final class PopupProbe implements AWTEventListener {

    private static final Logger LOG = Logger.from(PopupProbe.class);

    /** Poll interval; the popup is typically up after the very first tick. */
    private static final int PROBE_DELAY_MS = 40;
    /**
     * Ticks after a click before polling gives up (no popup opened, e.g. a right-click
     * on empty space). 8 ticks = 320 ms: the popup is normally on screen within 40–80 ms,
     * and each tick walks a window hierarchy, so a longer loop only burns EDT.
     */
    static final int PROBE_MAX_TICKS = 8;

    private final String label;
    private final Predicate<Component> scope;
    private final Predicate<JPopupMenu> accepts;
    private final Consumer<JPopupMenu> injection;
    /** Whether a popup counts as on screen; a seam because {@code isShowing()} needs a window. */
    private final Predicate<JPopupMenu> showing;

    /** Poll timer: right-click restarts it; it injects as soon as the popup shows. */
    private final Timer probe;

    /** Ticks since the last popup-trigger click; caps the poll loop. */
    private int probeTicks;

    /** True between a popup-trigger click and the end of its poll. */
    private boolean active;

    /**
     * Topmost container of the popup-trigger click's component — the window the popup
     * belongs to. Restricting the fallback search to it keeps the poll from walking every
     * open IDE window's hierarchy. Cleared when the poll ends so it retains no window.
     */
    private Container probeRoot;

    /**
     * @param label  window name for fine logging, e.g. {@code "Show Changes"}
     * @param scope  whether a clicked component belongs to the hook's window
     * @param accepts whether a shown popup is the one to inject into
     * @param injection adds the item; runs on the EDT with the popup on screen
     */
    PopupProbe(String label, Predicate<Component> scope, Predicate<JPopupMenu> accepts,
            Consumer<JPopupMenu> injection) {
        this(label, scope, accepts, injection, JPopupMenu::isShowing);
    }

    /** Test seam for {@link #showing}; production uses {@code JPopupMenu.isShowing()}. */
    PopupProbe(String label, Predicate<Component> scope, Predicate<JPopupMenu> accepts,
            Consumer<JPopupMenu> injection, Predicate<JPopupMenu> showing) {
        this.label = label;
        this.scope = scope;
        this.accepts = accepts;
        this.injection = injection;
        this.showing = showing;
        this.probe = new Timer(PROBE_DELAY_MS, e -> probeTick());
    }

    /** Registers the AWT listener; must run on the EDT. */
    void start() {
        Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        // Right-button press or release (popup trigger varies by platform/LAF).
        // Coalesce: the timer is simply restarted, so an entire click produces one probe.
        // Scope the click to the hook's window first: every right-click in the IDE
        // reaches this listener, and an unscoped one would start a poll that walks
        // every open window hierarchy for up to PROBE_MAX_TICKS before giving up.
        if (event instanceof MouseEvent mouseEvent && (mouseEvent.isPopupTrigger()
                || SwingUtilities.isRightMouseButton(mouseEvent))
                && scope.test(mouseEvent.getComponent())) {
            probeTicks = 0;
            active = true;
            probeRoot = rootOf(mouseEvent.getComponent());
            probe.restart();
        }
    }

    /**
     * One poll tick, on the EDT. Returns true when this tick ended the poll (popup
     * injected, or the tick cap reached) so a stray timer firing can be ignored.
     */
    boolean probeTick() {
        if (!active) {
            return true;
        }
        if (++probeTicks > PROBE_MAX_TICKS) {
            stopPoll(); // no qualifying popup opened; stop polling
            return true;
        }
        JPopupMenu popup = findShownPopup();
        if (popup == null) {
            LOG.fine("probe: no {0} popup on screen yet", label); // NOI18N
            return false;
        }
        stopPoll();
        LOG.fine("probe: injecting into the {0} popup", label); // NOI18N
        injection.accept(popup);
        return true;
    }

    /** Ends the poll and releases the remembered click window. */
    private void stopPoll() {
        probe.stop();
        active = false;
        probeRoot = null;
    }

    /**
     * Finds the on-screen popup of the hook's window at probe time.
     * Primary source is the {@link MenuSelectionManager} selection path — which
     * only lists the popup after one of its items has been armed, so if the path
     * is empty (no hover/selection yet) or the popup is not there, the window of
     * the click that started the probe is walked instead: a lightweight popup
     * lives in that window's layered pane, a heavyweight one in a window it owns.
     * Only without a recorded click (probe already running) does it fall back to
     * every open window, which is O(whole IDE component tree) per tick.
     */
    JPopupMenu findShownPopup() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        for (MenuElement el : path) {
            if (el instanceof JPopupMenu p && accepts.test(p)) {
                return p;
            }
        }
        Container root = probeRoot;
        if (root == null) {
            // No click recorded (probe already running): fall back to every window.
            for (Window w : Window.getWindows()) {
                JPopupMenu found = findShowingPopup(w, showing);
                if (found != null && accepts.test(found)) {
                    return found;
                }
            }
            return null;
        }
        // The click's own window: lightweight popups live in its layered pane, so the
        // walk never has to touch unrelated (and much larger) IDE windows.
        JPopupMenu found = findShowingPopup(root, showing);
        if (found != null && accepts.test(found)) {
            return found;
        }
        // A heavyweight popup is a separate window owned by the invoker's window.
        if (root instanceof Window window) {
            for (Window owned : window.getOwnedWindows()) {
                found = findShowingPopup(owned, showing);
                if (found != null && accepts.test(found)) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Topmost ancestor of {@code c}; null when the component is detached. */
    static Container rootOf(Component c) {
        Component top = c;
        while (top != null && top.getParent() != null) {
            top = top.getParent();
        }
        return top instanceof Container container ? container : null;
    }

    /** @return the topmost showing JPopupMenu in the given component hierarchy, if any. */
    static JPopupMenu findShowingPopup(Component c, Predicate<JPopupMenu> showing) {
        if (c instanceof JPopupMenu p && showing.test(p)) {
            return p;
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                JPopupMenu found = findShowingPopup(child, showing);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
