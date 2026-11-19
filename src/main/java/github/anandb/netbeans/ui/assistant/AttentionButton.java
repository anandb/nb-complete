package github.anandb.netbeans.ui.assistant;

import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

import github.anandb.netbeans.ui.components.UIUtils;

/**
 * A toolbar button whose tooltip anchors above it: the composer toolbar sits
 * in the bottom status row, where Swing's default below-the-component
 * placement can fall off screen.
 */
public class AttentionButton extends JButton {
    private static final long serialVersionUID = 1L;

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return UIUtils.tooltipAboveButton(event, this);
    }
}
