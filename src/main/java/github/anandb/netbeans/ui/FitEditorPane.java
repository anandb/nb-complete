package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.StringReader;

import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
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
    private volatile boolean revalidatePending = false;

    @Override
    public void setText(String t) {
        if (t != null && t.equals(lastText)) {
            return;
        }
        lastText = t;

        // Reuse the existing Document so the HTML view tree is updated
        // in-place rather than torn down and rebuilt from scratch.
        // JTextComponent.setText always obtains the EditorKit again,
        // opens a new StringReader, and dispatches separate remove +
        // insert DocumentEvents.  This path collapses those steps into
        // a single bulk operation on the existing document.
        javax.swing.text.Document doc = getDocument();
        if (doc == null) {
            super.setText(t);
        } else {
            try {
                if (doc.getLength() > 0) {
                    doc.remove(0, doc.getLength());
                }
                if (t != null && !t.isEmpty()) {
                    getEditorKit().read(new StringReader(t), doc, 0);
                }
            } catch (BadLocationException | java.io.IOException ex) {
                super.setText(t);
            }
        }

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
                    // +5 fudge prevents last text line from being clipped
                    cachedSize = new Dimension(w, lastComputedHeight + insets.top + insets.bottom + 5);
                    return cachedSize;
                }
            }
        } catch (Exception ex) {
            LOG.fine("View sizing failed, using fallback: {0}", ex.getMessage());
        }

        if (lastComputedHeight > 0) {
            // +5 fudge prevents last text line from being clipped
            cachedSize = new Dimension(w, Math.max(30, lastComputedHeight + insets.top + insets.bottom + 5));
            return cachedSize;
        }
        Dimension superSize = super.getPreferredSize();
        cachedSize = new Dimension(w, Math.max(30, superSize.height + insets.top + insets.bottom + 2));
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
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width > 0 && width != lastComputedWidth;
        super.setBounds(x, y, width, height);
        if (widthChanged) {
            // Invalidate cached preferred size so that getPreferredSize()
            // recomputes the height for the new width. This ensures the HTML
            // view reflows when the component is resized by the layout manager.
            lastComputedWidth = 0;
            cachedSize = null;
            // Force the HTML view to reformat at the new width.
            View root = getUI().getRootView(this);
            if (root != null) {
                Insets ins = getInsets();
                int cw = Math.max(1, width - ins.left - ins.right);
                root.setSize(cw, Integer.MAX_VALUE);
            }
            // The preferred height depends on the width (HTML text reflow).
            // After the layout manager assigns the real width, revalidate so
            // ancestors re-layout with the corrected preferred size. This avoids
            // the empty-space bug where the pane was sized for the fallback
            // 500 px width and then left oversized when the sidebar is wider.
            if (!revalidatePending) {
                revalidatePending = true;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    revalidatePending = false;
                    revalidate();
                });
            }
        }
    }

    @Override
    public Dimension getMaximumSize() {
        // Return a width sufficient for BoxLayout(Y_AXIS) to stretch this
        // component to the full container width. The preferred size height
        // is used for the returned height; width is set to a large finite
        // value so that BoxLayout does not clip the component to a small
        // preferred width (which would prevent text from wrapping to the
        // sidebar width).
        Dimension pref = getPreferredSize();
        return new Dimension(Short.MAX_VALUE, pref.height);
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

        // Bottom padding extra generous to prevent last line from being clipped
        pane.setBorder(new EmptyBorder(4, 20, 8, 6));
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.setText(styledHtml);
        return pane;
    }
}
