package github.anandb.netbeans.ui;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

/**
 * Draws an icon a few pixels above its normal position, for optical centering.
 * <p>
 * A glyph whose ink already fills its square box is geometrically centered but can read low
 * when its visual weight sits in the lower half — the padlock's body dominates its shackle.
 * Raising the paint position without changing the icon size or the button metrics keeps the
 * row's geometry intact.
 */
final class RaisedIcon implements Icon {

    private final Icon delegate;
    private final int rise;

    RaisedIcon(Icon delegate, int rise) {
        this.delegate = delegate;
        this.rise = rise;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        delegate.paintIcon(c, g, x, y - rise);
    }

    @Override
    public int getIconWidth() {
        return delegate.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return delegate.getIconHeight();
    }
}
