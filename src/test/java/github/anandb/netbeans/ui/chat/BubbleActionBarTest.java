package github.anandb.netbeans.ui.chat;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.nio.file.Path;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void overlayStaysInsideTheContentRowWhenQueued() throws Exception {
        String paragraph = ("assistant answer that wraps at the thread width ").repeat(8);
        SwingUtilities.invokeAndWait(() -> {
            MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, paragraph,
                    "m3", null, MessageBubble.AvatarPosition.NONE);
            bubble.setQueued(true);
            try {
                layOut(bubble);
                assertInsideRow(bubble);
            } finally {
                bubble.setQueued(false);
            }
        });
    }

    @Test
    void overlayClearsATrailingCollapsedCodeCopy() throws Exception {
        String markdown = "Some text above.\n\n```java\nint x = 1;\n```";
        SwingUtilities.invokeAndWait(() -> {
            MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, markdown,
                    "m4", null, MessageBubble.AvatarPosition.NONE);
            CollapsibleCodePane pane = findCodePane(bubble);
            assertNotNull(pane, "expected a code pane at the end of the message");
            pane.setExpanded(false);
            giveCodeCopyRealSize(pane);
            layOut(bubble);

            JPanel overlay = findOverlay(bubble);
            assertNotNull(overlay);
            Rectangle overlayBounds = boundsIn(overlay, bubble);
            Rectangle copyBounds = boundsIn(pane.copyButton, bubble);
            assertTrue(copyBounds.width > 0, "code copy must be laid out: " + copyBounds);
            assertFalse(overlayBounds.intersects(copyBounds),
                    "overlay must not cover the code copy button: overlay=" + overlayBounds
                            + " copy=" + copyBounds);
        });
    }

    @Test
    void overlayClearsTheCopyInACodeOnlyBubble() throws Exception {
        String markdown = "```java\nint x = 1;\n```";
        SwingUtilities.invokeAndWait(() -> {
            MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, markdown,
                    "m5", null, MessageBubble.AvatarPosition.NONE);
            CollapsibleCodePane pane = findCodePane(bubble);
            assertNotNull(pane, "expected a code pane");
            pane.setExpanded(false);
            giveCodeCopyRealSize(pane);
            layOut(bubble);

            JPanel overlay = findOverlay(bubble);
            assertNotNull(overlay);
            Rectangle overlayBounds = boundsIn(overlay, bubble);
            Rectangle copyBounds = boundsIn(pane.copyButton, bubble);
            assertTrue(copyBounds.width > 0, "code copy must be laid out: " + copyBounds);
            assertFalse(overlayBounds.intersects(copyBounds),
                    "overlay must not cover the code copy button: overlay=" + overlayBounds
                            + " copy=" + copyBounds);
            assertInsideRow(bubble);
        });
    }

    @TempDir
    Path tempDir;

    @Test
    void saveButtonIsFirstAndDisabledWithoutDirectory() throws Exception {
        String previous = PluginSettings.getMessageSaveDirectory();
        try {
            SwingUtilities.invokeAndWait(() -> {
                PluginSettings.setMessageSaveDirectory("");
                MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, "answer",
                        "m6", null, MessageBubble.AvatarPosition.NONE);
                JPanel overlay = findOverlay(bubble);
                assertNotNull(overlay, "action overlay must exist");
                assertEquals(3, overlay.getComponentCount(), "save, copy and pin must all be present");
                Component first = overlay.getComponent(0);
                assertTrue(first instanceof JButton, "save is the first button");
                JButton save = (JButton) first;
                assertFalse(save.isEnabled(), "save is disabled until a directory is configured");
                assertTrue(save.getToolTipText().contains("Settings -> Core Service -> Assistant"),
                        "disabled tooltip explains where to configure it: " + save.getToolTipText());
            });
        } finally {
            PluginSettings.setMessageSaveDirectory(previous);
        }
    }

    @Test
    void saveButtonEnablesWhenDirectoryIsConfigured() throws Exception {
        String previous = PluginSettings.getMessageSaveDirectory();
        try {
            SwingUtilities.invokeAndWait(() -> {
                PluginSettings.setMessageSaveDirectory(tempDir.toString());
                MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, "answer",
                        "m7", null, MessageBubble.AvatarPosition.NONE);
                JPanel overlay = findOverlay(bubble);
                assertNotNull(overlay, "action overlay must exist");
                JButton save = (JButton) overlay.getComponent(0);
                assertTrue(save.isEnabled(), "save enables once a directory is configured");
                assertEquals("Save Content", save.getToolTipText());
            });
        } finally {
            PluginSettings.setMessageSaveDirectory(previous);
        }
    }

    private static void assertInsideRow(MessageBubble bubble) {
        JPanel overlay = findOverlay(bubble);
        assertNotNull(overlay, "action overlay must exist");
        MessageBubble.AlignedRow row = findRow(bubble);
        assertNotNull(row, "content row must exist");
        Rectangle rowBounds = boundsIn(row, bubble);
        Rectangle overlayBounds = boundsIn(overlay, bubble);
        assertTrue(rowBounds.contains(overlayBounds),
                "overlay must stay inside the bubble row: overlay=" + overlayBounds
                        + " row=" + rowBounds);
    }

    /** Headless runs may load no icons; give the code copy button its production size. */
    private static void giveCodeCopyRealSize(CollapsibleCodePane pane) {
        Dimension size = new Dimension(28, 28);
        pane.copyButton.setPreferredSize(size);
        if (pane.copyButton.getParent() != null) {
            pane.copyButton.getParent().setPreferredSize(size);
        }
    }

    private static Rectangle boundsIn(Component component, Container target) {
        return SwingUtilities.convertRectangle(
                component.getParent(), component.getBounds(), target);
    }

    private static MessageBubble.AlignedRow findRow(MessageBubble bubble) {
        for (Component c : bubble.getComponents()) {
            if (c instanceof MessageBubble.AlignedRow row) {
                return row;
            }
        }
        return null;
    }

    private static CollapsibleCodePane findCodePane(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof CollapsibleCodePane pane) {
                return pane;
            }
            if (c instanceof Container child) {
                CollapsibleCodePane found = findCodePane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void layOut(MessageBubble bubble) {
        JPanel host = new JPanel();
        host.setLayout(new BoxLayout(host, BoxLayout.Y_AXIS));
        host.add(bubble);
        host.setSize(500, 10000);
        host.doLayout();
        layoutTree(bubble);
        host.setSize(500, Math.max(1, bubble.getPreferredSize().height));
        host.doLayout();
        layoutTree(bubble);
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
        }
        layoutTree(bubble);
        bubble.doLayout();
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layoutTree(nested);
            }
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

    /** The overlay is a direct child of the bubble; panes also hold button-only placeholders. */
    private static JPanel findOverlay(MessageBubble bubble) {
        for (Component c : bubble.getComponents()) {
            if (c instanceof JPanel panel && panel.getComponentCount() > 0
                    && panel.getComponentCount() <= 3 && onlyButtons(panel)) {
                return panel;
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
