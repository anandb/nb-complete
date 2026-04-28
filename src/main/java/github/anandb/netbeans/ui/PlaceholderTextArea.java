package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.JTextArea;

public class PlaceholderTextArea extends JTextArea {
    private static final long serialVersionUID = 1L;
    private String placeholder;

    public PlaceholderTextArea(String placeholder) {
        super();
        this.placeholder = placeholder;
    }

    public PlaceholderTextArea(int rows, int cols, String placeholder) {
        super(rows, cols);
        this.placeholder = placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    public String getPlaceholder() {
        return placeholder;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (placeholder != null && !placeholder.isEmpty() && getText().isEmpty() && !hasFocus()) {
            Color oldColor = g.getColor();
            Font oldFont = g.getFont();
            g.setColor(new Color(150, 150, 150));
            g.setFont(oldFont.deriveFont(Font.PLAIN));
            int x = getInsets().left;
            int y = getInsets().top + g.getFontMetrics().getAscent();
            int lineHeight = g.getFontMetrics().getHeight();
            String[] lines = placeholder.split("\\R", -1);
            for (String line : lines) {
                g.drawString(line, x, y);
                y += lineHeight;
            }
            g.setColor(oldColor);
            g.setFont(oldFont);
        }
    }
}