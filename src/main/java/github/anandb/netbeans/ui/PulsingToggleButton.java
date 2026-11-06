package github.anandb.netbeans.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import github.anandb.netbeans.ui.rendering.ThemeManager;

/**
 * A latching toolbar button that can draw attention to a state change with a short
 * one-shot opacity pulse.
 * <p>
 * Break mode uses it: arming the mode pulses the coffee icon twice (1.2 s) and then the
 * icon stays still. A repeating blink was rejected — motion is reserved for the transition,
 * while the accent pill painted behind the icon, the tooltip and the per-request chat
 * result carry the persistent state.
 * <p>
 * The pulse fades the whole button content via {@link AlphaComposite} instead of swapping
 * icons, so icon geometry, selected-icon handling and disabled rendering are untouched.
 * The timer must not outlive the component: {@link #removeNotify()} stops it.
 */
public final class PulsingToggleButton extends JToggleButton {

    private static final long serialVersionUID = 1L;

    /** Frame period: 30 frames × 40 ms = 1.2 s per pulse. */
    private static final int FRAME_MS = 40;
    private static final int FRAMES = 30;
    /** Two dips, matching the "one confident beat" interaction spec. */
    private static final int DIPS = 2;
    private static final float MIN_ALPHA = 0.55f;
    /** Selected-state pill: same 8 px corner radius as the permission glow. */
    private static final int SELECTED_ARC = 8;
    /** Fill/border alpha: readable as "on" against both light and dark headers. */
    private static final int SELECTED_FILL_ALPHA = 46;
    private static final int SELECTED_BORDER_ALPHA = 130;

    private final Timer pulseTimer;
    private int frame;
    private float alpha = 1f;

    PulsingToggleButton(Icon releasedIcon, Icon pressedIcon) {
        setIcon(releasedIcon);
        setSelectedIcon(pressedIcon);
        pulseTimer = new Timer(FRAME_MS, e -> advancePulse());
        pulseTimer.setRepeats(true);
    }

    /** Starts the pulse from full opacity. Safe to call repeatedly; restarts the beat. */
    void pulse() {
        pulseTimer.stop();
        frame = 0;
        setAlpha(1f);
        pulseTimer.start();
    }

    /** Cancels a running pulse and restores full opacity. */
    void stopPulse() {
        pulseTimer.stop();
        frame = 0;
        setAlpha(1f);
    }

    private void advancePulse() {
        frame++;
        if (frame >= FRAMES) {
            pulseTimer.stop();
            setAlpha(1f);
            return;
        }
        setAlpha(alphaFor(frame));
    }

    private void setAlpha(float value) {
        if (alpha != value) {
            alpha = value;
            repaint();
        }
    }

    /**
     * Opacity at a given frame: full at both ends, two cosine dips in between. Kept static
     * so the curve is testable without an EDT timer.
     */
    static float alphaFor(int frameIndex) {
        if (frameIndex <= 0 || frameIndex >= FRAMES) {
            return 1f;
        }
        double t = (double) frameIndex / FRAMES;
        double wave = 0.5 + 0.5 * Math.cos(2 * Math.PI * DIPS * t);
        return (float) (MIN_ALPHA + (1f - MIN_ALPHA) * wave);
    }

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        // Same above-the-button placement as the other header buttons.
        Insets ins = getInsets();
        return new Point(ins.left, -getHeight() - 8);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (alpha < 1f) {
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            }
            if (isSelected()) {
                paintSelectedState(g2);
            }
            super.paintComponent(g2);
        } finally {
            g2.dispose();
        }
    }

    /**
     * A persistent accent pill behind the icon while armed: the coffee glyph alone reads as
     * just another toolbar button, and the pulse is reserved for the transition.
     */
    private void paintSelectedState(Graphics2D g2) {
        Color accent = ThemeManager.getCurrentTheme().accent();
        if (accent == null) {
            accent = new Color(66, 133, 244);
        }
        Object antialias = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), SELECTED_FILL_ALPHA));
        g2.fillRoundRect(0, 0, width - 1, height - 1, SELECTED_ARC, SELECTED_ARC);
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), SELECTED_BORDER_ALPHA));
        g2.drawRoundRect(0, 0, width - 1, height - 1, SELECTED_ARC, SELECTED_ARC);
        if (antialias != null) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
        }
    }

    @Override
    public void removeNotify() {
        pulseTimer.stop();
        // Direct assignment: repainting a component being removed is pointless, and a
        // half-faded icon must not survive into a re-added header.
        alpha = 1f;
        super.removeNotify();
    }
}
