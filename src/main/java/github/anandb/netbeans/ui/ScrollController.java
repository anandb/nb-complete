package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openide.util.NbBundle;

public class ScrollController {
    private static final Color SCROLL_BTN_COLOR_A = new Color(41, 98, 255, 200);
    private static final Color SCROLL_BTN_COLOR_B = new Color(41, 98, 255, 240);

    private final JScrollPane scrollPane;
    private final Component parentComponent;
    private final JButton scrollDownBtn;
    private final Timer scrollTimer;

    public ScrollController(JScrollPane scrollPane, Component parentComponent, JLayeredPane layeredPane) {
        this.scrollPane = scrollPane;
        this.parentComponent = parentComponent;

        this.scrollDownBtn = createScrollDownBtn();
        this.scrollTimer = createScrollTimer();

        layeredPane.add(scrollDownBtn, JLayeredPane.PALETTE_LAYER);

        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateScrollDownBtnVisibility();
            }
        });
    }

    private JButton createScrollDownBtn() {
        JButton btn = new JButton(ThemeManager.getIcon("scroll-down.svg", 24)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SCROLL_BTN_COLOR_A);
                g2.fillOval(2, 2, getWidth() - 5, getHeight() - 5);
                g2.setColor(SCROLL_BTN_COLOR_B);
                g2.fillOval(3, 3, getWidth() - 7, getHeight() - 7);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(NbBundle.getMessage(ChatThreadPanel.class, "HINT_ScrollToBottom"));
        btn.setVisible(false);
        btn.addActionListener(e -> {
            scrollToBottom(true);
            btn.setVisible(false);
        });
        return btn;
    }

    private Timer createScrollTimer() {
        Timer t = new Timer(100, e -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(Math.max(0, vertical.getMaximum() - BOTTOM_PADDING));
        });
        t.setRepeats(false);
        return t;
    }

    public void redirectMouseWheel(Component source, MouseEvent e) {
        scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(source, e, scrollPane));
    }

    private static final int BOTTOM_PADDING = 40;

    public void fixMouseWheel(Component c) {
        c.addMouseWheelListener(e -> {
            scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(c, e, scrollPane));
            e.consume();
        });
    }

    public boolean isAtBottom() {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        int extent = vertical.getModel().getExtent();
        int value = vertical.getValue();
        int maximum = vertical.getMaximum();
        return (value + extent >= maximum - BOTTOM_PADDING);
    }

    public void positionScrollDownBtn(int parentWidth, int parentHeight) {
        int btnSize = 36;
        int margin = 12;
        scrollDownBtn.setBounds(
            parentWidth - btnSize - margin,
            parentHeight - btnSize - 37,
            btnSize,
            btnSize
        );
    }

    private void updateScrollDownBtnVisibility() {
        scrollDownBtn.setVisible(!isAtBottom());
    }

    public void scrollByBlock(boolean pageUp) {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        if (pageUp) {
            vertical.setValue(vertical.getValue() - vertical.getVisibleAmount());
        } else {
            vertical.setValue(vertical.getValue() + vertical.getVisibleAmount());
        }
    }

    public void scrollToTop() {
        scrollPane.getVerticalScrollBar().setValue(0);
    }

    public void scrollToBottom() {
        scrollToBottom(false);
    }

    public void scrollToBottom(boolean force) {
        SwingUtilities.invokeLater(() -> {
            if (!force && !isAtBottom()) {
                return;
            }
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            int target = Math.max(0, vertical.getMaximum() - BOTTOM_PADDING);
            vertical.setValue(target);
            scrollDownBtn.setVisible(false);
            if (!scrollTimer.isRunning()) {
                scrollTimer.start();
            }
        });
    }

    public void cleanup() {
        if (scrollTimer.isRunning()) {
            scrollTimer.stop();
        }
    }
}
