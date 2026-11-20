package github.anandb.netbeans.ui.session;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import github.anandb.netbeans.ui.composer.PlaceholderTextArea;
import github.anandb.netbeans.ui.stats.ContextUsageMeter;

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
    void scheduleResetHonoursACustomDelayThenReturnsToTheDefault() {
        StatusController c = controller(label());
        c.scheduleReset(2000);
        assertTrue(c.getStatusResetTimer().isRunning());
        assertEquals(2000, c.getStatusResetTimer().getInitialDelay());
        assertEquals(2000, c.getStatusResetTimer().getDelay());
        c.scheduleReset();
        assertEquals(1500, c.getStatusResetTimer().getInitialDelay(),
                "later transient statuses keep the default lifetime");
        assertEquals(1500, c.getStatusResetTimer().getDelay());
    }

    @Test
    void sendingAnimationFramesKeepAConstantLength() {
        // "Sending" plus a space and one spinner glyph: the text width never
        // changes while ticking, so the row does not shift.
        int width = "Sending".length() + 2;
        for (int phase = 0; phase < 3; phase++) {
            String frame = StatusController.thinkingFrame("Sending", phase);
            assertEquals(width, frame.length(), "frame " + phase + " must stay " + width + " chars");
            assertTrue(frame.startsWith("Sending "), "frame " + phase + " must keep the message text");
        }
    }

    @Test
    void thinkingFramesKeepAConstantLengthForEveryPhase() {
        int expected = "Thinking".length() + 2;
        for (int phase = 0; phase < 3; phase++) {
            assertEquals(expected, StatusController.thinkingFrame("Thinking", phase).length());
        }
        // Only the spinner glyph changes; the base text is unchanged.
        assertTrue(StatusController.thinkingFrame("Thinking", 2).startsWith("Thinking "));
    }

    @Test
    void thinkingFrameNeverExceedsTheStatusCap() {
        String frame = StatusController.thinkingFrame("a".repeat(MAX), 2);
        assertEquals(MAX, frame.length());
    }

    @Test
    void animationFramesRenderAtTheSameLength() {
        int baseline = StatusController.thinkingFrame("Sending", 0).length();
        for (int phase = 1; phase < 3; phase++) {
            assertEquals(baseline, StatusController.thinkingFrame("Sending", phase).length(),
                    "frame " + phase + " must match frame 0's width");
        }
    }

    @Test
    void overLongBaseIsNotShortenedFromFrameToFrame() {
        // The base is stored by the controller, not recovered from the label, so a
        // long message must not lose a character per tick to the ellipsis. The stem
        // is capped once to (MAX - 2) — room for the spinner — and keeps that length.
        String base = "a".repeat(MAX);
        String stem = "a".repeat(MAX - 5) + "...";
        for (int phase = 0; phase < 3; phase++) {
            String frame = StatusController.thinkingFrame(base, phase);
            assertEquals(MAX, frame.length(), "frame " + phase + " must keep the full width");
            assertTrue(frame.startsWith(stem), "frame " + phase + " must keep the same visible message");
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
        String stem = "a".repeat(MAX - 5) + "...";
        assertEquals(MAX, shown[0].length());
        assertTrue(shown[0].startsWith(stem));
    }

    @Test
    void startThinkingPinsTheWidestSpinnerFrameWidth() throws Exception {
        JLabel label = label();
        StatusController c = controller(label);
        int[] pinned = new int[1];
        int[] expected = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            c.setStatusText("Sending");
            c.startThinking();
            pinned[0] = label.getPreferredSize().width;
            java.awt.FontMetrics fm = label.getFontMetrics(label.getFont());
            for (int phase = 0; phase < 3; phase++) {
                expected[0] = Math.max(expected[0],
                        fm.stringWidth(StatusController.thinkingFrame("Sending", phase)));
            }
            c.stopThinking();
            c.stopAllTimers();
        });
        assertEquals(expected[0], pinned[0],
                "the label reserves the widest spinner glyph, so no tick can resize the row");
    }

    @Test
    void startThinkingShowsTheFirstSpinnerFrame() throws Exception {
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
        assertEquals("Sending -", shown[0], "the first frame carries the hyphen spinner");
    }

    @Test
    void stopThinkingAppendsAPeriod() throws Exception {
        JLabel label = label();
        StatusController c = controller(label);
        String[] shown = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            c.setStatusText("Sending");
            c.startThinking();
            c.stopThinking();
            shown[0] = label.getText();
            c.stopAllTimers();
        });
        assertEquals("Sending.", shown[0], "the finished status ends with a period");
    }

    @Test
    void stopThinkingDoesNotDoubleATerminalPeriod() throws Exception {
        JLabel label = label();
        StatusController c = controller(label);
        String[] shown = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            c.setStatusText("Done.");
            c.startThinking();
            c.stopThinking();
            shown[0] = label.getText();
            c.stopAllTimers();
        });
        assertEquals("Done.", shown[0]);
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
    void configGateHoldsGoAndComposerShutUntilUnlocked() throws Exception {
        // The new-session config gate (Continue bar) must keep both shut: a prompt posted
        // before the preamble arrives first and makes the preamble a second session/prompt.
        JButton send = new JButton();
        PlaceholderTextArea input = new PlaceholderTextArea("");
        StatusController c = new StatusController(label(), send, new JButton(), input, new JButton(), meter());

        SwingUtilities.invokeAndWait(() -> {
            c.setSessionActive(true);
            c.updateButtonState(false);
            c.setComposerLocked(true);
            // Applied inside the EDT event that asked for it: nothing can queue the release
            // behind the lock and leave the composer shut.
            assertTrue(c.isComposerLocked());
            assertFalse(send.isEnabled(), "Go must be disabled while the config gate is open");
            assertFalse(input.isEnabled(), "the composer must be disabled while the config gate is open");
        });

        // Continue releases it in the same event — no wait for the preamble response, and no
        // dependency on the config sends succeeding.
        SwingUtilities.invokeAndWait(() -> {
            c.setComposerLocked(false);
            assertFalse(c.isComposerLocked());
            assertTrue(send.isEnabled(), "Continue re-enables Go");
            assertTrue(input.isEnabled(), "Continue re-enables the composer");
        });
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
