package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Base class for collapsible UI components in the chat.
 * Provides a standardized header, toggle behavior, and layout.
 */
public abstract class BaseCollapsiblePane extends RoundedPanel {
    protected final JPanel header;
    protected final JLabel headerLabel;
    protected final JLabel toggleIcon;
    protected final JPanel contentPanel;
    protected boolean expanded;

    public BaseCollapsiblePane(int radius, String title, Icon icon, boolean expandedByDefault) {
        super(radius);
        this.expanded = expandedByDefault;

        setLayout(new BorderLayout());
        setOpaque(false);
        setDoubleBuffered(true);

        ColorTheme theme = ThemeManager.getCurrentTheme();

        // Header setup
        header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));

        toggleIcon = new JLabel(expanded ? "▼" : "▶");
        toggleIcon.setFont(ThemeManager.getMonospaceFont().deriveFont(Font.BOLD));
        toggleIcon.setForeground(theme.getHeaderForeground());

        headerLabel = new JLabel(title, icon, JLabel.LEFT);
        headerLabel.setIconTextGap(8);
        headerLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        headerLabel.setForeground(theme.getHeaderForeground());

        header.add(toggleIcon, BorderLayout.WEST);
        header.add(headerLabel, BorderLayout.CENTER);

        // Content setup
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.setVisible(expanded);

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        // Toggle listener
        MouseAdapter toggleListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                toggle();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                onHeaderHover(true);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                onHeaderHover(false);
            }
        };
        header.addMouseListener(toggleListener);
        headerLabel.addMouseListener(toggleListener);
        toggleIcon.addMouseListener(toggleListener);
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            contentPanel.setVisible(expanded);
            toggleIcon.setText(expanded ? "▼" : "▶");
            onToggle(expanded);
            revalidate();
            repaint();
            updateParentLayout();
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    protected void toggle() {
        setExpanded(!expanded);
    }

    protected void onToggle(boolean expanded) {
        // Optional override
    }

    protected void onHeaderHover(boolean hover) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        if (hover) {
            header.setBackground(theme.getPanelHeaderHover());
        } else {
            header.setBackground(theme.getBase2()); // Default fallback
        }
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    protected void updateParentLayout() {
        Component parent = getParent();
        while (parent != null) {
            parent.revalidate();
            parent.repaint();
            if (parent.getClass().getName().contains("ChatThreadPanel")) {
                break;
            }
            parent = parent.getParent();
        }
    }

    public abstract void refreshTheme();
}
