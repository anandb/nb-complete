package github.anandb.netbeans.ui;

import java.awt.CardLayout;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import org.openide.util.NbBundle;

/**
 * Manages status bar display, thinking animation, send/stop button states,
 * and input enablement. Shared by SessionLifecycleHandler and MessageSender.
 */
// DSL-CONTROLLER: not a view — thinkingTimer (500ms) + statusResetTimer (1.5s)
// drive status label/send-stop enablement. Stays imperative; the status label
// it drives is bound via the future StatusView spec.
public class StatusController {

    private static final String[] DOT_STRINGS = {"", ".", "..", "..."};
    private static final Pattern TRAILING_DOTS = Pattern.compile("\\.+$");

    private final JLabel statusLabel;
    private final Timer thinkingTimer;
    private final Timer statusResetTimer;
    private final JButton sendBtn;
    private final JButton stopBtn;
    private final PlaceholderTextArea inputArea;
    private final JButton toggleOptionsBtn;

    private volatile boolean animatedStatus = false;
    private int thinkingDots = 0;

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
                statusLabel.setText(NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Ready"));
            }
        });
        this.statusResetTimer.setRepeats(false);
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
        statusLabel.setText(NbBundle.getMessage(AssistantTopComponent.class, key, args));
    }

    public void setStatusText(String text) {
        statusLabel.setText(text);
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
        thinkingTimer.start();
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

    // -- Button state --

    /** MUST be called on EDT. */
    public void updateButtonState(boolean isProcessing) {
        sendBtn.setEnabled(!isProcessing);
        stopBtn.setEnabled(isProcessing);
        if (sendBtn.getParent() != null && sendBtn.getParent().getLayout() instanceof CardLayout cl) {
            cl.show(sendBtn.getParent(), isProcessing ? "STOP" : "SEND");
        }
        if (isProcessing) {
            thinkingTimer.start();
        } else {
            thinkingTimer.stop();
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
            inputArea.setEnabled(enabled);
            sendBtn.setEnabled(enabled);
            toggleOptionsBtn.setVisible(enabled);
            if (!enabled) {
                inputArea.setBackground(UIManager.getColor("TextArea.background"));
            }
        });
    }

    // -- Cleanup --

    public void stopAllTimers() {
        if (thinkingTimer != null && thinkingTimer.isRunning()) {
            thinkingTimer.stop();
        }
        if (statusResetTimer != null && statusResetTimer.isRunning()) {
            statusResetTimer.stop();
        }
    }

    private void animateThinkingTick() {
        if (animatedStatus && statusLabel != null) {
            String txt = statusLabel.getText();
            if (txt != null) {
                String base = TRAILING_DOTS.matcher(txt).replaceFirst("");
                thinkingDots = (thinkingDots + 1) % 4;
                statusLabel.setText(base + DOT_STRINGS[thinkingDots]);
            }
        }
    }
}
