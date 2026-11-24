package github.anandb.netbeans.ui.chat;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guards for the copy/pin overlay. Bubbles with no action buttons must
 * not arm the hover timer, and the overlay must paint above the content row and
 * stay inside the visible bubble after an id is assigned.
 */
class BubbleActionBarTest {

    @Test
    void showButtonsIsNoOpWithoutActionButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MessageBubble owner = new MessageBubble(MessageType.info, "note", null, null,
                    MessageBubble.AvatarPosition.NONE);
            BubbleActionBar bar = new BubbleActionBar(new JPanel(), "info", null, null,
                    new StringBuilder("note"), ThemeManager.getCurrentTheme(), owner);

            // The content rebuild that installs hover targets on a button-less bubble.
            bar.refreshHoverTargets();

            assertDoesNotThrow(bar::showButtons);
            bar.stopAllTimers();
        });
    }

    @Test
    void overlayStaysOnTopAndInsideTheBubble() throws Exception {
        String paragraph = ("long assistant answer that wraps at the thread width ").repeat(12);

        SwingUtilities.invokeAndWait(() -> {
            MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, paragraph,
                    null, null, MessageBubble.AvatarPosition.NONE, true);
            layOut(bubble);
            assertOverlayUsable(bubble);

            // Generated ids are assigned after streaming: the overlay is rebuilt then.
            bubble.setMessageId("m2");
            bubble.finalizeStreaming(true, true);
            layOut(bubble);
            assertOverlayUsable(bubble);
        });
    }

    private static void layOut(MessageBubble bubble) {
        JPanel host = new JPanel();
        host.setLayout(new BoxLayout(host, BoxLayout.Y_AXIS));
        host.add(bubble);
        host.setSize(500, 10000);
        host.doLayout();
        bubble.doLayout();
        host.setSize(500, Math.max(1, bubble.getPreferredSize().height));
        host.doLayout();
        bubble.doLayout();
        JPanel overlay = findOverlay(bubble);
        if (overlay != null) {
            // Headless runs may load no icons; give the buttons their production size
            // and let FlowLayout size the overlay from them.
            for (Component c : overlay.getComponents()) {
                if (c instanceof JButton button) {
                    button.setPreferredSize(new Dimension(24, 24));
                }
            }
            overlay.setPreferredSize(null);
            overlay.invalidate();
            bubble.doLayout();
        }
    }

    private static void assertOverlayUsable(MessageBubble bubble) {
        JPanel overlay = findOverlay(bubble);
        assertNotNull(overlay, "action overlay must exist");
        // Index 0 is the topmost child: the overlay must paint above the content row.
        assertEquals(0, indexOf(bubble, overlay), "overlay must be the first child");
        assertTrue(overlay.getWidth() >= 24 * overlay.getComponentCount(),
                "overlay must fit every button: " + overlay.getBounds());
        assertTrue(overlay.getX() >= 0 && overlay.getY() >= 0,
                "overlay must start inside the bubble: " + overlay.getBounds());
        assertTrue(overlay.getX() + overlay.getWidth() <= bubble.getWidth(),
                "overlay must not overflow the bubble width: " + overlay.getBounds());
        assertTrue(overlay.getY() + overlay.getHeight() <= bubble.getHeight(),
                "overlay must not overflow the bubble height: " + overlay.getBounds());

        // Hovering only shows the overlay (setVisible), without a revalidate; the
        // buttons must already have real bounds from the hidden layout pass.
        overlay.setVisible(true);
        for (Component c : overlay.getComponents()) {
            assertTrue(c.getWidth() > 0 && c.getHeight() > 0,
                    "button must be laid out before the overlay is shown: " + c.getBounds());
        }
        overlay.setVisible(false);
    }

    private static int indexOf(Container parent, Component child) {
        for (int i = 0; i < parent.getComponentCount(); i++) {
            if (parent.getComponent(i) == child) {
                return i;
            }
        }
        return -1;
    }

    private static JPanel findOverlay(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JPanel panel && panel.getComponentCount() > 0
                    && panel.getComponentCount() <= 2 && onlyButtons(panel)) {
                return panel;
            }
            if (c instanceof Container child) {
                JPanel found = findOverlay(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean onlyButtons(JPanel panel) {
        for (Component c : panel.getComponents()) {
            if (!(c instanceof JButton)) {
                return false;
            }
        }
        return true;
    }
}
