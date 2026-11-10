package github.anandb.netbeans.ui.assistant;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.left;

import org.openide.util.NbBundle;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.components.ScrollablePanel;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.editor.EditorContextCapture;
import github.anandb.netbeans.ui.platform.SessionService;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.UIBundle;

// DSL-LEAF: not a controller — static factory that builds the welcome/session
// list view. The DSL migration ports build to WelcomeSpec; the
// SessionControl.lookup calls move to PlatformBridge.SessionService (see MIGRATION.md Phase 4).
public final class WelcomeScreen {

    private static final Logger LOG = Logger.from(WelcomeScreen.class);

    /** Horizontal gutter shared by every band, so the blocks line up. */
    private static final int GUTTER = 16;
    /** Breathing room above the first band. */
    private static final int TOP_STRUT = 24;
    /** Space between two bands. */
    private static final int SECTION_STRUT = 20;

    private WelcomeScreen() {}

    private static SessionService sessionService() {
        var bridge = PlatformBridge.get();
        if (bridge == null) {
            LOG.severe("PlatformBridge not found in Lookup — WelcomeScreen may display stale data");
            return null;
        }
        return bridge.sessionService();
    }

    public static void show(JPanel messagesContainer, List<Session> sessions,
                            Consumer<String> onSessionSelected, Runnable onNewChat) {
        show(messagesContainer, sessions, onSessionSelected, onNewChat, null);
    }

    public static void show(JPanel messagesContainer, List<Session> sessions,
                            Consumer<String> onSessionSelected, Runnable onNewChat,
                            Consumer<String> onStarterPrompt) {
        messagesContainer.removeAll();

        boolean firstRun = sessions == null || sessions.isEmpty();
        ColorTheme theme = ThemeManager.getCurrentTheme();
        // Let the container fill the viewport while its content is shorter than the
        // viewport, so the support line can sit at the bottom instead of floating.
        if (messagesContainer instanceof ScrollablePanel) {
            messagesContainer.putClientProperty(ScrollablePanel.TRACK_HEIGHT_IF_SHORTER, Boolean.TRUE);
        }

        messagesContainer.add(Box.createVerticalStrut(TOP_STRUT));
        addHeader(messagesContainer, theme, firstRun);

        if (!firstRun) {
            messagesContainer.add(Box.createVerticalStrut(SECTION_STRUT));
            addRecentSection(messagesContainer, theme, sessions, onSessionSelected);
        }

        messagesContainer.add(Box.createVerticalStrut(SECTION_STRUT));
        addStartAction(messagesContainer, theme, onNewChat);

        if (onStarterPrompt != null) {
            messagesContainer.add(Box.createVerticalStrut(SECTION_STRUT));
            addStarterPrompts(messagesContainer, theme, onStarterPrompt);
        }

        messagesContainer.add(Box.createVerticalGlue());

        messagesContainer.revalidate();
        // Wrapping JTextArea needs a real width before its preferred height is correct;
        // the first pass can size it one line short (same fix as MessageBubble).
        SwingUtilities.invokeLater(() -> {
            messagesContainer.revalidate();
            messagesContainer.repaint();
        });
    }

