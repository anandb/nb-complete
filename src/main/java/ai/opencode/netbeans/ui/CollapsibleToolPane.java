package ai.opencode.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
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
        setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        Color headerBg = new Color(0, 0, 0, 8);
        Color borderCol = new Color(0, 0, 0, 15);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(headerBg);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderCol, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));

        headerLabel = new JLabel(title);
        headerLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        headerLabel.setForeground(new Color(110, 110, 110));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        toggleIcon = new JLabel(expanded ? "▼" : "▶");
        toggleIcon.setFont(new Font("Monospaced", Font.BOLD, 12));
        toggleIcon.setForeground(new Color(120, 120, 120));

        header.add(toggleIcon, BorderLayout.WEST);
        header.add(headerLabel, BorderLayout.CENTER);

        // Content
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        
        textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        textArea.setForeground(new Color(110, 110, 110));
        textArea.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 8));
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
                header.setBackground(new Color(0, 0, 0, 15));
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

    public void setContent(String content) {
        textArea.setText(content);
    }

    public void setTitle(String title) {
        headerLabel.setText(title);
    }

    private void toggle() {
        expanded = !expanded;
        contentPanel.setVisible(expanded);
        toggleIcon.setText(expanded ? "▼" : "▶");
        revalidate();
        repaint();
        // Force parent re-layout to update the chat thread
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
            if (getParent().getParent() != null) {
                getParent().getParent().revalidate();
                getParent().getParent().repaint();
            }
        }
    }
}