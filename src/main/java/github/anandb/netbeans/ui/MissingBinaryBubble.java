package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openide.util.NbBundle;
import javax.swing.JTextArea;
import org.netbeans.api.options.OptionsDisplayer;

class MissingBinaryBubble extends JPanel {
    private static final long serialVersionUID = 1L;

    MissingBinaryBubble(Runnable onGuide, Runnable onRestart) {
        setLayout(new BorderLayout());
        setAlignmentY(Component.CENTER_ALIGNMENT);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ColorTheme theme = ThemeManager.getCurrentTheme();

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(true);
        // We use a light blue-ish background or assistantBubbleBg to match the theme
        content.setBackground(theme.bubbleAssistant() != null ? theme.bubbleAssistant() : theme.sunkenBackground());
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.bubbleBorder() != null ? theme.bubbleBorder() : Color.LIGHT_GRAY, 1, true),
            BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(NbBundle.getMessage(MissingBinaryBubble.class, "MissingBinaryBubble.Title"));
        titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, ThemeManager.getFont().getSize() + 4f));
        titleLabel.setForeground(theme.foreground());
        
        JButton settingsBtn = UIUtils.createToolbarButton("settings.svg", 32, "Open Settings", e -> {
            OptionsDisplayer.getDefault().open("github-anandb-netbeans-ui-ACPOptionsPanelController");
        });
        settingsBtn.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(settingsBtn, BorderLayout.EAST);

        content.add(headerPanel, BorderLayout.NORTH);

        String text = NbBundle.getMessage(MissingBinaryBubble.class, "MissingBinaryBubble.Body");
        JTextArea bodyLabel = new JTextArea(text);
        bodyLabel.setLineWrap(true);
        bodyLabel.setWrapStyleWord(true);
        bodyLabel.setEditable(false);
        bodyLabel.setOpaque(false);
        bodyLabel.setFocusable(false);
        bodyLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        bodyLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() + 1f));
        bodyLabel.setForeground(theme.foreground());
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(bodyLabel, BorderLayout.CENTER);
        content.add(centerPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonsPanel.setOpaque(false);

        JButton guideBtn = new JButton(NbBundle.getMessage(MissingBinaryBubble.class, "MissingBinaryBubble.Button.Guide"));
        guideBtn.setFocusPainted(false);
        guideBtn.addActionListener(e -> {
            if (onGuide != null) onGuide.run();
        });

        JButton restartBtn = new JButton(NbBundle.getMessage(MissingBinaryBubble.class, "MissingBinaryBubble.Button.Restart"));
        restartBtn.setFocusPainted(false);
        restartBtn.addActionListener(e -> {
            if (onRestart != null) onRestart.run();
        });

        buttonsPanel.add(guideBtn);
        buttonsPanel.add(restartBtn);

        content.add(buttonsPanel, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        setAlignmentX(LEFT_ALIGNMENT);
    }
}
