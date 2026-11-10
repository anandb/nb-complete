package github.anandb.netbeans.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.ui.TestUiUtils;

/**
 * The harness chooser replaces the thread with its own bubble, so dismissing it must put the
 * same components back. It used to reload the session instead, and a reload of the already-loaded
 * session merely resumes it — no history is replayed, so the chooser stayed on screen.
 */
class ChatThreadPanelOnboardingRestoreTest {

    private ChatThreadPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> panel = new ChatThreadPanel());
    }

    @Test
    void dismissingTheChooserPutsTheSameThreadBack() throws Exception {
        JLabel chatContent = new JLabel("chat");
        JLabel chooser = new JLabel("chooser");

        SwingUtilities.invokeAndWait(() -> {
            panel.getMessagesContainer().add(chatContent);
            panel.stashThreadForOnboarding();
            panel.getMessagesContainer().removeAll();
            panel.getMessagesContainer().add(chooser);
            panel.restoreThreadAfterOnboarding();
        });
        SwingUtilities.invokeAndWait(() -> { });

        Component[] content = panel.getMessagesContainer().getComponents();
        assertEquals(1, content.length, "the chooser bubble must be gone");
        assertSame(chatContent, content[0], "the previous thread must be back, not rebuilt");
    }

    @Test
    void reShowingTheChooserKeepsTheOriginalThread() throws Exception {
        JLabel chatContent = new JLabel("chat");

        SwingUtilities.invokeAndWait(() -> panel.getMessagesContainer().add(chatContent));
        // Selecting a harness rebuilds the bubble while it is on screen; the
        // stash must keep the thread, not capture the chooser bubble over it.
        SwingUtilities.invokeAndWait(() -> panel.addOnboardingBubble(List.of(), (id, path) -> { }, () -> { }));
        SwingUtilities.invokeAndWait(() -> panel.addOnboardingBubble(List.of(), (id, path) -> { }, () -> { }));
        SwingUtilities.invokeAndWait(panel::restoreThreadAfterOnboarding);
        SwingUtilities.invokeAndWait(() -> { });

        Component[] content = panel.getMessagesContainer().getComponents();
        assertEquals(1, content.length);
        assertSame(chatContent, content[0]);
    }

    @Test
    void restoringWithoutAStashStillRemovesTheChooser() throws Exception {
        JLabel chooser = new JLabel("chooser");

        SwingUtilities.invokeAndWait(() -> {
            panel.getMessagesContainer().add(chooser);
            panel.restoreThreadAfterOnboarding();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(0, panel.getMessagesContainer().getComponentCount());
    }
}
