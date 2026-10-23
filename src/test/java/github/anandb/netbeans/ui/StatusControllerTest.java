package github.anandb.netbeans.ui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final ContextUsageMeter[] meterHolder = new ContextUsageMeter[1];

    private static ContextUsageMeter meter() {
        if (meterHolder[0] == null) {
            meterHolder[0] = new ContextUsageMeter();
        }
        return meterHolder[0];
    }

    private static StatusController controller(JLabel label) {
        return new StatusController(label, new JButton(), new JButton(),
                new PlaceholderTextArea(""), new JButton(),
                meter());
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
        int width = "Sending".length() + 3;
        for (int dots = 0; dots <= 3; dots++) {
            String frame = StatusController.thinkingFrame("Sending", dots);
            assertEquals(width, frame.length(), "frame " + dots + " must stay " + width + " chars");
            assertTrue(frame.startsWith("Sending"), "frame " + dots + " must keep the message text");
        }
    }

    @Test
    void thinkingFramesKeepAConstantWidthForLongerDotsPhases() {
        int expected = "Thinking".length() + 3;
        for (int dots = 0; dots <= 3; dots++) {
            assertEquals(expected, StatusController.thinkingFrame("Thinking", dots).length());
        }
        // Only the dot phase changes; the base text is unchanged.
        assertTrue(StatusController.thinkingFrame("Thinking", 3).startsWith("Thinking"));
    }

    @Test
    void thinkingFrameNeverExceedsTheStatusCap() {
        String frame = StatusController.thinkingFrame("a".repeat(MAX), 3);
        assertEquals(MAX, frame.length());
    }

    @Test
    void animationFramesRenderAtTheSameWidth() {
        int baseline = StatusController.thinkingFrame("Sending", 0).length();
        for (int dots = 1; dots <= 3; dots++) {
            assertEquals(baseline, StatusController.thinkingFrame("Sending", dots).length(),
                    "frame " + dots + " must match frame 0's width");
        }
    }

    @Test
    void overLongBaseIsNotShortenedFromFrameToFrame() {
        // The base is stored by the controller, not recovered from the label, so a
        // long message must not lose a character per tick to the ellipsis. The stem
        // is capped once to (MAX - 3) — room for the dots — and keeps that length.
        String base = "a".repeat(MAX);
        String stem = "a".repeat(MAX - 6) + "...";
        for (int dots = 0; dots <= 3; dots++) {
            String frame = StatusController.thinkingFrame(base, dots);
            assertEquals(MAX, frame.length(), "frame " + dots + " must keep the full width");
            assertTrue(frame.startsWith(stem), "frame " + dots + " must keep the same visible message");
        }
    }

    @Test
    void startThinkingOnAnOverLongStatusDoesNotErodeIt() throws Exception {
        JLabel label = label();
        StatusController c = controller(label);
        String[] shown = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            c.setStatusText("a".repeat(MAX));
            c.startThinking();
            shown[0] = label.getText();
            c.stopThinking();
            c.stopAllTimers();
        });
        String stem = "a".repeat(MAX - 6) + "...";
        assertEquals(MAX, shown[0].length());
        assertTrue(shown[0].startsWith(stem));
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
        assertEquals("Sending".length() + 3, shown[0].length(),
                "the first frame must already be the padded width");
        assertTrue(shown[0].startsWith("Sending"), "the first frame must keep the message text");
    }

    @Test
    void contextUsageForwardsToMeter() {
        StatusController c = controller(label());
        c.setContextUsage(100, 200);
        assertEquals(0.5, meter().getFraction(), 1e-9);
    }

    @Test
    void clearContextUsageForwardsToMeter() {
        StatusController c = controller(label());
        c.setContextUsage(100, 200);
        c.clearContextUsage();
        assertEquals(-1.0, meter().getFraction(), 1e-9);
    }

    @Test
    void statusLabelTooltipIsNotUsedForContextUsage() {
        // The tooltip moved to the meter; setContextUsage must not touch the
        // status label tooltip.
        JLabel label = label();
        StatusController c = controller(label);
        c.setContextUsage(100, 200);
        assertEquals(null, label.getToolTipText());
    }
}
