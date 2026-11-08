package github.anandb.netbeans.ui.chat;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import github.anandb.netbeans.ui.TestUiUtils;

/**
 * setSessionList must leave the WelcomeScreen on the panel. clearMessages() used to queue
 * its container wipe via invokeLater, so the wipe ran after WelcomeScreen.show() and erased
 * the welcome view one tick later (a brief flash, then a blank thread).
 */
class ChatThreadPanelWelcomeScreenTest {

    private ChatThreadPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> panel = new ChatThreadPanel());
    }

    @Test
    void welcomeScreenSurvivesTheQueuedClear() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.setSessionList(List.of(), id -> { }, () -> { }));
        // Flush everything the call queued, including any deferred clearMessages() wipe.
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertTrue(panel.getMessagesContainer().getComponentCount() > 0,
                "the WelcomeScreen must still be on the panel after the EDT drains");
    }
}
