package github.anandb.netbeans.ui;

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
        return false;
    }
}
