package github.anandb.netbeans.ui.components;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * A JPanel that implements {@link Scrollable} for use inside a JScrollPane.
 * Tracks viewport width to fill the container horizontally, but not height.
 */
public class ScrollablePanel extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;

    /** Client property: fill the viewport height while the content is shorter than it. */
    public static final String TRACK_HEIGHT_IF_SHORTER = "scrollablePanel.trackHeightIfShorter";

    public ScrollablePanel() {
        setOpaque(false);
        setDoubleBuffered(true);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : 16;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        if (!Boolean.TRUE.equals(getClientProperty(TRACK_HEIGHT_IF_SHORTER))) {
            return false;
        }
        // Only while the content fits: tracking a taller panel would clip it instead of scrolling.
        Container parent = getParent();
        return parent == null || getPreferredSize().height < parent.getHeight();
    }
}
