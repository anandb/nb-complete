package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.openide.util.NbBundle;

/**
 * Package-private helper that extracts theme/appearance logic from {@link MessageBubble}.
 */
class BubbleThemeApplier {

    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final MessageBubble owner;
    private final JPanel segments;
    private final JPanel bubblePanel;
    private final String messageId;
    private final String role;

    BubbleThemeApplier(MessageBubble owner, JPanel segments, JPanel bubblePanel,
                       String messageId, String role) {
        this.owner = owner;
        this.segments = segments;
        this.bubblePanel = bubblePanel;
        this.messageId = messageId;
        this.role = role;
    }

    /**
     * Apply background color and RoundedPanel base color for a message bubble.
     *
     * @param theme the current theme
     * @param type  the message type (user, error, assistant, tool, thought)
     */
    void applyBubbleTheme(ColorTheme theme, String type) {
        Color bgColor;
        if (null == type) {
            bgColor = TRANSPARENT;
        } else bgColor = switch (type) {
            case "assistant" -> theme.bubbleAssistant();
            case "user" -> theme.bubbleUser();
            case "error" -> theme.errorBackground();
            default -> TRANSPARENT;
        };

        owner.setBackground(theme.sunkenBackground());
        owner.setOpaque(true);

        bubblePanel.setBackground(bgColor);
        bubblePanel.setOpaque(true);
        segments.setBackground(bgColor);
        segments.setOpaque(false);

        if (bubblePanel instanceof RoundedPanel rp) {
            rp.setBaseColor(bgColor);
            rp.setOpaque(false);
        }
    }

    /**
     * Creates the user avatar label with click-to-copy behavior.
     * Used when the avatar is positioned outside the bubble (LEFT or RIGHT).
     */
    JLabel createUserAvatar() {
        Icon userIcon = UIUtils.loadUserIcon(44);
        JLabel userLabel = new JLabel(userIcon);
        userLabel.setBorder(new EmptyBorder(10, 8, 0, 10));
        userLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        userLabel.setToolTipText(NbBundle.getMessage(MessageBubble.class, "HINT_CopyToInput"));

        userLabel.addMouseListener(new MessageCopyMouseAdapter(
            userLabel,
            userIcon,
            ThemeManager.getIcon("copy.svg", 44),
            ThemeManager.getIcon("check.svg", 44),
            messageId, role, owner
        ));
        return userLabel;
    }

    /**
     * Creates and adds a TTFT (time-to-first-token) label with elapsed time formatting.
     */
    void setResponseTimeMs(long ms) {
        if (ms <= 0) return;
        String label = formatElapsed(ms);
        JLabel ttftLabel = new JLabel(label);
        ttftLabel.setToolTipText(NbBundle.getMessage(MessageBubble.class, "HINT_TimeToFirstToken", label));
        ttftLabel.setFont(ThemeManager.getFont().deriveFont(10f));
        ttftLabel.setForeground(Color.GRAY);
        ttftLabel.setBorder(new EmptyBorder(0, 0, 0, 12));
        owner.add(ttftLabel, UIUtils.createGbc(0, 1, 1.0, 0,
                GridBagConstraints.NONE, GridBagConstraints.SOUTHEAST,
                new Insets(0, 12, 2, 12)));
        owner.revalidate();
    }

    static String formatElapsed(long ms) {
        if (ms < 10000) return String.format("%.1fs", ms / 1000.0);
        if (ms < 60000) return String.format("%ds", ms / 1000);
        long mins = ms / 60000;
        long secs = (ms % 60000) / 1000;
        return String.format("%dm %ds", mins, secs);
    }
}
