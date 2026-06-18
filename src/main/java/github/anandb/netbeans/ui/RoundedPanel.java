package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.Component;
import javax.swing.JPanel;

/**
 * A panel that paints itself as a rounded rectangle.
 * - If baseColor is non-null, fills with that color.
 * - Always clips children to the rounded shape.
 * - Draws a 1px border in the theme's bubbleBorder color.
 * - Insets (from setBorder) serve as outer margin for the rounded rect.
 */
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
    private boolean showBorder = true;
    private volatile RoundRectangle2D.Float cachedShape;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

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
    }

    private void invalidateShape() {
        cachedShape = null;
        cachedWidth = -1;
        cachedHeight = -1;
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

        // Resolve border color once for this paint pass.
        Color resolvedBorder = borderColor != null ? borderColor : ThemeManager.getCurrentTheme().bubbleBorder();
        boolean drawBorder = showBorder && resolvedBorder != null && resolvedBorder.getAlpha() > 0;

        if (ROUNDED_ENABLED && radius > 0) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.clip(getShape());
                super.paintChildren(g2);
            } finally {
                g2.dispose();
            }

            // Draw rounded border AFTER children
            if (drawBorder) {
                Graphics2D g3 = (Graphics2D) g.create();
                try {
                    g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g3.setColor(resolvedBorder);
                    g3.draw(new RoundRectangle2D.Float(
                            ins.left + 0.5f, ins.top + 0.5f,
                            w - 1.0f, h - 1.0f,
                            radius, radius));
                } finally {
                    g3.dispose();
                }
            }
        } else {
            super.paintChildren(g);

            if (drawBorder) {
                g.setColor(resolvedBorder);
                g.drawRect(ins.left, ins.top, w - 1, h - 1);
            }
        }
    }

    @Override
    public void paintBorder(Graphics g) {
        // Border is drawn after children in paintChildren() to avoid being overwritten
    }
}
