package github.anandb.netbeans.ui.assistant;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * Small vector accents for the welcome screen, drawn instead of typed.
 *
 * <p>An emoji codepoint is a glyph the user's font may not have — Swing then draws an empty box,
 * and Java cannot render a colour-emoji font at all — so the accents that used to be "✨" are
 * painted here instead. They follow the caller's colour (button foreground, muted text) and look
 * the same on every platform.</p>
 */
final class WelcomeAccentIcons {

    private WelcomeAccentIcons() {
    }

    /** Sparkle, the accent on the primary "Start New Chat" action. */
    static Icon sparkle(Color color, int size) {
        return new Accent(color, size) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.fill(fourPointStar(s * 0.40, s * 0.40, s * 0.40));
                g2.fill(fourPointStar(s * 0.80, s * 0.80, s * 0.18));
            }
        };
    }

    /** Light bulb: "explain this". */
    static Icon bulb(Color color, int size) {
        return new Accent(color, size) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.setStroke(stroke(s));
                // Glass as an outline reads as a bulb; a filled disc reads as a pin.
                double d = s * 0.58;
                g2.draw(new Ellipse2D.Double(s * 0.21, s * 0.08, d, d));
                g2.draw(new Line2D.Double(s * 0.36, s * 0.74, s * 0.64, s * 0.74));
                g2.draw(new Line2D.Double(s * 0.43, s * 0.89, s * 0.57, s * 0.89));
            }
        };
    }

    /** Magnifier: "find a bug". */
    static Icon magnifier(Color color, int size) {
        return new Accent(color, size) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.setStroke(stroke(s));
                double d = s * 0.50;
                g2.draw(new Ellipse2D.Double(s * 0.12, s * 0.10, d, d));
                g2.draw(new Line2D.Double(s * 0.60, s * 0.60, s * 0.88, s * 0.88));
            }
        };
    }

    /** Ticked box: "write a test". */
    static Icon checkBox(Color color, int size) {
        return new Accent(color, size) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.setStroke(stroke(s));
                g2.draw(new RoundRectangle2D.Double(
                        s * 0.10, s * 0.10, s * 0.80, s * 0.80, s * 0.26, s * 0.26));
                Path2D check = new Path2D.Double();
                check.moveTo(s * 0.28, s * 0.52);
                check.lineTo(s * 0.44, s * 0.68);
                check.lineTo(s * 0.72, s * 0.34);
                g2.draw(check);
            }
        };
    }

    /** Opposed arrows: "review changes". */
    static Icon diff(Color color, int size) {
        return new Accent(color, size) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.setStroke(stroke(s));
                g2.draw(new Line2D.Double(s * 0.14, s * 0.32, s * 0.80, s * 0.32));
                arrowHead(g2, s * 0.80, s * 0.32, 1, s);
                g2.draw(new Line2D.Double(s * 0.86, s * 0.68, s * 0.20, s * 0.68));
                arrowHead(g2, s * 0.20, s * 0.68, -1, s);
            }
        };
    }

    /** Two short strokes closing an arrow at {@code (x, y)}, pointing along {@code dir}. */
    private static void arrowHead(Graphics2D g2, double x, double y, int dir, int size) {
        double back = size * 0.18;
        double wing = size * 0.14;
        g2.draw(new Line2D.Double(x, y, x - dir * back, y - wing));
        g2.draw(new Line2D.Double(x, y, x - dir * back, y + wing));
    }

    /** A four-point star (the sparkle) centred on {@code (cx, cy)}. */
    private static Path2D fourPointStar(double cx, double cy, double r) {
        double waist = r * 0.22;
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - r);
        p.quadTo(cx + waist, cy - waist, cx + r, cy);
        p.quadTo(cx + waist, cy + waist, cx, cy + r);
        p.quadTo(cx - waist, cy + waist, cx - r, cy);
        p.quadTo(cx - waist, cy - waist, cx, cy - r);
        p.closePath();
        return p;
    }

    private static BasicStroke stroke(int size) {
        return new BasicStroke(Math.max(1.2f, size * 0.10f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /** Shared plumbing: size, colour, anti-aliasing. */
    private abstract static class Accent implements Icon {

        private final Color color;
        private final int size;

        Accent(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setColor(color);
                paint(g2, size);
            } finally {
                g2.dispose();
            }
        }

        /** Draws the accent inside a {@code size × size} box at the origin. */
        abstract void paint(Graphics2D g2, int size);
    }
}
