package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.JTextArea;
import org.openide.util.ImageUtilities;

public class CollapsibleToolPane extends RoundedPanel {
    private final JPanel contentPanel;
    private final JLabel headerLabel;
    private final JLabel toggleIcon;
    private final JTextArea textArea;
    private boolean expanded;

    public CollapsibleToolPane(String title, String content, boolean expandedByDefault) {
        super(12);
        this.expanded = expandedByDefault;

        setLayout(new BorderLayout());
        setOpaque(false);
        setDoubleBuffered(true);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color headerBg = theme.getPanelHeader();
        Color yellowAccent = theme.getYellow();

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(headerBg);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent),
            BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Icon headerIcon = null;
        String displayTitle = title;
        if (title.toUpperCase().contains("THINKING")) {
            displayTitle = expanded ? "Thinking Process" : "Thinking Process...";
            headerIcon = ThemeManager.getIcon("brain.svg", 21);
        } else if (title.toUpperCase().contains("TOOL")) {
            String stripped = title.replaceFirst("(?i)TOOL:?\\s*", "").trim();
            if (stripped.isEmpty() || stripped.equalsIgnoreCase("Tool Call")) {
                displayTitle = "Tool";
            } else {
                displayTitle = "Tool: " + stripped;
            }
            headerIcon = ThemeManager.getIcon("tool.svg", 21);
        }

        headerLabel = new JLabel(displayTitle, headerIcon, JLabel.LEFT);
        headerLabel.setIconTextGap(8);
        headerLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        headerLabel.setForeground(theme.isDark() ? Color.decode("#BBBBBB") : Color.decode("#555555"));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 0));


        toggleIcon = new JLabel(expanded ? "▼" : "▶");
        toggleIcon.setFont(ThemeManager.getMonospaceFont().deriveFont(Font.BOLD));
        toggleIcon.setForeground(theme.isDark() ? Color.decode("#BBBBBB") : Color.decode("#555555"));

        header.add(toggleIcon, BorderLayout.WEST);
        header.add(headerLabel, BorderLayout.CENTER);

        // Content
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(true);
        contentPanel.setBackground(theme.getBase2());
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent));
        
        textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        boolean isThinking = title.toUpperCase().contains("THINKING");
        textArea.setFont(isThinking
                ? ThemeManager.getFont().deriveFont(Font.PLAIN)
                : ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN));
        textArea.setForeground(theme.getForeground());
        textArea.setBorder(BorderFactory.createEmptyBorder(2, 25, 2, 10));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        
        contentPanel.add(textArea, BorderLayout.CENTER);
        contentPanel.setVisible(expanded);

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        MouseAdapter toggleListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                toggle();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                header.setBackground(theme.getPanelHeaderHover()); // Subtle hover
            }
            @Override
            public void mouseExited(MouseEvent e) {
                header.setBackground(headerBg);
            }
        };
        header.addMouseListener(toggleListener);
        headerLabel.addMouseListener(toggleListener);
        toggleIcon.addMouseListener(toggleListener);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    public void setContent(String content) {
        if (content != null && content.equals(textArea.getText())) {
            return;
        }
        textArea.setText(content);
    }

    public void setTitle(String title) {
        Icon headerIcon = null;
        String displayTitle = title;
        if (title.toUpperCase().contains("THINKING")) {
            displayTitle = expanded ? "THINKING PROCESS" : "Thinking Process...";
            headerIcon = ThemeManager.getIcon("brain.svg", 21);
        } else if (title.toUpperCase().contains("TOOL")) {
            displayTitle = "TOOL: " + title.replaceFirst("(?i)TOOL:?\\s*", "").trim();
            headerIcon = ThemeManager.getIcon("tool.svg", 21);
        }
        headerLabel.setText(displayTitle);
        headerLabel.setIcon(headerIcon);
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            contentPanel.setVisible(expanded);
            toggleIcon.setText(expanded ? "▼" : "▶");
            
            if (headerLabel.getText().toUpperCase().contains("THINKING")) {
                headerLabel.setText(expanded ? "Thinking Process" : "Thinking Process...");
            }
            revalidate();
            repaint();
        }
    }

    public void refreshTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color headerBg = theme.getBase2();
        Color yellowAccent = theme.getYellow();

        // Update components
        Component[] comps = getComponents();
        for (Component c : comps) {
            if (c instanceof JPanel jp && jp.getLayout() instanceof BorderLayout) {
                // This is likely the header or contentPanel
                jp.setBackground(headerBg);
                if (jp == contentPanel) {
                     jp.setBorder(BorderFactory.createMatteBorder(0, 4, 1, 1, yellowAccent));
                }
            }
        }
        
        textArea.setForeground(theme.getForeground());
        Color headerFg = theme.isDark() ? Color.decode("#BBBBBB") : Color.decode("#555555");
        headerLabel.setForeground(headerFg);
        toggleIcon.setForeground(headerFg);
        
        revalidate();
        repaint();
    }

    private void toggle() {
        setExpanded(!expanded);
        
        // Force parent re-layout if we are in a scrollable container
        Component parent = getParent();
        while (parent != null) {
            if (parent.getClass().getName().contains("ChatThreadPanel")) {
                parent.revalidate();
                parent.repaint();
                break;
            }
            parent = parent.getParent();
        }
    }
}