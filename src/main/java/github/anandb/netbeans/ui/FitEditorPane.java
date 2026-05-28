package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.text.View;

import github.anandb.netbeans.support.Logger;

public class FitEditorPane extends JTextPane {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.from(FitEditorPane.class);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private int lastComputedHeight = 0;
    private int lastComputedWidth = 0;
    private String lastText = null;
    private Dimension cachedSize = null;

    @Override
    public void setText(String t) {
        if (t != null && t.equals(lastText)) {
            return;
        }
        lastText = t;
        super.setText(t);
        lastComputedHeight = 0;
        cachedSize = null;
    }

    @Override
    public Dimension getPreferredSize() {
        Insets insets = getInsets();

        int w = getWidth();
        if (w <= 0 || (getParent() != null && getParent().getWidth() > 0 && getParent().getWidth() != w)) {
            Component p = getParent();
            while (p != null) {
                if (p.getWidth() > 0) {
                    w = p.getWidth();
                    break;
                }
                p = p.getParent();
            }
        }

        if (w <= 0) {
            w = 500;
        }

        if (w == lastComputedWidth && lastComputedHeight > 0 && cachedSize != null) {
            return cachedSize;
        }

        try {
            View root = getUI().getRootView(this);
            if (root != null) {
                int contentWidth = Math.max(1, w - insets.left - insets.right);
                root.setSize(contentWidth, Integer.MAX_VALUE);

                float h = root.getPreferredSpan(View.Y_AXIS);
                if (h > 0) {
                    lastComputedHeight = (int) Math.ceil(h);
                    lastComputedWidth = w;
                    cachedSize = new Dimension(w, lastComputedHeight + insets.top + insets.bottom + 20);
                    return cachedSize;
                }
            }
        } catch (Exception ex) {
            LOG.fine("View sizing failed, using fallback: {0}", ex.getMessage());
        }

        if (lastComputedHeight > 0) {
            cachedSize = new Dimension(w, Math.max(30, lastComputedHeight + insets.top + insets.bottom + 20));
            return cachedSize;
        }
        Dimension superSize = super.getPreferredSize();
        cachedSize = new Dimension(w, Math.max(30, superSize.height + insets.top + insets.bottom + 20));
        return cachedSize;
    }

    @Override
    public void doLayout() {
        try {
            super.doLayout();
        } catch (ArrayIndexOutOfBoundsException e) {
            // JDK bug: BoxView.getOffset() assumes non-empty view children
            // during HTML layout when the view tree hasn't been built yet.
            // Safe to ignore — layout will succeed on the next pass.
        }
    }

    @Override
    public float getAlignmentX() {
        return Component.LEFT_ALIGNMENT;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    public static FitEditorPane createHtmlPane(String styledHtml, Color bg, String role, boolean opaqueUser) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        FitEditorPane pane = new FitEditorPane();
        pane.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setEditable(false);
        pane.setContentType("text/html");
        if (opaqueUser && "user".equals(role) && bg != null && bg.getAlpha() > 0) {
            pane.setOpaque(true);
            pane.setBackground(bg);
        } else {
            pane.setOpaque(false);
            pane.setBackground(TRANSPARENT);
        }
        pane.setMargin(new Insets(0, 0, 0, 0));
        pane.setForeground(theme.foreground());
        pane.setDoubleBuffered(true);
        pane.setFont(ThemeManager.getFont());
        pane.setBorder(new EmptyBorder(6, 20, 10, 6));
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.setText(styledHtml);
        return pane;
    }
}
