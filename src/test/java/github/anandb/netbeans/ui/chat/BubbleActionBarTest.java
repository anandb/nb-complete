package github.anandb.netbeans.ui.chat;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Regression guard for the NPE in {@code showButtons()} when a bubble has no action
 * buttons: user, info and error bubbles install hover targets when their content is
 * rebuilt, and the delayed show timer then dereferenced a null copy button.
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
}
