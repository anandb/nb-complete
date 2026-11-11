package github.anandb.netbeans.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JLayeredPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * Streaming auto-scroll follows the bottom until the user moves the view themselves. The
 * {@link ScrollController#setUserScrollListener(Runnable)} signal is what cancels that follow, so
 * every user gesture (wheel, scrollbar drag, paging, Home) must raise it while programmatic
 * scrolls must not — otherwise the panel would either stop following or fight the user.
 */
class ScrollControllerUserScrollTest {

    @Test
    void everyUserScrollGestureNotifiesTheListener() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        JScrollPane[] pane = new JScrollPane[1];
        ScrollController[] controller = new ScrollController[1];
        SwingUtilities.invokeAndWait(() -> {
            pane[0] = new JScrollPane(new javax.swing.JPanel());
            controller[0] = new ScrollController(pane[0], pane[0], new JLayeredPane());
            controller[0].setUserScrollListener(fired::incrementAndGet);
        });

        int before = fired.get();
        SwingUtilities.invokeAndWait(() -> pane[0].dispatchEvent(new MouseWheelEvent(pane[0],
                MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 10, 10, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1)));
        assertNotified(before, fired, "a wheel scroll must cancel following");

        before = fired.get();
        SwingUtilities.invokeAndWait(() -> {
            JScrollBar bar = pane[0].getVerticalScrollBar();
            bar.setValueIsAdjusting(true);
            bar.setValue(8);
            bar.setValueIsAdjusting(false);
        });
        assertNotified(before, fired, "dragging the scrollbar must cancel following");

        before = fired.get();
        SwingUtilities.invokeAndWait(() -> controller[0].scrollByBlock(false));
        assertNotified(before, fired, "paging must cancel following");

        before = fired.get();
        SwingUtilities.invokeAndWait(() -> controller[0].scrollToTop());
        assertNotified(before, fired, "jumping to the top must cancel following");
    }

    /** The signal only has to fire; a drag can emit an event per model change. */
    private static void assertNotified(int before, AtomicInteger fired, String message) {
        assertTrue(fired.get() > before, message);
    }

    @Test
    void programmaticScrollDoesNotNotifyTheListener() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        JScrollPane[] pane = new JScrollPane[1];
        ScrollController[] controller = new ScrollController[1];
        SwingUtilities.invokeAndWait(() -> {
            pane[0] = new JScrollPane(new javax.swing.JPanel());
            controller[0] = new ScrollController(pane[0], pane[0], new JLayeredPane());
            controller[0].setUserScrollListener(fired::incrementAndGet);
        });

        SwingUtilities.invokeAndWait(() -> controller[0].scrollToBottom(true));
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(0, fired.get(), "the auto-scroll pin must not cancel itself");
    }
}
