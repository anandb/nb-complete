package github.anandb.netbeans.ui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards the status label's length cap. The label sits left of the model
 * dropdown and absorbs spare width, so an unbounded message would resize the row.
 */
class StatusControllerTest {

    /** Mirror of {@code StatusController.MAX_STATUS_LENGTH}. */
    private static final int MAX = 50;

    private static JLabel label() {
        return new JLabel();
    }

    private static StatusController controller(JLabel label) {
        return new StatusController(label, new JButton(), new JButton(),
                new PlaceholderTextArea(""), new JButton());
    }

    @Test
    void shortStatusTextIsUnchanged() {
        JLabel label = label();
        controller(label).setStatusText("Ready");
        assertEquals("Ready", label.getText());
    }

    @Test
    void textAtTheCapIsUnchanged() {
        JLabel label = label();
        String text = "a".repeat(MAX);
        controller(label).setStatusText(text);
        assertEquals(text, label.getText());
    }

    @Test
    void overLongTextIsTruncatedToTheCapWithEllipsis() {
        JLabel label = label();
        controller(label).setStatusText("a".repeat(MAX + 20));
        String shown = label.getText();
        assertEquals("a".repeat(MAX - 3) + "...", shown);
        assertEquals(MAX, shown.length(), "the shown text must never exceed the cap");
    }

    @Test
    void nullTextClearsTheLabel() {
        JLabel label = new JLabel("previous");
        controller(label).setStatusText(null);
        assertNull(label.getText());
    }

    @Test
    void sendingAnimationFramesAreAllTenCharacters() {
        // "Sending" is 7 chars; the cycle appends up to 3 dots, so every frame is
        // padded with spaces to 10 — the label width never changes while ticking.
        for (int dots = 0; dots <= 3; dots++) {
            String frame = StatusController.thinkingFrame("Sending", dots);
            assertEquals(10, frame.length(), "frame " + dots + " must stay 10 chars");
        }
        assertEquals("Sending   ", StatusController.thinkingFrame("Sending", 0));
        assertEquals("Sending.  ", StatusController.thinkingFrame("Sending", 1));
        assertEquals("Sending.. ", StatusController.thinkingFrame("Sending", 2));
        assertEquals("Sending...", StatusController.thinkingFrame("Sending", 3));
    }

    @Test
    void thinkingFramesKeepAConstantWidthForLongerDotsPhases() {
        int expected = "Thinking".length() + 3;
        for (int dots = 0; dots <= 3; dots++) {
            assertEquals(expected, StatusController.thinkingFrame("Thinking", dots).length());
        }
        // Only the dot phase changes; the base text is unchanged.
        assertEquals("Thinking", StatusController.thinkingFrame("Thinking", 3).replaceFirst("[ .]+$", ""));
    }

    @Test
    void thinkingFrameNeverExceedsTheStatusCap() {
        String frame = StatusController.thinkingFrame("a".repeat(MAX), 3);
        assertEquals(MAX, frame.length());
    }

    @Test
    void animationFramesRenderAtTheSameWidth() {
        int baseline = new JLabel(StatusController.thinkingFrame("Sending", 0)).getPreferredSize().width;
        for (int dots = 1; dots <= 3; dots++) {
            String frame = StatusController.thinkingFrame("Sending", dots);
            assertEquals(baseline, new JLabel(frame).getPreferredSize().width,
                    "frame " + dots + " must render at the same width as frame 0");
        }
    }

    @Test
    void startThinkingPadsTheSendingStatusImmediately() throws Exception {
        JLabel label = label();
        StatusController c = controller(label);
        String[] shown = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            c.setStatusText("Sending");
            c.startThinking();
            shown[0] = label.getText();
            c.stopThinking();
            c.stopAllTimers();
        });
        assertEquals("Sending   ", shown[0], "the first frame must already be the padded width");
    }
}
