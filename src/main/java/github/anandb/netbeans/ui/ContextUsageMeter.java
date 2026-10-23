package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.util.Locale;

import javax.swing.JComponent;

import org.openide.util.NbBundle;

/**
 * Compact, always-visible meter in the bottom status row showing how much of the
 * session context window the harness has consumed. Own paint component: fixed
 * preferred size, so it never triggers or participates in layout invalidation.
 *
 * <p>Fills green below 70% context, amber up to 90%, red beyond. Lives entirely
 * on the EDT (its only callers, {@link SessionLifecycleHandler} via
 * {@link StatusController#setContextUsage}, already marshal there). Deliberately
 * independent of {@link #statusLabel}: the label is ephemeral (status text,
 * thinking animation); this meter is persistent state and never written through
 * {@code StatusController.show()}.
 */
final class ContextUsageMeter extends JComponent {

    private static final int WIDTH = 72;
    private static final int BAR_HEIGHT = 8;
    private static final int VERTICAL_MARGIN = 4;
    private static final double AMBER_AT = 0.70;
    private static final double RED_AT = 0.90;

    /** Guard against division by zero and negative values from harnesses. */
    private static final double UNKNOWN_FRACTION = -1.0;

    private volatile double fraction = UNKNOWN_FRACTION;

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, BAR_HEIGHT + 2 * VERTICAL_MARGIN);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    ContextUsageMeter() {
        setOpaque(false);
        setName("contextUsageMeter");
        // getAccessibleContext() is null until the component is shown; set the
        // strings lazily so headless construction (tests, early buildHeader)
        // cannot NPE.
        initAccessibleContext();
        setRequestFocusEnabled(false);
    }

    private void initAccessibleContext() {
        javax.accessibility.AccessibleContext ac = getAccessibleContext();
        if (ac == null) {
            return;
        }
        if (ac.getAccessibleName() == null) {
            ac.setAccessibleName(NbBundle.getMessage(AssistantTopComponent.class,
                    "LBL_ContextUsage"));
        }
        ac.setAccessibleDescription(getToolTipText() != null ? getToolTipText()
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ContextUsage"));
    }

    /** Sets used/size and the tooltip. Unknown size (≤ 0) resets to an empty
     *  fill with an "unavailable" tooltip, so a session switch never shows
     *  stale data. Fraction is clamped to [0, 1]. */
    void setUsage(long used, long size) {
        if (size <= 0) {
            fraction = UNKNOWN_FRACTION;
            setToolTipText(NbBundle.getMessage(AssistantTopComponent.class,
                    "HINT_ContextUsageUnavailable"));
        } else {
            long clamped = Math.max(0, used);
            double f = (double) clamped / (double) size;
            fraction = Math.max(0.0, Math.min(1.0, f));
            NumberFormat nf = NumberFormat.getInstance(Locale.getDefault());
            Object[] args = {nf.format(clamped), nf.format(size)};
            setToolTipText(NbBundle.getMessage(AssistantTopComponent.class,
                    "HINT_ContextUsage", args));
        }
        initAccessibleContext();
        repaint();
    }

    void clear() {
        fraction = UNKNOWN_FRACTION;
        setToolTipText(null);
        repaint();
    }

    double getFraction() {
        return fraction;
    }

    /** Color for the current fill level; used by the renderer and tests. */
    Color fillColor() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        if (fraction >= RED_AT) {
            return theme.permissionDenyFg();
        }
        if (fraction >= AMBER_AT) {
            return theme.yellow();
        }
        return theme.activityAccent();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        int h = getHeight();
        int w = getWidth();
        int y = (h - BAR_HEIGHT) / 2;
        g2.setColor(trackColor());
        g2.fillRoundRect(0, y, w, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
        if (fraction > 0) {
            int fillW = (int) Math.round(w * fraction);
            g2.setColor(fillColor());
            g2.fillRoundRect(0, y, fillW, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
        }
    }

    /** Track seen on both themes: neutral with alpha, adapts to theme surface. */
    private static Color trackColor() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        return theme.bubbleBorder();
    }
}
