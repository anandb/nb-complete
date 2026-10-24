package github.anandb.netbeans.ui;

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
    /** Ticks after a click before polling gives up (no popup opened, e.g. plain right-click). */
    private static final int PROBE_MAX_TICKS = 25;

    private final String label;
    private final Predicate<Component> scope;
    private final Predicate<JPopupMenu> accepts;
    private final Consumer<JPopupMenu> injection;

    /** Poll timer: right-click restarts it; it injects as soon as the popup shows. */
    private final Timer probe;

    /** Ticks since the last popup-trigger click; caps the poll loop. */
    private int probeTicks;

    /**
     * @param label  window name for fine logging, e.g. {@code "Show Changes"}
     * @param scope  whether a clicked component belongs to the hook's window
     * @param accepts whether a shown popup is the one to inject into
     * @param injection adds the item; runs on the EDT with the popup on screen
     */
    PopupProbe(String label, Predicate<Component> scope, Predicate<JPopupMenu> accepts,
            Consumer<JPopupMenu> injection) {
        this.label = label;
        this.scope = scope;
        this.accepts = accepts;
        this.injection = injection;
        this.probe = new Timer(PROBE_DELAY_MS, e -> injectIntoShownPopup());
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
            probe.restart();
        }
    }

    /** Runs on the EDT via the poll timer. */
    private void injectIntoShownPopup() {
        if (++probeTicks > PROBE_MAX_TICKS) {
            probe.stop(); // no qualifying popup opened; stop polling
            return;
        }
        JPopupMenu popup = findShownPopup();
        if (popup == null) {
            LOG.fine("probe: no {0} popup on screen yet", label); // NOI18N
            return;
        }
        probe.stop();
        LOG.fine("probe: injecting into the {0} popup", label); // NOI18N
        injection.accept(popup);
    }

    /**
     * Finds the on-screen popup of the hook's window at probe time.
     * Primary source is the {@link MenuSelectionManager} selection path — which
     * only lists the popup after one of its items has been armed, so if the
     * path is empty (no hover/selection yet) or the popup is not there, every
     * open top-level window hierarchy is walked for a showing JPopupMenu
     * instead (lightweight popups live inside one of the open windows,
     * heavyweight ones inside a Popup window).
     */
    private JPopupMenu findShownPopup() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        for (MenuElement el : path) {
            if (el instanceof JPopupMenu p && accepts.test(p)) {
                return p;
            }
        }
        for (Window w : Window.getWindows()) {
            JPopupMenu popup = findShowingPopup(w);
            if (popup != null && accepts.test(popup)) {
                return popup;
            }
        }
        return null;
    }

    /** @return the topmost showing JPopupMenu in the given component hierarchy, if any. */
    private static JPopupMenu findShowingPopup(Component c) {
        if (c instanceof JPopupMenu p && p.isShowing()) {
            return p;
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                JPopupMenu found = findShowingPopup(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
