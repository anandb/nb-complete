package github.anandb.netbeans.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Right-aligned button rows, wrapping when the width runs out, with a preferred height
 * that matches the rows it lays out.
 * <p>
 * {@link java.awt.FlowLayout} reports the width of a single row, so a button bar
 * pinned to the bottom of a {@code BorderLayout} was given one row of height and then
 * wrapped to five. Devin answers a permission request with six options, and every row
 * past the first — including its Reject button — was clipped away.
 * <p>
 * Measuring and laying out share {@link #rows}, so the height reported to the parent
 * is the height actually used. A row breaks only when the next button does not fit at
 * the current width: four short answers stay on one line, while Devin's six prose
 * labels fall into two rows on a normally sized sidebar.
 */
final class ButtonRowLayout implements LayoutManager {

    private final int hgap;
    private final int vgap;

    ButtonRowLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            return measure(parent, availableWidth(parent));
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            int width = parent.getWidth();
            int y = 0;
            for (List<Component> row : rows(parent, width)) {
                int rowHeight = 0;
                for (Component comp : row) {
                    rowHeight = Math.max(rowHeight, comp.getPreferredSize().height);
                }
                int x = width;
                for (int i = row.size() - 1; i >= 0; i--) {
                    Component comp = row.get(i);
                    Dimension pref = comp.getPreferredSize();
                    x -= pref.width;
                    comp.setBounds(x, y, pref.width, rowHeight);
                    x -= hgap;
                }
                y += rowHeight + vgap;
            }
        }
    }

    /**
     * Width to wrap at: the inner width of the container that sizes this row.
     * <p>
     * Not the row's own width — that is still the <em>previous</em> width when the
     * parent asks for the preferred height, so measuring with it would reserve the
     * rows of the old width and clip the extra ones right after the panel narrows.
     */
    private static int availableWidth(Container parent) {
        Container host = parent.getParent();
        for (Container c = host != null ? host : parent; c != null; c = c.getParent()) {
            Insets insets = c.getInsets();
            int width = c.getWidth() - insets.left - insets.right;
            if (width > 0) {
                return width;
            }
        }
        return 0;
    }

    private Dimension measure(Container parent, int width) {
        int widest = 0;
        int height = 0;
        for (List<Component> row : rows(parent, width)) {
            int rowWidth = 0;
            int rowHeight = 0;
            for (Component comp : row) {
                Dimension pref = comp.getPreferredSize();
                rowWidth += (rowWidth == 0 ? 0 : hgap) + pref.width;
                rowHeight = Math.max(rowHeight, pref.height);
            }
            widest = Math.max(widest, rowWidth);
            height += (height == 0 ? 0 : vgap) + rowHeight;
        }
        return new Dimension(widest, height);
    }

    /** Visible components grouped into rows, breaking when the next one does not fit. */
    private List<List<Component>> rows(Container parent, int width) {
        List<List<Component>> rows = new ArrayList<>();
        List<Component> row = new ArrayList<>();
        int rowWidth = 0;
        for (Component comp : parent.getComponents()) {
            if (!comp.isVisible()) {
                continue;
            }
            int compWidth = comp.getPreferredSize().width;
            if (!row.isEmpty() && width > 0 && rowWidth + hgap + compWidth > width) {
                rows.add(row);
                row = new ArrayList<>();
                rowWidth = 0;
            }
            rowWidth += (row.isEmpty() ? 0 : hgap) + compWidth;
            row.add(comp);
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }
}
