package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.Border;

public class UIUtils {

    public static JButton createToolbarButton(String iconName, String toolTip, ActionListener l) {
        return createToolbarButton(iconName, 24, toolTip, l);
    }

    public static JButton createToolbarButton(String iconName, int iconSize, String toolTip, ActionListener l) {
        JButton btn = new JButton();
        btn.setIcon(ThemeManager.getIcon(iconName, iconSize));
        btn.setToolTipText(toolTip);
        styleToolbarButton(btn);
        if (l != null) {
            btn.addActionListener(l);
        }
        return btn;
    }

    public static JButton createTextButton(String text, ActionListener l) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(2, 12, 2, 12));
        btn.setPreferredSize(new Dimension(65, 32));
        if (l != null) {
            btn.addActionListener(l);
        }
        return btn;
    }

    public static void styleToolbarButton(JButton btn) {
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (btn.getIcon() != null) {
            int w = btn.getIcon().getIconWidth();
            int h = btn.getIcon().getIconHeight();
            btn.setPreferredSize(new Dimension(w + 8, h + 8));
        }
    }

    public static JPanel createPanel(LayoutManager layout, boolean opaque, Color bg, Border border) {
        JPanel p = new JPanel(layout);
        p.setOpaque(opaque);
        if (bg != null) {
            p.setBackground(bg);
        }
        if (border != null) {
            p.setBorder(border);
        }
        return p;
    }

    public static JPanel createTransparentPanel(LayoutManager layout) {
        return createPanel(layout, false, null, null);
    }

    public static GridBagConstraints createGbc(int x, int y, double wx, double wy, int fill, int anchor, Insets insets) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.weightx = wx;
        gbc.weighty = wy;
        gbc.fill = fill;
        gbc.anchor = anchor;
        gbc.insets = insets != null ? insets : new Insets(0, 0, 0, 0);
        return gbc;
    }
}
