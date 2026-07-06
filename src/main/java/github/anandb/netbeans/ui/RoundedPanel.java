package github.anandb.netbeans.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

/**
 * A panel that paints itself as a rounded rectangle.
 * - If baseColor is non-null, fills with that color.
 * - Always clips children to the rounded shape.
 * - Draws a 1px border in the theme's bubbleBorder color.
 * - Insets (from setBorder) serve as outer margin for the rounded rect.
 */
// DSL-LEAF: keep imperative, wrap via UI.of(...) — Graphics2D gradient/clip paintComponent + paintBorder. Not ported to withStyle.
public class RoundedPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Set {@code nb.complete.roundedPanels=false} to disable rounded corners
     * and clipping. Useful on slow GPU / remote desktop / accessibility.
     */
    private static final boolean ROUNDED_ENABLED =
            Boolean.parseBoolean(System.getProperty("netbeans.codingassistant.roundedPanels", "true"));

    private int radius;
    private Color baseColor; // null = transparent (children fill their own backgrounds)
    private Color borderColor; // null = use theme's bubbleBorder
    private Color leftAccent; // null = no accent
    private float borderStrokeWidth = 1f;
    private boolean showBorder = true;
    private RoundRectangle2D.Float cachedShape;
    private RoundRectangle2D.Float cachedBorderShape;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    /** Cached theme-derived border color; invalidated when theme changes. */
    private Color cachedResolvedBorder;
    private ColorTheme cachedBorderTheme;

    public RoundedPanel(int radius) {
        this.radius = radius;
        setOpaque(false);
        setDoubleBuffered(true);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    public void setRadius(int radius) {
        this.radius = radius;
        invalidateShape();
    }

    public final void setBaseColor(Color color) {
        this.baseColor = color;
    }

    public final void setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
    }

    public final void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        invalidateResolvedBorder();
    }

    public final void setBorderStrokeWidth(float width) {
        this.borderStrokeWidth = width;
    }

    public final void setLeftAccent(Color accent) {
        this.leftAccent = accent;
        repaint();
    }

    private void invalidateShape() {
        cachedShape = null;
        cachedBorderShape = null;
        cachedWidth = -1;
        cachedHeight = -1;
    }

    private void invalidateResolvedBorder() {
        cachedResolvedBorder = null;
        cachedBorderTheme = null;
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        if (w != cachedWidth || h != cachedHeight) {
            invalidateShape();
        }
        super.setBounds(x, y, w, h);
    }

    private RoundRectangle2D.Float getShape() {
        RoundRectangle2D.Float shape = cachedShape;
        if (shape != null) {
            return shape;
        }
        Insets ins = getInsets();
        float x = ins.left;
        float y = ins.top;
        float w = getWidth() - ins.left - ins.right;
        float h = getHeight() - ins.top - ins.bottom;
        shape = new RoundRectangle2D.Float(x, y, w, h, radius, radius);
        cachedShape = shape;
        cachedWidth = getWidth();
        cachedHeight = getHeight();
        return shape;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (baseColor == null) {
            return;
        }
        if (ROUNDED_ENABLED && radius > 0) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(baseColor);
                g2.fill(getShape());
            } finally {
                g2.dispose();
            }
        } else {
            Insets ins = getInsets();
            g.setColor(baseColor);
            g.fillRect(ins.left, ins.top,
                    getWidth() - ins.left - ins.right,
                    getHeight() - ins.top - ins.bottom);
        }
    }

    @Override
    protected void paintChildren(Graphics g) {
        Insets ins = getInsets();
        int w = getWidth() - ins.left - ins.right;
        int h = getHeight() - ins.top - ins.bottom;

        // Resolve border color once and cache it; re-resolve only when the
        // explicit borderColor changes or the theme changes.
        Color resolvedBorder = borderColor != null ? borderColor : resolveThemeBorder();
        boolean drawBorder = showBorder && resolvedBorder != null && resolvedBorder.getAlpha() > 0;

        if (ROUNDED_ENABLED && radius > 0) {
            // Single Graphics2D pass: clip, paint children, draw border.
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.clip(getShape());
                super.paintChildren(g2);
                if (leftAccent != null && leftAccent.getAlpha() > 0) {
                    // Paint a 4px rounded accent on the left side.
                    g2.setColor(leftAccent);
                    int accentWidth = 4;
                    RoundRectangle2D.Float full = getShape();
                    RoundRectangle2D.Float accentShape = new RoundRectangle2D.Float(
                            full.x, full.y, accentWidth, full.height,
                            radius, radius);
                    // Clip accent to the rounded shape so it doesn't bleed outside.
                    Area accentArea = new Area(accentShape);
                    accentArea.intersect(new Area(full));
                    g2.fill(accentArea);
                }
                if (drawBorder) {
                    g2.setColor(resolvedBorder);
                    float sw = borderColor != null ? borderStrokeWidth : 1f;
                    g2.setStroke(new BasicStroke(sw));
                    g2.draw(getBorderShape(ins, w, h));
                }
            } finally {
                g2.dispose();
            }
        } else {
            super.paintChildren(g);

            if (drawBorder) {
                g.setColor(resolvedBorder);
                g.drawRect(ins.left, ins.top, w - 1, h - 1);
            }
        }
    }

    private Color resolveThemeBorder() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        if (cachedBorderTheme == theme && cachedResolvedBorder != null) {
            return cachedResolvedBorder;
        }
        cachedBorderTheme = theme;
        cachedResolvedBorder = theme.bubbleBorder();
        return cachedResolvedBorder;
    }

    private RoundRectangle2D.Float getBorderShape(Insets ins, int w, int h) {
        RoundRectangle2D.Float shape = cachedBorderShape;
        if (shape != null) {
            return shape;
        }
        shape = new RoundRectangle2D.Float(
                ins.left + 0.5f, ins.top + 0.5f,
                w - 1.0f, h - 1.0f,
                radius, radius);
        cachedBorderShape = shape;
        return shape;
    }

    @Override
    public void paintBorder(Graphics g) {
        // Border is drawn after children in paintChildren() to avoid being overwritten
    }
}
