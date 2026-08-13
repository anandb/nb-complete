package github.anandb.netbeans.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * Wraps the paperclip icon and overlays a count badge in its top-right corner,
 * making attached files immediately visible. A plain dot is too subtle to signal
 * "this message has attachments"; a numeric badge is far more salient and also
 * distinguishes 1 file from several.
 */
final class AttachmentBadgeIcon implements Icon {

    private final Icon base;
    private final int count;

    AttachmentBadgeIcon(Icon base, int count) {
        this.base = base;
        this.count = count;
    }

    @Override
    public int getIconWidth() {
        return base.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return base.getIconHeight();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        base.paintIcon(c, g, x, y);
        if (count <= 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getIconWidth();
            int digits = Integer.toString(count).length();
            int badgeSize = (int) Math.round(w * (digits > 1 ? 0.62 : 0.52));
            badgeSize = Math.max(badgeSize, 9);

            // Top-right corner, clamped inside the icon bounds.
            int bx = x + w - badgeSize;
            bx = Math.max(bx, x);
            int by = y;

            ColorTheme theme = ThemeManager.getCurrentTheme();
            Color fill = theme.accent();
            if (fill == null) {
                fill = Color.GRAY;
            }

            g2.setColor(fill);
            g2.fillRoundRect(bx, by, badgeSize, badgeSize, badgeSize, badgeSize);

            // Thin light ring for contrast on any toolbar background.
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(bx, by, badgeSize, badgeSize, badgeSize, badgeSize);

            String s = Integer.toString(count);
            Font f = c.getFont().deriveFont(Font.BOLD, badgeSize * 0.6f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int tx = bx + (badgeSize - fm.stringWidth(s)) / 2;
            int ty = by + (badgeSize - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(Color.WHITE);
            g2.drawString(s, tx, ty);
        } finally {
            g2.dispose();
        }
    }
}
