package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that consecutive same-allowed permission results merge into a
 * single bubble while a reject (or a different result) starts a new one.
 */
class PermissionResultMergeTest {

    private ChatThreadPanel panel;
    private JPanel messagesContainer;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> {
            panel = new ChatThreadPanel();
            messagesContainer = (JPanel) privateField(panel, "messagesContainer");
        });
    }

    @Test
    void consecutiveAllowedMerge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.addPermissionResult("Permission Granted", true);
            panel.addPermissionResult("Permission Granted", true);
            panel.addPermissionResult("Permission Granted", true);
        });
        flushEDT();
        assertEquals(1, permissionGroupCount(), "three consecutive approved results must merge into one bubble");
        JLabel lbl = findLabelByText("Permission");
        assertTrue(lbl.getText().contains("\u00D73"), "merged label should show ×3 but was: " + lbl.getText());
        assertTrue(lbl.getText().contains("\u00D73"), "merged label should show ×3 but was: " + lbl.getText());
    }

    @Test
    void rejectBreaksMerge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.addPermissionResult("Permission Granted", true);
            panel.addPermissionResult("Permission Denied", false);
            panel.addPermissionResult("Permission Granted", true);
        });
        flushEDT();
        assertEquals(3, permissionGroupCount(), "allow+reject+allow must produce three separate bubbles");
    }

    @Test
    void allowedAfterRejectStartsNewGroup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.addPermissionResult("Permission Denied", false);
            panel.addPermissionResult("Permission Granted", true);
            panel.addPermissionResult("Permission Granted", true);
        });
        flushEDT();
        assertEquals(2, permissionGroupCount(), "deny then two allows must produce two bubbles (deny + merged allow)");
    }

    @Test
    void hiddenToolMessageDoesNotBreakMerge() throws Exception {
        setFilterTool(true);
        try {
            // Two allows, then a HIDDEN tool message, then another allow: the
            // invisible message must not break the merge of consecutive Allowed results.
            SwingUtilities.invokeAndWait(() -> {
                panel.addPermissionResult("Permission Granted", true);
                panel.addPermissionResult("Permission Granted", true);
            });
            SwingUtilities.invokeAndWait(() -> addToolMessage("tool-1"));
            SwingUtilities.invokeAndWait(() -> panel.addPermissionResult("Permission Granted", true));
            flushEDT();
            assertEquals(1, permissionGroupCount(), "hidden tool message between allows must not split the group");
            JLabel lbl = findLabelByText("Permission");
            assertTrue(lbl.getText().contains("\u00D73"), "merged label should show ×3 but was: " + lbl.getText());
        } finally {
            setFilterTool(false);
        }
    }

    @Test
    void visibleToolMessageBreaksMerge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.addPermissionResult("Permission Granted", true);
            panel.addPermissionResult("Permission Granted", true);
        });
        SwingUtilities.invokeAndWait(() -> addToolMessage("tool-2"));
        SwingUtilities.invokeAndWait(() -> panel.addPermissionResult("Permission Granted", true));
        flushEDT();
        assertEquals(2, permissionGroupCount(), "a visible tool message between allows must split the group");
    }

    private static void setFilterTool(boolean hidden) throws Exception {
        java.lang.reflect.Field f = MessageFilterManager.class.getDeclaredField("filterTool");
        f.setAccessible(true);
        f.setBoolean(null, hidden);
    }

    private void addToolMessage(String id) {
        ProcessedMessage pm =
                new ProcessedMessage.Builder()
                        .messageType(MessageType.tool_call)
                        .text("running some tool")
                        .messageId(id)
                        .kind("tool")
                        .toolTitle("some-tool")
                        .rawText("running some tool")
                        .build();
        panel.addMessage(pm);
    }

    /** Runs on EDT after all pending invokeLater events (permission adds, message drain). */
    private void flushEDT() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private int permissionGroupCount() throws Exception {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<Integer> count = new AtomicReference<>(0);
            searchLabel(messagesContainer, "Permission", l -> count.set(count.get() + 1));
            n.set(count.get());
        });
        return n.get();
    }

    /** Recursively visits every JLabel whose text contains {@code prefix}. */
    private void searchLabel(Component c, String prefix, java.util.function.Consumer<JLabel> visitor) {
        if (c instanceof JLabel l && l.getText() != null && l.getText().contains(prefix)) {
            visitor.accept(l);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                searchLabel(child, prefix, visitor);
            }
        }
    }

    private JLabel findLabelByText(String prefix) {
        AtomicReference<JLabel> found = new AtomicReference<>();
        searchLabel(messagesContainer, prefix, l -> {
            if (found.get() == null) {
                found.set(l);
            }
        });
        return found.get();
    }

    private static Object privateField(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}