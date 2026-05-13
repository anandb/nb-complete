package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.left;

import org.openide.util.NbBundle;

import github.anandb.netbeans.manager.SessionTitleMapper;
import github.anandb.netbeans.model.Session;

class WelcomeScreen {

    public static void show(JPanel messagesContainer, List<Session> sessions,
                            Consumer<String> onSessionSelected, Runnable onNewChat) {
        messagesContainer.removeAll();

        JLabel titleLabel = new JLabel(sessions.isEmpty()
            ? NbBundle.getMessage(ChatThreadPanel.class, "LBL_WelcomeToACP")
            : NbBundle.getMessage(ChatThreadPanel.class, "LBL_WelcomeBack"));
        titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 12, 10, 12));
        messagesContainer.add(titleLabel);

        JLabel subtitle = new JLabel(sessions.isEmpty()
            ? NbBundle.getMessage(ChatThreadPanel.class, "MSG_NewChatPrompt")
            : NbBundle.getMessage(ChatThreadPanel.class, "MSG_ExistingChatPrompt"));
        subtitle.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
        subtitle.setForeground(Color.GRAY);
        subtitle.setBorder(BorderFactory.createEmptyBorder(0, 12, 20, 12));
        messagesContainer.add(subtitle);

        JButton newChatBtn = createSelectionButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_StartNewChat"), null);
        newChatBtn.addActionListener(e -> onNewChat.run());
        messagesContainer.add(newChatBtn);
        messagesContainer.add(Box.createVerticalStrut(12));

        if (!sessions.isEmpty()) {
            JSeparator sep = new JSeparator();
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            messagesContainer.add(sep);
            messagesContainer.add(Box.createVerticalStrut(12));

            for (Session s : sessions) {
                String title = defaultIfBlank(s.title(), "Chat " + left(s.id(), 8));
                String label = SessionTitleMapper.getTitle(s.id(), title);
                String dir = s.effectiveDirectory();
                JButton sessionBtn = createSelectionButton(label, dir);
                sessionBtn.addActionListener(e -> onSessionSelected.accept(s.id()));
                messagesContainer.add(sessionBtn);
                messagesContainer.add(Box.createVerticalStrut(4));
            }
        }

        messagesContainer.revalidate();
    }

    private static JButton createSelectionButton(String text, String subtext) {
        ColorTheme theme = ThemeManager.getCurrentTheme();

        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(8, 0));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.bubbleBorder() != null ? theme.bubbleBorder() : Color.LIGHT_GRAY, 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textPanel = new JPanel(new GridLayout(subtext != null ? 2 : 1, 1));
        textPanel.setOpaque(false);

        JLabel mainLabel = new JLabel(text);
        mainLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        textPanel.add(mainLabel);

        if (subtext != null) {
            String folder = new File(subtext).getName();
            JLabel subLabel = new JLabel(NbBundle.getMessage(ChatThreadPanel.class, "LBL_InFolder", folder));
            subLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
            subLabel.setForeground(Color.GRAY);
            textPanel.add(subLabel);
        }

        btn.add(textPanel, BorderLayout.CENTER);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(btn.getPreferredSize().height, 60)));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setOpaque(false);
                btn.setBackground(new Color(0, 0, 0, 10));
                btn.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setOpaque(false);
                btn.repaint();
            }
        });

        return btn;
    }
}
