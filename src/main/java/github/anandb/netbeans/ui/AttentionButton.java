package github.anandb.netbeans.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/**
 * A JButton subclass that supports drawing offsets (xOffset, yOffset) to enable
 * animations (shaking/bouncing) without changing the button's layout bounds.
 */
public class AttentionButton extends JButton {
    private static final long serialVersionUID = 1L;
    private int xOffset = 0;
    private int yOffset = 0;
    private float highlightAlpha = 0.0f;

    public void setOffset(int x, int y) {
        setOffsetAndHighlight(x, y, 0.0f);
    }

    public void setOffsetAndHighlight(int x, int y, float alpha) {
        if (this.xOffset != x || this.yOffset != y || this.highlightAlpha != alpha) {
            this.xOffset = x;
            this.yOffset = y;
            this.highlightAlpha = alpha;
            repaint();
        }
    }

    public int getXOffset() {
        return xOffset;
    }

    public int getYOffset() {
        return yOffset;
    }

    public float getHighlightAlpha() {
        return highlightAlpha;
    }

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        Insets ins = getInsets();
        return new Point(ins.left, -getHeight() - 8);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.translate(xOffset, yOffset);
            if (highlightAlpha > 0.0f) {
                g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                ColorTheme theme = ThemeManager.getCurrentTheme();
                java.awt.Color accent = theme.accent();
                if (accent == null) {
                    accent = new java.awt.Color(66, 133, 244);
                }
                int alphaVal = Math.round(highlightAlpha * 120); // Max opacity ~47% (120/255)
                java.awt.Color glowColor = new java.awt.Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alphaVal);
                g2d.setColor(glowColor);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
            super.paintComponent(g2d);
        } finally {
            g2d.dispose();
        }
    }
}
