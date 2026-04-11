package ai.opencode.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class CollapsibleToolPane extends JPanel {
    private final String content;
    private final JPanel contentPanel;
    private final JLabel headerLabel;
    private final JEditorPane textPane;
    private boolean expanded;

    public CollapsibleToolPane(String title, String content, boolean expandedByDefault) {
        this.content = content;
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
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));

        headerLabel = new JLabel(title);
        headerLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        headerLabel.setForeground(new Color(100, 100, 100));
        header.add(headerLabel, BorderLayout.CENTER);

        JLabel toggleIcon = new JLabel(expanded ? "▼" : "▶");
        toggleIcon.setFont(new Font("Monospaced", Font.PLAIN, 10));
        toggleIcon.setForeground(new Color(120, 120, 120));
        header.add(toggleIcon, BorderLayout.WEST);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

        // Content
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        
        textPane = new JEditorPane();
        textPane.setEditable(false);
        textPane.setContentType("text/plain");
        textPane.setOpaque(false);
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 11));
        textPane.setForeground(new Color(110, 110, 110));
        textPane.setText(content);
        textPane.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 8));
        
        contentPanel.add(textPane, BorderLayout.CENTER);
        contentPanel.setVisible(expanded);

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
                toggleIcon.setText(expanded ? "▼" : "▶");
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                header.setBackground(new Color(0, 0, 0, 15));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                header.setBackground(headerBg);
            }
        });
    }

    public void setContent(String content) {
        textPane.setText(content);
    }

    public void setTitle(String title) {
        headerLabel.setText(title);
    }

    private void toggle() {
        expanded = !expanded;
        contentPanel.setVisible(expanded);
        revalidate();
        repaint();
    }
}
