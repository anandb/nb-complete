package github.anandb.netbeans.ui;

import github.anandb.netbeans.support.Logger;
import java.awt.CardLayout;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.openide.util.NbBundle;

/**
 * Manages status bar display, thinking animation, send/stop button states,
 * and input enablement. Shared by SessionLifecycleHandler and MessageSender.
 */
// DSL-CONTROLLER: not a view — thinkingTimer (500ms) + statusResetTimer (1.5s)
// drive status label/send-stop enablement. Stays imperative; the status label
// it drives is bound via the future StatusView spec.
public class StatusController {

    private static final Logger LOG = Logger.from(StatusController.class);
    private static final String[] DOT_STRINGS = {"", ".", "..", "..."};

    /** Hard cap on the status label width in characters. The label sits left of
     *  the model dropdown and absorbs all spare width, so an unbounded message
     *  (error text, long file name) would resize the row on every update. */
    private static final int MAX_STATUS_LENGTH = 50;
    private static final String ELLIPSIS = "...";

    private final JLabel statusLabel;
    private final Timer thinkingTimer;
    private final Timer statusResetTimer;
    private final JButton sendBtn;
    private final JButton stopBtn;
    private final PlaceholderTextArea inputArea;
    private final JButton toggleOptionsBtn;

    private volatile Consumer<Boolean> processingListener;

    private volatile boolean animatedStatus = false;
    private int thinkingDots = 0;
    private volatile boolean sessionActive = true;
    /** The unpadded, capped text the label shows when it is not animating. The
     *  animation composes frames from this rather than from the label's current
     *  text: recovering a base by stripping trailing dots and padding back off the
     *  rendered string would also strip a genuine ellipsis, shortening the message
     *  a little on every tick. */
    private volatile String statusBase = "";

    /** How long (ms) a run may be silent before the status flags a stall.
     *  A heads-up only: the connection is NOT closed, Stop stays enabled, and
     *  any resumed inbound data clears the stall (see {@link #touchRunActivity()}). */
    private static final int RUN_STALL_MS = 60_000;

    /** Stalls are detected on a 5s tick while the run is active. */
    private final Timer runWatchdogTimer;
    private volatile boolean runWatchdogArmed = false;
    private volatile boolean runWatchdogStalled = false;
    private volatile long lastRunActivityNanos = 0L;

