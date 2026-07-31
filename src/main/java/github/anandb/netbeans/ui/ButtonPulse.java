package github.anandb.netbeans.ui;

import javax.swing.JButton;

/**
 * Pulses a toolbar button while the "missing binary" bubble is displayed.
 * Delegates to {@link HelpButtonFlash} so the animation is the exact same
 * toggle highlight used by the help icon.
 */
final class ButtonPulse {

    private ButtonPulse() {}

    /** Starts the help-icon style flash; no-op if already flashing. */
    static void start(JButton button) {
        HelpButtonFlash.flash(button);
    }

    /** Stops the flash and restores the button's original appearance. */
    static void stop(JButton button) {
        HelpButtonFlash.stop(button);
    }
}
