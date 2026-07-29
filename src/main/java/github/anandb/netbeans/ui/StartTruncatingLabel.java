package github.anandb.netbeans.ui;

import javax.swing.JLabel;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;

public class StartTruncatingLabel extends JLabel {
    private static final long serialVersionUID = 1L;

    public StartTruncatingLabel(String text) {
        super(text);
    }

    @Override
    protected void paintComponent(Graphics g) {
        String originalText = getText();
        if (originalText == null || originalText.isEmpty()) {
            super.paintComponent(g);
            return;
        }

        Insets insets = getInsets();
        int availableWidth = getWidth() - insets.left - insets.right;
        FontMetrics fm = g.getFontMetrics(getFont());

        if (fm.stringWidth(originalText) <= availableWidth) {
            super.paintComponent(g);
            return;
        }

        String prefix = "...";
        int prefixWidth = fm.stringWidth(prefix);
        int targetWidth = availableWidth - prefixWidth;

        if (targetWidth <= 0) {
            setText(prefix);
            super.paintComponent(g);
            setText(originalText);
            return;
        }

        int start = 0;
        for (int i = 0; i < originalText.length(); i++) {
            if (fm.stringWidth(originalText.substring(i)) <= targetWidth) {
                start = i;
                break;
            }
        }

        String truncated = prefix + originalText.substring(start);
        setText(truncated);
        super.paintComponent(g);
        setText(originalText);
    }
}