    public StatusController(
            JLabel statusLabel,
            JButton sendBtn,
            JButton stopBtn,
            PlaceholderTextArea inputArea,
            JButton toggleOptionsBtn) {
        this.statusLabel = statusLabel;
        this.sendBtn = sendBtn;
        this.stopBtn = stopBtn;
        this.inputArea = inputArea;
        this.toggleOptionsBtn = toggleOptionsBtn;

        this.thinkingTimer = new Timer(500, e -> animateThinkingTick());
        this.statusResetTimer = new Timer(1500, e -> {
            if (statusLabel != null) {
                show(NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Ready"));
            }
        });
        this.statusResetTimer.setRepeats(false);

        this.runWatchdogTimer = new Timer(5000, e -> checkRunWatchdog());
        this.runWatchdogTimer.setRepeats(true);
    }

    public JButton getSendBtn() {
        return sendBtn;
    }

    public JButton getStopBtn() {
        return stopBtn;
    }

    public Timer getThinkingTimer() {
        return thinkingTimer;
    }

    public Timer getStatusResetTimer() {
        return statusResetTimer;
    }

    // -- Status text --

    public void setStatus(String key, Object... args) {
        show(NbBundle.getMessage(AssistantTopComponent.class, key, args));
    }

    public void setStatusText(String text) {
        show(text);
    }

    /** Sets the label text and remembers it as the base the animation composes
     *  from. Every write to {@code statusLabel} goes through here. */
    private void show(String text) {
        statusBase = cap(text);
        statusLabel.setText(statusBase);
    }

    /** Truncates an over-long status message to {@link #MAX_STATUS_LENGTH}
     *  characters, ending it with an ellipsis. Shorter text is returned as-is;
     *  null is passed through so {@code JLabel.setText} keeps its blank semantics. */
    private static String cap(String text) {
        return cap(text, MAX_STATUS_LENGTH);
    }

    private static String cap(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }

    /** Composes one thinking-animation frame: {@code base} followed by
     *  {@code dotCount} dots, then right-padded with spaces to the width the phase
     *  may reach ({@code base.length() + 3}). Every frame of the cycle therefore has
     *  the same length — the label absorbs spare width left of the model dropdown,
     *  so an unpadded cycle ("Sending", "Sending.", "Sending..", "Sending...") would
     *  resize that row on every 500ms tick. The dot budget is reserved before the
     *  cap, so an over-long base is truncated once and then stays put. */
    static String thinkingFrame(String base, int dotCount) {
        if (base == null) {
            return null;
        }
        int dotBudget = DOT_STRINGS[DOT_STRINGS.length - 1].length();
        String stem = base.length() <= MAX_STATUS_LENGTH - dotBudget
                ? base
                : cap(base, MAX_STATUS_LENGTH - dotBudget);
        String frame = stem + DOT_STRINGS[Math.floorMod(dotCount, DOT_STRINGS.length)];
        int width = stem.length() + dotBudget;
        if (frame.length() >= width) {
            return frame;
        }
        return frame + " ".repeat(width - frame.length());
    }

    public String getStatusText() {
        String t = statusLabel.getText();
        return t != null ? t : "";
    }

    public void setTooltip(String key, Object... args) {
        statusLabel.setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, key, args));
    }

    // -- Thinking animation --

    public void startThinking() {
        animatedStatus = true;
        normalizeStatusWidth();
        thinkingTimer.start();
    }

    /** Pads the current status text to the width its animation cycle will reach, so
     *  the first tick — and every later one — leaves the label width unchanged. */
    private void normalizeStatusWidth() {
        Runnable pad = () -> {
            if (!animatedStatus) {
                // A reset (resetToReady/stopThinking) landed between the off-EDT
                // startThinking() and this hop; padding now would re-pad "Ready".
                return;
            }
            String base = statusBase;
            if (base != null) {
                statusLabel.setText(thinkingFrame(base, 0));
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            pad.run();
        } else {
            SwingUtilities.invokeLater(pad);
        }
    }

    public void stopThinking() {
        animatedStatus = false;
        thinkingTimer.stop();
    }

    public boolean isAnimated() {
        return animatedStatus;
    }

    public void scheduleReset() {
        statusResetTimer.restart();
    }

    // -- Run-stall watchdog --

    /** Arms the run-stall watchdog. Call when a message/task send begins. Safe
     *  from any thread (timer ops are marshalled to the EDT). */
    public void armRunWatchdog() {
        lastRunActivityNanos = System.nanoTime();
        runWatchdogArmed = true;
        runWatchdogStalled = false;
        SwingUtilities.invokeLater(() -> {
            // A stale "No response" message from a previous run must not be left
            // on the label when a new run begins: it persists because re-arming
            // cleared the flag touchRunActivity checks. Any fresh send therefore
            // resets the status to the running baseline.
            if (statusLabel != null
                    && statusLabel.getText() != null
                    && statusLabel.getText().startsWith(
                            NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Stalled"))) {
                LOG.info("Clearing Stalled Status during arm");
                setStatus("STATUS_Thinking");
            }
            if (!runWatchdogTimer.isRunning()) {
                runWatchdogTimer.start();
            }
        });
    }

    /** Reports inbound activity from the agent (any processed message), resetting
     *  the stall clock. If a stall had been flagged, restores the Running UI. */
    public void touchRunActivity() {
        if (!runWatchdogArmed) {
            return;
        }

        lastRunActivityNanos = System.nanoTime();
        if (runWatchdogStalled) {
            runWatchdogStalled = false;
            SwingUtilities.invokeLater(() -> {
                setStatus("STATUS_Thinking");
                startThinking();
            });
        }
    }

    /** Disarms the run-stall watchdog. Call on turn end, error, or stop. */
    public void disarmRunWatchdog() {
        runWatchdogArmed = false;
        runWatchdogStalled = false;
        SwingUtilities.invokeLater(() -> {
            if (runWatchdogTimer.isRunning()) {
                runWatchdogTimer.stop();
            }
        });
    }

    public boolean isRunWatchdogArmed() {
        return runWatchdogArmed;
    }

    private void checkRunWatchdog() {
        if (!runWatchdogArmed || !animatedStatus) {
            // Not armed, or the run UI is not in an active state (Stopping/
            // Stopped/terminal) — never override those statuses with a stall.
            return;
        }
        long idleMs = (System.nanoTime() - lastRunActivityNanos) / 1_000_000;
        if (idleMs >= RUN_STALL_MS && !runWatchdogStalled) {
            runWatchdogStalled = true;
            // Agent has produced no data for RUN_STALL_MS. Surface it so the
            // user knows the run stalled instead of silently hanging. Stop stays
            // enabled (processing is still true) so they can recover. The stall
            // clears automatically on the next touchRunActivity().
            setStatus("STATUS_Stalled");
        }
    }

    // -- Button state --

    public void setProcessingListener(Consumer<Boolean> listener) {
        this.processingListener = listener;
    }

    /** Record whether a session is currently active. Distinct from the
     *  per-message processing flag: an active session exists for the Go button
     *  even when idle; processing reflects an in-flight message. The actual
     *  Go/Stop disable-on-inactive is applied in the sessionActiveCallback. */
    public void setSessionActive(boolean active) {
        sessionActive = active;
    }

    /** MUST be called on EDT. */
    public void updateButtonState(boolean isProcessing) {
        sendBtn.setEnabled(sessionActive);
        stopBtn.setEnabled(isProcessing && sessionActive);
        if (sendBtn.getParent() != null && sendBtn.getParent().getLayout() instanceof CardLayout cl) {
            cl.show(sendBtn.getParent(), isProcessing ? "STOP" : "SEND");
        }
        if (isProcessing) {
            thinkingTimer.start();
        } else {
            thinkingTimer.stop();
        }
        Consumer<Boolean> pl = processingListener;
        if (pl != null) {
            pl.accept(isProcessing);
        }
    }

    // -- Compound actions --

    public void resetToReady() {
        SwingUtilities.invokeLater(() -> {
            setStatus("STATUS_Ready");
            animatedStatus = false;
            updateButtonState(false);
        });
    }

    public void setInputEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            toggleOptionsBtn.setVisible(enabled);
            inputArea.setEnabled(enabled);
            for (Consumer<Boolean> listener : inputEnabledListeners) {
                listener.accept(enabled);
            }
        });
    }

    /** Listeners notified (on the EDT) whenever the input-enabled state changes. */
    private final List<Consumer<Boolean>> inputEnabledListeners = new CopyOnWriteArrayList<>();

    /** Registers a listener that follows the input text area's enabled state. */
    public void addInputEnabledListener(Consumer<Boolean> listener) {
        inputEnabledListeners.add(listener);
    }

    // -- Cleanup --

    public void stopAllTimers() {
        if (thinkingTimer != null && thinkingTimer.isRunning()) {
            thinkingTimer.stop();
        }
        if (statusResetTimer != null && statusResetTimer.isRunning()) {
            statusResetTimer.stop();
        }
        if (runWatchdogTimer != null && runWatchdogTimer.isRunning()) {
            runWatchdogTimer.stop();
        }
        runWatchdogArmed = false;
        runWatchdogStalled = false;
    }

    private void animateThinkingTick() {
        if (animatedStatus && statusLabel != null) {
            String base = statusBase;
            if (base != null) {
                thinkingDots = (thinkingDots + 1) % DOT_STRINGS.length;
                statusLabel.setText(thinkingFrame(base, thinkingDots));
            }
        }
    }
}