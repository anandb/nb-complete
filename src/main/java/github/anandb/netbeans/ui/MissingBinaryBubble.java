package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.openide.util.NbBundle;
import javax.swing.JTextArea;
import org.netbeans.api.options.OptionsDisplayer;

class MissingBinaryBubble extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Delay before the copy icon reverts from a check mark (milliseconds). */
    private static final int COPY_REVERT_MS = 1500;

    private Timer copyRevertTimer;

    /** Icon shown before the copy check mark animation (captured once to survive rapid clicks). */
    private Icon copyIcon;

    MissingBinaryBubble(Runnable onGuide, Runnable onRestart) {
        setLayout(new BorderLayout());
        setAlignmentY(Component.CENTER_ALIGNMENT);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ColorTheme theme = ThemeManager.getCurrentTheme();

        JPanel content = UIUtils.createBubbleContentPanel();

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

        // Body text
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

        // Install command panel — clicking anywhere on it copies the command
        String installCmd = getInstallCommand();
        Color cmdBg = theme.isDark() ? new Color(0x1A1B26) : new Color(0xF0F0F0);
        Color cmdFg = theme.isDark() ? new Color(0xA1EFE4) : new Color(0x333333);

        JLabel cmdLabel = new JLabel("$ " + installCmd);
        cmdLabel.setFont(IconResourceManager.getMonospaceFont());
        cmdLabel.setForeground(cmdFg);
        cmdLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdLabel.setToolTipText(NbBundle.getMessage(MissingBinaryBubble.class, "MissingBinaryBubble.Hint.Copy"));

        final JButton[] copyBtnRef = new JButton[1];
        copyBtnRef[0] = UIUtils.createToolbarButton("copy.svg", 32,
            NbBundle.getMessage(MissingBinaryBubble.class, "MissingBinaryBubble.Button.Copy"),
            e -> copyCommand(copyBtnRef[0], installCmd));
        copyBtnRef[0].setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JButton copyBtn = copyBtnRef[0];
        copyIcon = copyBtn.getIcon();

        JPanel cmdPanel = new JPanel(new BorderLayout());
        cmdPanel.setOpaque(true);
        cmdPanel.setBackground(cmdBg);
        cmdPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        cmdPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                copyCommand(copyBtn, installCmd);
            }
        });
        cmdPanel.add(cmdLabel, BorderLayout.CENTER);
        cmdPanel.add(copyBtn, BorderLayout.EAST);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);
        centerPanel.add(bodyLabel, BorderLayout.NORTH);
        centerPanel.add(cmdPanel, BorderLayout.CENTER);
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

    /** Copies the command and briefly swaps the copy icon for a check mark. */
    private void copyCommand(JButton copyBtn, String installCmd) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(installCmd), null);
        if (copyIcon == null) {
            copyIcon = copyBtn.getIcon();
        }
        copyBtn.setIcon(ThemeManager.getIcon("check.svg", 14));

        // Cancel any previous revert timer to avoid leaking timers on rapid clicks.
        if (copyRevertTimer != null) {
            copyRevertTimer.stop();
        }
        copyRevertTimer = new Timer(COPY_REVERT_MS, e -> copyBtn.setIcon(copyIcon));
        copyRevertTimer.setRepeats(false);
        copyRevertTimer.start();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (copyRevertTimer != null) {
            copyRevertTimer.stop();
        }
    }

    /** Returns the OS-specific install command for OpenCode. */
    static String getInstallCommand() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "winget install SST.opencode";
        } else if (os.contains("mac")) {
            return "brew install opencode";
        } else {
            return "curl -fsSL https://opencode.ai/install.sh | sh";
        }
    }
}
