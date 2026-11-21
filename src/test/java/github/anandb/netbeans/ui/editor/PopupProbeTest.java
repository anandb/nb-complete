package github.anandb.netbeans.ui.editor;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The probe's fallback walk must stay inside the window the user right-clicked, and
 * the poll must give up after a few ticks. {@code JPopupMenu.isShowing()} needs a real
 * window, which the test JVM (headless) cannot build, so the tests inject a
 * {@code showing} predicate over visible component trees.
 */
class PopupProbeTest {

    private JPanel clickedWindow;
    private JPanel clickTarget;
    private JPopupMenu clickedPopup;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            clickedPopup = popup();
            clickTarget = new JPanel();
            clickedWindow = new JPanel();
            clickedWindow.add(clickTarget);
            clickedWindow.add(clickedPopup);
            clickedWindow.setVisible(true);
            clickTarget.setVisible(true);
        });
    }

    @Test
    void scopedWalkFindsTheClickedWindowsPopup() {
        PopupProbe probe = probe(always(true), new ArrayList<>());

        probe.eventDispatched(rightClick(clickTarget));

        assertSame(clickedWindow, PopupProbe.rootOf(clickTarget),
                "the click's topmost ancestor is the window to search");
        assertSame(clickedPopup, probe.findShownPopup(),
                "the scoped walk must find the clicked window's own popup");
    }

    @Test
    void unrelatedWindowIsNotScanned() throws Exception {
        JPopupMenu unrelatedPopup = popup();
        JPanel unrelatedWindow = windowWithPopup(unrelatedPopup);
        // The clicked window has no popup at all; a global scan would return the
        // unrelated window's popup instead.
        JPanel[] clickedHolder = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            clickedHolder[0] = new JPanel();
            clickedHolder[0].setVisible(true);
        });

        PopupProbe probe = new PopupProbe("Test", c -> true, p -> true,
                p -> { }, always(true));
        probe.eventDispatched(rightClick(clickedHolder[0]));

        assertNull(probe.findShownPopup(),
                "an unrelated window's popup must never satisfy the probe");

        // Clicking inside the unrelated window does find its popup: the scope follows
        // the click, it is not a global exclusion.
        probe.eventDispatched(rightClick(unrelatedWindow.getComponent(0)));
        assertSame(unrelatedPopup, probe.findShownPopup(),
                "the popup of the window that was actually clicked is found");
    }

    @Test
    void pollGivesUpAfterTheTickCap() {
        AtomicInteger scans = new AtomicInteger();
        PopupProbe probe = probe(p -> {
            scans.incrementAndGet();
            return false;
        }, new ArrayList<>());
        probe.eventDispatched(rightClick(clickTarget));

        boolean stopped = false;
        for (int i = 0; i < PopupProbe.PROBE_MAX_TICKS; i++) {
            stopped = probe.probeTick();
            assertTrue(!stopped, "tick " + (i + 1) + " gave up before the cap");
        }
        stopped = probe.probeTick();

        assertTrue(stopped, "the poll must stop at the tick cap");
        int scansAtStop = scans.get();
        probe.probeTick();
        assertEquals(scansAtStop, scans.get(),
                "a stopped poll must not keep walking the window hierarchy");
    }

    @Test
    void foundPopupIsInjectedExactlyOnce() {
        List<JPopupMenu> injected = new ArrayList<>();
        PopupProbe probe = probe(always(true), injected);
        probe.eventDispatched(rightClick(clickTarget));

        assertTrue(probe.probeTick(), "finding the popup ends the poll");
        probe.probeTick();

        assertEquals(List.of(clickedPopup), injected, "the popup must be injected exactly once");
    }

    @Test
    void retryCapStaysSmall() {
        assertTrue(PopupProbe.PROBE_MAX_TICKS <= 10,
                "the poll cap guards against scanning every window during a failed right-click");
    }

    private PopupProbe probe(java.util.function.Predicate<JPopupMenu> showing, List<JPopupMenu> injected) {
        return new PopupProbe("Test", c -> true, p -> true, injected::add, showing);
    }

    private static java.util.function.Predicate<JPopupMenu> always(boolean value) {
        return p -> value;
    }

    private static JPopupMenu popup() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem("Copy Message"));
        return menu;
    }

    private static JPanel windowWithPopup(JPopupMenu menu) throws Exception {
        JPanel[] holder = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new JPanel();
            holder[0].add(menu);
            holder[0].setVisible(true);
        });
        return holder[0];
    }

    private static MouseEvent rightClick(Component source) {
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                1, 1, 1, true, MouseEvent.BUTTON3);
    }
}