    /** Icon, title and the one-line promise. The promise is the only body copy a
     *  first-run user reads, so it stays at one sentence. */
    private static void addHeader(JPanel container, ColorTheme theme, boolean firstRun) {
        JLabel icon = new JLabel(ThemeManager.getIcon("logo.svg", 36));
        icon.setAlignmentX(Component.LEFT_ALIGNMENT);
        icon.setBorder(BorderFactory.createEmptyBorder(0, GUTTER, 10, GUTTER));
        container.add(icon);

        JLabel title = new JLabel(firstRun
            ? NbBundle.getMessage(UIBundle.class, "LBL_WelcomeToACP")
            : NbBundle.getMessage(UIBundle.class, "LBL_WelcomeBack"));
        title.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, titleSize()));
        title.setForeground(theme.foreground());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(0, GUTTER, 4, GUTTER));
        container.add(title);

        JTextArea valueProp = createWrappedText(NbBundle.getMessage(UIBundle.class, "LBL_ValueProp"),
            theme.mutedForeground(), 0, 0);
        valueProp.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, bodySize()));
        container.add(valueProp);
    }

    /** The recent list, which returning users should see before the CTA. */
    private static void addRecentSection(JPanel container, ColorTheme theme, List<Session> sessions,
                                         Consumer<String> onSessionSelected) {
        SessionService svc = sessionService();
        if (svc == null) {
            return;
        }
        boolean showHidden = ChatLayoutBuilder.isShowingHidden();
        List<Session> visible = sessions.stream()
            .filter(s -> showHidden || !svc.get().isHidden(s.id()))
            .toList();
        if (visible.isEmpty()) {
            return;
        }

        container.add(sectionLabel(NbBundle.getMessage(UIBundle.class, "LBL_RecentChats"), theme));
        for (Session s : visible) {
            String fallback = NbBundle.getMessage(UIBundle.class, "LBL_ChatDefault", left(s.id(), 8));
            String title = defaultIfBlank(s.title(), fallback);
            String label = svc.get().getCustomTitle(s.id(), title);
            JButton sessionBtn = createSelectionButton(label, s.effectiveDirectory());
            sessionBtn.addActionListener(e -> onSessionSelected.accept(s.id()));
            container.add(sessionBtn);
            container.add(Box.createVerticalStrut(6));
        }
    }

    /** The primary action: a filled button rather than a bordered list row, plus the
     *  nudge that the composer below works too. */
    private static void addStartAction(JPanel container, ColorTheme theme, Runnable onNewChat) {
        JButton newChatBtn = createPrimaryButton(NbBundle.getMessage(UIBundle.class, "BTN_StartNewChat"), theme);
        newChatBtn.addActionListener(e -> onNewChat.run());

        // The gutter lives on the wrapper: the button's own background would otherwise
        // paint edge to edge and break the alignment of every other band.
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(0, GUTTER, 0, GUTTER));
        row.add(newChatBtn, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, newChatBtn.getPreferredSize().height));
        container.add(row);
    }

    /** Example prompts. They fill the composer and start the chat: a welcome screen
     *  has no session to type into, so a chip that only fills the box would be a dead end. */
    private static void addStarterPrompts(JPanel container, ColorTheme theme,
                                          Consumer<String> onStarterPrompt) {
        container.add(sectionLabel(NbBundle.getMessage(UIBundle.class, "LBL_TryOneOfThese"), theme));

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.setBorder(BorderFactory.createEmptyBorder(0, GUTTER - 4, 0, GUTTER - 4));

        Color accentTint = theme.mutedForeground();
        addStarterChip(chips, theme, WelcomeAccentIcons.bulb(accentTint, 14),
            "BTN_StarterExplain", "LBL_PromptExplain", "LBL_PromptExplainProject", onStarterPrompt);
        addStarterChip(chips, theme, WelcomeAccentIcons.magnifier(accentTint, 14),
            "BTN_StarterFindBug", "LBL_PromptFindBug", "LBL_PromptFindBugProject", onStarterPrompt);
        addStarterChip(chips, theme, WelcomeAccentIcons.checkBox(accentTint, 14),
            "BTN_StarterWriteTest", "LBL_PromptWriteTest", "LBL_PromptWriteTestProject", onStarterPrompt);
        addStarterChip(chips, theme, WelcomeAccentIcons.diff(accentTint, 14),
            "BTN_StarterReview", "LBL_PromptReview", null, onStarterPrompt);
        container.add(chips);
    }

    private static void addStarterChip(JPanel chips, ColorTheme theme, Icon accent, String labelKey,
                                       String promptKey, String projectPromptKey,
                                       Consumer<String> onStarterPrompt) {
        JButton chip = createChipButton(NbBundle.getMessage(UIBundle.class, labelKey), accent, theme);
        chip.addActionListener(e -> onStarterPrompt.accept(starterPrompt(promptKey, projectPromptKey)));
        chips.add(chip);
    }

    /** A file-scoped prompt needs an open editor; without one the project-scoped prompt is used. */
    private static String starterPrompt(String promptKey, String projectPromptKey) {
        boolean hasFile = EditorContextCapture.capture() != null;
        String key = hasFile || projectPromptKey == null ? promptKey : projectPromptKey;
        return NbBundle.getMessage(UIBundle.class, key);
    }

    /** Small muted section heading ("Recent", "Try one of these"). */
    private static JLabel sectionLabel(String text, ColorTheme theme) {
        JLabel label = createMutedLabel(text, theme);
        label.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, smallSize()));
        label.setBorder(BorderFactory.createEmptyBorder(0, GUTTER, 6, GUTTER));
        return label;
    }

    /**
     * Word-wrapping body line. A JLabel never reflows, so long welcome copy would clip at the
     * panel edge; JTextArea wraps once BoxLayout gives it the container width. Max height stays
     * locked to preferred so the Y-axis layout cannot stretch it into a gap.
     */
    private static JTextArea createWrappedText(String text, Color color, int padTop, int padBottom) {
        JTextArea area = new JTextArea(text) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }

            @Override
            public float getAlignmentX() {
                return Component.LEFT_ALIGNMENT;
            }
        };
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFocusable(false);
        area.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
        area.setForeground(color);
        area.setBorder(BorderFactory.createEmptyBorder(padTop, GUTTER, padBottom, GUTTER));
        return area;
    }

    private static JLabel createMutedLabel(String text, ColorTheme theme) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, smallSize()));
        label.setForeground(theme.mutedForeground());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Filled accent button: the one element on the screen allowed to shout. */
    private static JButton createPrimaryButton(String text, ColorTheme theme) {
        Color accent = theme.accent() != null ? theme.accent() : new Color(66, 133, 244);
        Color foreground = contrastingForeground(accent);

        // Paint the fill ourselves: several NetBeans L&Fs ignore setBackground on JButton.
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        btn.setIcon(WelcomeAccentIcons.sparkle(foreground, 16));
        btn.setIconTextGap(8);
        btn.setBackground(accent);
        btn.setForeground(foreground);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, btn.getPreferredSize().height));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(accent.brighter());
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(accent);
                btn.repaint();
            }
        });
        return btn;
    }

    /** Quiet pill for an example prompt: secondary to the CTA, still obviously clickable. */
    private static JButton createChipButton(String text, Icon accent, ColorTheme theme) {
        JButton chip = new JButton(text);
        chip.setIcon(accent);
        chip.setIconTextGap(6);
        chip.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, smallSize()));
        chip.setForeground(theme.foreground());
        chip.setBackground(theme.panelHeader() != null ? theme.panelHeader() : theme.background());
        chip.setOpaque(true);
        chip.setContentAreaFilled(true);
        chip.setFocusPainted(false);
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(softBorder(theme), 1, true),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                Color accent = theme.accent() != null ? theme.accent() : Color.LIGHT_GRAY;
                chip.setForeground(accent);
                chip.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                chip.setForeground(theme.foreground());
                chip.repaint();
            }
        });
        return chip;
    }

    private static Color softBorder(ColorTheme theme) {
        Color border = theme.bubbleBorder();
        return border != null ? border : Color.LIGHT_GRAY;
    }

    private static Color contrastingForeground(Color background) {
        double luminance = 0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue();
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private static float titleSize() {
        return ThemeManager.getFont().getSize2D() + 6f;
    }

    private static float bodySize() {
        return ThemeManager.getFont().getSize2D() + 1f;
    }

    private static float smallSize() {
        return Math.max(11f, ThemeManager.getFont().getSize2D() - 1f);
    }

    private static JButton createSelectionButton(String text, String subtext) {
        ColorTheme theme = ThemeManager.getCurrentTheme();

        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(8, 0));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, GUTTER, 0, GUTTER),
                BorderFactory.createLineBorder(softBorder(theme), 1, true)),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)
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

        if (isNotBlank(subtext)) {
            String folder = new File(subtext).getName();
            JLabel subLabel = new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_InFolder", folder));
            subLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
            subLabel.setForeground(ThemeManager.getCurrentTheme().mutedForeground());
            textPanel.add(subLabel);
        }

        btn.add(textPanel, BorderLayout.CENTER);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(btn.getPreferredSize().height, 60)));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setOpaque(true);
                btn.setBackground(ThemeManager.getCurrentTheme().panelHeader());
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
