package github.anandb.netbeans.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

import github.anandb.netbeans.support.WobbleAnimator;

/**
 * A {@link JPanel} that wobbles side-to-side to draw attention while a
 * permission request is pending. Used by both the main assistant sidebar and
 * the mini-assistant dialog.
 */
final class WobblePanel extends JPanel {

    private final WobbleAnimator animator = new WobbleAnimator(this);

    void scheduleWobble() {
        animator.scheduleStart();
    }

    void stopWobble() {
        animator.stop();
    }

    /** Performs a rapid "buzz" burst to draw immediate attention (e.g. when the user
     *  tries to send a new message while a permission request is pending). */
    void buzz() {
        animator.buzz();
    }

    @Override
    public void paint(Graphics g) {
        if (animator.x() != 0 || animator.y() != 0) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.translate(animator.x(), animator.y());
            super.paint(g2d);
            g2d.dispose();
        } else {
            super.paint(g);
        }
    }

    @Override
    public void removeNotify() {
        animator.stop();
        super.removeNotify();
    }
}
