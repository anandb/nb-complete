package github.anandb.netbeans.ui;

import github.anandb.netbeans.support.GlobalOpencodeConfig;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.openide.util.NbBundle;

/**
 * Sidebar bubble shown in the chat panel (instead of a modal dialog) that
 * offers to set up the starter global opencode configuration, or — with the
 * user's consent — to replace a file that cannot be parsed.
 */
class GlobalConfigBubble extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Guards against duplicate action events (e.g. a click plus a focus-lost release). */
    private boolean dismissed;

    /**
     * @param result        evaluation outcome driving the message and buttons
     * @param onYes         run when the user accepts (write the starter config)
     * @param onNo          run when the user declines for now
     * @param onDontAskAgain run when the user wants to stop future prompts
     */
    GlobalConfigBubble(GlobalOpencodeConfig.CheckResult result, Runnable onYes, Runnable onNo, Runnable onDontAskAgain) {
        boolean overwrite = result.state == GlobalOpencodeConfig.State.UNPARSEABLE;

        setLayout(new BorderLayout());
        setAlignmentY(Component.CENTER_ALIGNMENT);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ColorTheme theme = ThemeManager.getCurrentTheme();

        JPanel content = UIUtils.createBubbleContentPanel();

        JLabel titleLabel = new JLabel(NbBundle.getMessage(GlobalConfigBubble.class,
                overwrite ? "GlobalConfigBubble.Title.Overwrite" : "GlobalConfigBubble.Title.Setup"));
        titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, ThemeManager.getFont().getSize() + 4f));
        titleLabel.setForeground(theme.foreground());
        content.add(titleLabel, BorderLayout.NORTH);

        String bodyKey = overwrite ? "GlobalConfigBubble.Body.Overwrite" : "GlobalConfigBubble.Body.Setup";
        String body = overwrite
                ? NbBundle.getMessage(GlobalConfigBubble.class, bodyKey, result.fileName)
                : NbBundle.getMessage(GlobalConfigBubble.class, bodyKey);
        JTextArea bodyLabel = new JTextArea(body);
        bodyLabel.setLineWrap(true);
        bodyLabel.setWrapStyleWord(true);
        bodyLabel.setEditable(false);
        bodyLabel.setOpaque(false);
        bodyLabel.setFocusable(false);
        bodyLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        bodyLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() + 1f));
        bodyLabel.setForeground(theme.foreground());
        content.add(bodyLabel, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonsPanel.setOpaque(false);

        JButton yesBtn = new JButton(NbBundle.getMessage(GlobalConfigBubble.class,
                overwrite ? "GlobalConfigBubble.Button.Replace" : "GlobalConfigBubble.Button.Setup"));
        yesBtn.setFocusPainted(false);
        yesBtn.addActionListener(e -> {
            if (dismiss() && onYes != null) {
                onYes.run();
            }
        });

        JButton noBtn = new JButton(NbBundle.getMessage(GlobalConfigBubble.class,
                overwrite ? "GlobalConfigBubble.Button.Keep" : "GlobalConfigBubble.Button.NotNow"));
        noBtn.setFocusPainted(false);
        noBtn.addActionListener(e -> {
            if (dismiss() && onNo != null) {
                onNo.run();
            }
        });

        JButton dontAskBtn = new JButton(NbBundle.getMessage(GlobalConfigBubble.class, "GlobalConfigBubble.Button.DontAsk"));
        dontAskBtn.setFocusPainted(false);
        dontAskBtn.addActionListener(e -> {
            if (dismiss() && onDontAskAgain != null) {
                onDontAskAgain.run();
            }
        });

        buttonsPanel.add(yesBtn);
        buttonsPanel.add(noBtn);
        buttonsPanel.add(dontAskBtn);
        content.add(buttonsPanel, BorderLayout.SOUTH);

        add(content, BorderLayout.CENTER);
        setAlignmentX(LEFT_ALIGNMENT);
    }

    /** Removes this bubble from its parent container. Returns false when the
     *  bubble was already dismissed (duplicate action event), so callers can
     *  skip running their action callback a second time. */
    private boolean dismiss() {
        if (dismissed) {
            return false;
        }
        dismissed = true;
        Container parent = getParent();
        if (parent != null) {
            parent.remove(this);
            parent.revalidate();
            parent.repaint();
        }
        return true;
    }
}
