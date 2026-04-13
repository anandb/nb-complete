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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class CollapsibleToolPane extends JPanel {
    private final JPanel contentPanel;
    private final JLabel headerLabel;
    private final JLabel toggleIcon;
    private final JTextArea textArea;
    private boolean expanded;

    public CollapsibleToolPane(String title, String content, boolean expandedByDefault) {
        this.expanded = expandedByDefault;

        setLayout(new BorderLayout());
        setOpaque(false);
        setDoubleBuffered(true);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
        Color headerBg = theme.getBase2();
        Color borderCol = Color.decode("#DCD6C1");
        Color yellowAccent = theme.getYellow();

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(headerBg);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 4, 1, 1, yellowAccent),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));

        String displayTitle = title;
        if (title.toUpperCase().contains("THINKING")) {
            displayTitle = "THINKING PROCESS";
        }

        headerLabel = new JLabel(displayTitle);
        headerLabel.setFont(new Font("JetBrains Mono", Font.BOLD, 12));
        headerLabel.setForeground(theme.getBase1());
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        // Letter spacing simulation (Swing doesn't support it directly easily, but we can try uppercase)
        if (displayTitle.equals("THINKING PROCESS")) {
             headerLabel.setText("THINKING PROCESS");
        }

        toggleIcon = new JLabel(expanded ? "▼" : "▶");
        toggleIcon.setFont(new Font("Monospaced", Font.BOLD, 12));
        toggleIcon.setForeground(theme.getBase1());

        header.add(toggleIcon, BorderLayout.WEST);
        header.add(headerLabel, BorderLayout.CENTER);

        // Content
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(true);
        contentPanel.setBackground(headerBg);
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 1, 1, yellowAccent));
        
        textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
        textArea.setForeground(theme.getForeground());
        textArea.setBorder(BorderFactory.createEmptyBorder(4, 25, 8, 10));
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
                header.setBackground(new Color(230, 225, 205)); // Subtle hover
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
        textArea.setText(content);
    }

    public void setTitle(String title) {
        headerLabel.setText(title);
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            contentPanel.setVisible(expanded);
            toggleIcon.setText(expanded ? "▼" : "▶");
            
            // If it's a thinking process, update the title to include the emoji when collapsed
            if (headerLabel.getText().contains("THINKING")) {
                if (expanded) {
                    headerLabel.setText("THINKING PROCESS");
                } else {
                    headerLabel.setText("🧠 Thinking Process...");
                }
            }
 
            revalidate();
            repaint();
        }
    }

    public void refreshTheme() {
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
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
        headerLabel.setForeground(theme.getBase1());
        toggleIcon.setForeground(theme.getBase1());
        
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