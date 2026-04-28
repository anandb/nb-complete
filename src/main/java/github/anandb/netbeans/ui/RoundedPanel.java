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

    public void setBaseColor(Color color) {
        this.baseColor = color;
    }

    public void setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
    }

    public void setBorderColor(Color borderColor) {
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
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(baseColor);
        g2.fill(getShape());
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D.Float shape = getShape();
        if (radius > 0) {
            g2.clip(shape);
        }
        super.paintChildren(g2);
        g2.dispose();

        // Draw border AFTER children so it is always on top of child panels
        Color border = borderColor != null ? borderColor : ThemeManager.getCurrentTheme().bubbleBorder();
        if (showBorder && border != null && border.getAlpha() > 0) {
            Graphics2D g3 = (Graphics2D) g.create();
            g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g3.setColor(border);
            g3.draw(new RoundRectangle2D.Float(
                    shape.x + 0.5f, shape.y + 0.5f,
                    shape.width - 1.0f, shape.height - 1.0f,
                    radius, radius));
            g3.dispose();
        }
    }

    @Override
    public void paintBorder(Graphics g) {
        // Border is drawn after children in paintChildren() to avoid being overwritten
    }
}
