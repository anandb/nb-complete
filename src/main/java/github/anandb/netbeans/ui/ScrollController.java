package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLayeredPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openide.util.NbBundle;

public class ScrollController implements KeyEventDispatcher {
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

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);

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
            vertical.setValue(vertical.getMaximum());
        });
        t.setRepeats(false);
        return t;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            int keyCode = e.getKeyCode();
            if (keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN
                    || ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0
                        && (keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END))) {
                Component src = e.getComponent();
                if (src != null && SwingUtilities.isDescendingFrom(src, parentComponent)) {
                    if (keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN) {
                        Component c = src;
                        while (c != null) {
                            if (c instanceof JComboBox) {
                                return false;
                            }
                            c = c.getParent();
                        }
                    }
                    switch (keyCode) {
                        case KeyEvent.VK_PAGE_UP -> scrollByBlock(true);
                        case KeyEvent.VK_PAGE_DOWN -> scrollByBlock(false);
                        case KeyEvent.VK_HOME -> scrollToTop();
                        case KeyEvent.VK_END -> scrollToBottom(true);
                        default -> {}
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void redirectMouseWheel(Component source, MouseEvent e) {
        scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(source, e, scrollPane));
    }

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
        return (value + extent >= maximum - 16);
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
            vertical.setValue(vertical.getMaximum());
            scrollDownBtn.setVisible(false);
            if (!scrollTimer.isRunning()) {
                scrollTimer.start();
            }
        });
    }

    public void cleanup() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
        if (scrollTimer.isRunning()) {
            scrollTimer.stop();
        }
    }
}
