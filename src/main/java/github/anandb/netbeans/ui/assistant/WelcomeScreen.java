package github.anandb.netbeans.ui.assistant;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.left;

import org.openide.util.NbBundle;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.support.AppLinks;
import github.anandb.netbeans.support.BrowserUtils;
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

    /** Gap between the card and the thread surface around it. */
    private static final int OUTER = 14;
    /** Card content padding. */
    private static final int PAD_X = 26;
    private static final int PAD_Y = 22;
    /** Gap between the two columns/rows of starter cards. */
    private static final int CARD_GAP = 14;
    /** Breathing room between the greeting header and the hero heading. */
    private static final int HERO_TOP_GAP = 72;
    /** Below this content width the starter cards stack in one column instead of two. */
    private static final int TWO_COLUMN_MIN_WIDTH = 520;
    private static final int CARD_RADIUS = 8;
    private static final int CARD_PAD = 16;
    private static final int CARD_ICON = 16;

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
        // viewport, so the footer can sit at the bottom instead of floating.
        if (messagesContainer instanceof ScrollablePanel) {
            messagesContainer.putClientProperty(ScrollablePanel.TRACK_HEIGHT_IF_SHORTER, Boolean.TRUE);
        }

        int width = messagesContainer.getWidth() - 2 * (OUTER + PAD_X);
        int columns = width <= 0 || width >= TWO_COLUMN_MIN_WIDTH ? 2 : 1;
        messagesContainer.add(buildCard(theme, firstRun, sessions, onSessionSelected, onNewChat,
            onStarterPrompt, columns, width));

        messagesContainer.revalidate();
        // Wrapped copy needs a real width before its preferred height is correct; the
        // first pass can size it one line short, so revalidate once the card has its width.
        SwingUtilities.invokeLater(() -> {
            messagesContainer.revalidate();
            messagesContainer.repaint();
        });
    }

    /** One rounded card holding every band, painted on the thread surface. */
    private static JPanel buildCard(ColorTheme theme, boolean firstRun, List<Session> sessions,
                                    Consumer<String> onSessionSelected, Runnable onNewChat,
                                    Consumer<String> onStarterPrompt, int columns, int innerWidth) {
        Palette palette = palette(theme);
        JPanel card = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(palette.card());
                    g2.fillRoundRect(OUTER, OUTER, getWidth() - 2 * OUTER, getHeight() - 2 * OUTER,
                        12, 12);
                    g2.setColor(palette.border());
                    g2.drawRoundRect(OUTER, OUTER, getWidth() - 2 * OUTER, getHeight() - 2 * OUTER,
                        12, 12);
                } finally {
                    g2.dispose();
                }
            }
        };
        card.setLayout(new StackLayout());
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createEmptyBorder(
            OUTER + PAD_Y, OUTER + PAD_X, OUTER + PAD_Y, OUTER + PAD_X));

        addHeader(card, palette, firstRun);
        card.add(Box.createVerticalStrut(HERO_TOP_GAP));
        JComponent subtitle = addHero(card, palette, onNewChat);
        card.add(Box.createVerticalStrut(24));

        if (!firstRun) {
            card.add(sectionLabel(NbBundle.getMessage(UIBundle.class, "LBL_RecentChats"), palette));
            addRecentSection(card, palette, sessions, onSessionSelected);
            card.add(Box.createVerticalStrut(22));
        }

        if (onStarterPrompt != null) {
            card.add(sectionLabel(NbBundle.getMessage(UIBundle.class, "LBL_JumpRightIn"), palette));
            card.add(Box.createVerticalStrut(10));
            addStarterCards(card, palette, columns, onStarterPrompt);
            card.add(Box.createVerticalStrut(24));
        }

        card.add(rule(palette));
        card.add(Box.createVerticalStrut(12));
        JComponent hint = addHarnessSelectionHint(card, palette);
        card.add(verticalGlue());
        card.add(Box.createVerticalStrut(20));
        card.add(rule(palette));
        card.add(Box.createVerticalStrut(12));
        addFooter(card, palette);
        applyWrapWidth(innerWidth, subtitle, hint);
        card.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyWrapWidth(card.getWidth() - 2 * (OUTER + PAD_X), subtitle, hint);
            }
        });
        return card;
    }

    /** Keep the wrapped copy in step with the card width, before and during resize. */
    private static void applyWrapWidth(int inner, JComponent subtitle, JComponent hint) {
        if (inner <= 0) {
            return;
        }
        if (subtitle instanceof WrappingText text) {
            text.setWrapWidth(inner);
        }
        if (hint instanceof WrappingText text) {
            text.setWrapWidth(inner - 22);
        }
    }

    /** Sprout mark, title and the one-line promise. */
    private static void addHeader(JPanel container, Palette palette, boolean firstRun) {
        JLabel badge = new JLabel(ThemeManager.getIcon("sprout.svg", 42));
        badge.setAlignmentY(Component.TOP_ALIGNMENT);
        badge.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);

        JLabel title = new JLabel(firstRun
            ? NbBundle.getMessage(UIBundle.class, "LBL_WelcomeToACP")
            : NbBundle.getMessage(UIBundle.class, "LBL_WelcomeBack"));
        title.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, titleSize()));
        title.setForeground(palette.heading());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(Box.createVerticalStrut(3));

        JLabel valueProp = new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_ValueProp"));
        valueProp.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, bodySize()));
        valueProp.setForeground(palette.muted());
        valueProp.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(valueProp);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(badge, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
        container.add(header);
    }

    /** Big question, the one-line answer, and the primary action. */
    private static JComponent addHero(JPanel container, Palette palette, Runnable onNewChat) {
        JLabel heading = new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_WelcomeHeading"));
        heading.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, headingSize()));
        heading.setForeground(palette.heading());
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(heading);
        container.add(Box.createVerticalStrut(6));

        JComponent sub = new WrappingText(
            NbBundle.getMessage(UIBundle.class, "LBL_WelcomeSubheading"), palette.muted(), 0,
            ThemeManager.getFont().deriveFont(Font.PLAIN, bodySize()));
        container.add(sub);
        container.add(Box.createVerticalStrut(16));

        JButton newChatBtn = new PrimaryActionButton(
            NbBundle.getMessage(UIBundle.class, "BTN_StartNewChat"), palette);
        newChatBtn.addActionListener(e -> onNewChat.run());
        newChatBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        newChatBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, newChatBtn.getPreferredSize().height));
        container.add(newChatBtn);
        return sub;
    }

    /** The recent list, which returning users should see before they scroll. */
    private static void addRecentSection(JPanel container, Palette palette,
                                         List<Session> sessions, Consumer<String> onSessionSelected) {
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

        for (Session s : visible) {
            String fallback = NbBundle.getMessage(UIBundle.class, "LBL_ChatDefault", left(s.id(), 8));
            String title = defaultIfBlank(s.title(), fallback);
            String label = svc.get().getCustomTitle(s.id(), title);
            JButton sessionBtn = createSelectionButton(label, s.effectiveDirectory(), palette);
            sessionBtn.addActionListener(e -> onSessionSelected.accept(s.id()));
            container.add(sessionBtn);
            container.add(Box.createVerticalStrut(6));
        }
    }

    private static JComponent addHarnessSelectionHint(JPanel container, Palette palette) {
        JLabel info = new JLabel(WelcomeAccentIcons.info(palette.muted(), 14));
        info.setVerticalAlignment(JLabel.TOP);

        JComponent hint = new WrappingText(
            NbBundle.getMessage(UIBundle.class, "LBL_HarnessSelectionHint"), palette.muted(),
            22, ThemeManager.getFont().deriveFont(Font.PLAIN, smallSize()));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(info, BorderLayout.WEST);
        row.add(hint, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        container.add(row);
        return hint;
    }

    /** Funding nudge on the left, the project's independence note on the right. */
    private static void addFooter(JPanel container, Palette palette) {
        JButton support = new JButton(NbBundle.getMessage(UIBundle.class, "LBL_EnjoyingBeanBot")) {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        support.setIcon(ThemeManager.getIcon("sprout.svg", 14));
        support.setIconTextGap(8);
        support.setFont(underlined(ThemeManager.getFont().deriveFont(Font.PLAIN, smallSize())));
        support.setForeground(palette.muted());
        support.setContentAreaFilled(false);
        support.setBorderPainted(false);
        support.setFocusPainted(false);
        support.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        support.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        support.addActionListener(e -> BrowserUtils.openOrCopyUrl(
            AppLinks.supportUrl(), "STATUS_SupportUrlCopied", null));
        support.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                support.setForeground(palette.accent());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                support.setForeground(palette.muted());
            }
        });

        JLabel developed = new JLabel(
            NbBundle.getMessage(UIBundle.class, "LBL_IndependentlyDeveloped"));
        developed.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, smallSize()));
        developed.setForeground(palette.muted());

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.add(support, BorderLayout.WEST);
        footer.add(developed, BorderLayout.EAST);
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, footer.getPreferredSize().height));
        container.add(footer);
    }

    /** Example prompts as cards. They fill the composer and start the chat: a welcome screen
     *  has no session to type into, so a card that only fills the box would be a dead end. */
    private static void addStarterCards(JPanel container, Palette palette,
                                        int columns, Consumer<String> onStarterPrompt) {
        JPanel grid = new JPanel(new GridLayout(0, columns, CARD_GAP, CARD_GAP));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        Color iconColor = palette.muted();
        addStarterCard(grid, palette, WelcomeAccentIcons.bulb(iconColor, CARD_ICON),
            "BTN_StarterExplain", "LBL_StarterExplainDesc",
            "LBL_PromptExplain", "LBL_PromptExplainProject", onStarterPrompt);
        addStarterCard(grid, palette, WelcomeAccentIcons.magnifier(iconColor, CARD_ICON),
            "BTN_StarterFindBug", "LBL_StarterFindBugDesc",
            "LBL_PromptFindBug", "LBL_PromptFindBugProject", onStarterPrompt);
        addStarterCard(grid, palette, WelcomeAccentIcons.checkBox(iconColor, CARD_ICON),
            "BTN_StarterWriteTest", "LBL_StarterWriteTestDesc",
            "LBL_PromptWriteTest", "LBL_PromptWriteTestProject", onStarterPrompt);
        addStarterCard(grid, palette, WelcomeAccentIcons.diff(iconColor, CARD_ICON),
            "BTN_StarterReview", "LBL_StarterReviewDesc",
            "LBL_PromptReview", null, onStarterPrompt);

        // Stack the cards on a narrow thread: two columns would clip the descriptions.
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        container.addComponentListener(new ComponentAdapter() {
            private int current = columns;

            @Override
            public void componentResized(ComponentEvent e) {
                int available = container.getWidth() - 2 * (OUTER + PAD_X);
                int wanted = available >= TWO_COLUMN_MIN_WIDTH ? 2 : 1;
                if (wanted == current) {
                    return;
                }
                current = wanted;
                grid.setLayout(new GridLayout(0, wanted, CARD_GAP, CARD_GAP));
                grid.setMaximumSize(new Dimension(
                    Integer.MAX_VALUE, grid.getPreferredSize().height));
                container.revalidate();
            }
        });

        container.add(grid);
    }

    private static void addStarterCard(JPanel grid, Palette palette, Icon accent, String labelKey,
                                       String descriptionKey, String promptKey, String projectPromptKey,
                                       Consumer<String> onStarterPrompt) {
        StarterCard card = new StarterCard(
            NbBundle.getMessage(UIBundle.class, labelKey),
            NbBundle.getMessage(UIBundle.class, descriptionKey), accent, palette);
        card.setToolTipText(NbBundle.getMessage(UIBundle.class, labelKey));
        card.addActionListener(e -> onStarterPrompt.accept(starterPrompt(promptKey, projectPromptKey)));
        grid.add(card);
    }

    /** A file-scoped prompt needs an open editor; without one the project-scoped prompt is used. */
    private static String starterPrompt(String promptKey, String projectPromptKey) {
        boolean hasFile = EditorContextCapture.capture() != null;
        String key = hasFile || projectPromptKey == null ? promptKey : projectPromptKey;
        return NbBundle.getMessage(UIBundle.class, key);
    }

    /** Small muted section heading ("Recent", "Or jump right in"). */
    private static JLabel sectionLabel(String text, Palette palette) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, bodySize()));
        label.setForeground(palette.heading());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return label;
    }

    private static JComponent rule(Palette palette) {
        JComponent line = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(0, 1);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 1);
            }

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(palette.rule());
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    /** Stacks children at their preferred height; one flagged glue child absorbs the slack. */
    private static final class StackLayout implements LayoutManager {
        private static final String GLUE = "welcome.glue";

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int width = 0;
            int height = 0;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) {
                    continue;
                }
                Dimension pref = child.getPreferredSize();
                width = Math.max(width, pref.width);
                if (!isGlue(child)) {
                    height += pref.height;
                }
            }
            return new Dimension(width + insets.left + insets.right,
                height + insets.top + insets.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = parent.getWidth() - insets.left - insets.right;
            int content = 0;
            for (Component child : parent.getComponents()) {
                if (child.isVisible() && !isGlue(child)) {
                    content += child.getPreferredSize().height;
                }
            }
            int slack = Math.max(0,
                parent.getHeight() - insets.top - insets.bottom - content);
            int y = insets.top;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) {
                    continue;
                }
                int height = isGlue(child) ? slack : child.getPreferredSize().height;
                child.setBounds(insets.left, y, width, height);
                y += height;
            }
        }

        private static boolean isGlue(Component child) {
            return child instanceof JComponent
                && Boolean.TRUE.equals(((JComponent) child).getClientProperty(GLUE));
        }
    }

    private static JComponent verticalGlue() {
        JComponent glue = (JComponent) Box.createVerticalGlue();
        glue.putClientProperty(StackLayout.GLUE, Boolean.TRUE);
        return glue;
    }

    /**
     * Word-wrapping body line. Neither a JLabel nor a JTextArea wraps in time during the first
     * layout pass, so long copy clipped to one line; this leaf paints the wrapped text itself and
     * derives its height from the width its parent actually gives it.
     */
    private static final class WrappingText extends JComponent implements Accessible {
        private static final long serialVersionUID = 1L;

        private final String text;
        private final Color color;
        private final int reserve;
        private int wrapWidth;

        WrappingText(String text, Color color, int reserve, Font font) {
            this.text = text;
            this.color = color;
            this.reserve = reserve;
            setFont(font);
            setForeground(color);
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            // Painted text is invisible to assistive technology unless the
            // component exposes it; announce it as the accessible name.
            getAccessibleContext().setAccessibleName(text);
        }

        /** {@link JComponent} has no context of its own; a Swing leaf must create one. */
        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleWrappingText();
            }
            return accessibleContext;
        }

        private final class AccessibleWrappingText extends AccessibleJComponent {
            private static final long serialVersionUID = 1L;
        }

        /** Width the copy wraps at; the card sets it before and during layout. */
        void setWrapWidth(int width) {
            if (width != wrapWidth) {
                wrapWidth = width;
                revalidate();
            }
        }

        private int availableWidth() {
            if (wrapWidth > 0) {
                return wrapWidth - reserve;
            }
            int width = getWidth();
            if (width <= 0) {
                Container parent = getParent();
                if (parent == null) {
                    return 0;
                }
                width = parent.getWidth() - parent.getInsets().left - parent.getInsets().right;
            }
            return width > 0 ? width - reserve : 0;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int width = availableWidth();
            if (width <= 0) {
                return new Dimension(fm.stringWidth(text), fm.getHeight());
            }
            return new Dimension(width, wrap(text, width, fm).size() * fm.getHeight());
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                g2.setColor(color);
                FontMetrics fm = g2.getFontMetrics();
                List<String> lines = wrap(text, Math.max(0, getWidth() - reserve), fm);
                int y = fm.getAscent();
                for (String line : lines) {
                    g2.drawString(line, 0, y);
                    y += fm.getHeight();
                }
            } finally {
                g2.dispose();
            }
        }

        private static List<String> wrap(String text, int width, FontMetrics fm) {
            List<String> lines = new ArrayList<>();
            if (width <= 0) {
                lines.add(text);
                return lines;
            }
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && fm.stringWidth(candidate) > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines;
        }
    }

    /** Filled full-width action with the sparkle and a trailing arrow, painted by hand so the
     *  icon pair and the NetBeans L&amp;F cannot disagree about the shape. */
    private static final class PrimaryActionButton extends JButton {
        private static final long serialVersionUID = 1L;

        private final Palette palette;

        PrimaryActionButton(String text, Palette palette) {
            super(text);
            this.palette = palette;
            setFont(ThemeManager.getFont().deriveFont(Font.BOLD, bodySize() + 1f));
            setForeground(palette.accentFg());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setRolloverEnabled(true);
            setToolTipText(getText());
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(super.getPreferredSize().width, fm.getHeight() + 24);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getModel().isRollover()
                    ? blend(palette.accent(), Color.WHITE, 0.12) : palette.accent();
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), CARD_RADIUS, CARD_RADIUS);

                Icon sparkle = WelcomeAccentIcons.sparkle(palette.accentFg(), 16);
                Icon arrow = WelcomeAccentIcons.arrowRight(palette.accentFg(), 16);
                FontMetrics fm = g2.getFontMetrics(getFont());
                String text = getText();
                int gap = 8;
                int total = sparkle.getIconWidth() + gap + fm.stringWidth(text)
                    + gap + arrow.getIconWidth();
                int x = Math.max(0, (getWidth() - total) / 2);
                int iconY = (getHeight() - sparkle.getIconHeight()) / 2;
                sparkle.paintIcon(this, g2, x, iconY);
                g2.setFont(getFont());
                g2.setColor(palette.accentFg());
                g2.drawString(text, x + sparkle.getIconWidth() + gap,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                arrow.paintIcon(this, g2,
                    x + sparkle.getIconWidth() + gap + fm.stringWidth(text) + gap, iconY);

                if (hasFocus()) {
                    g2.setColor(palette.accentFg());
                    g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, CARD_RADIUS, CARD_RADIUS);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** A quiet starter prompt card: icon and title on the first line, description below. */
    private static final class StarterCard extends JButton {
        private static final long serialVersionUID = 1L;

        private final String description;
        private final Icon accent;
        private final Palette palette;
        private final int titleHeight;
        private final int descriptionHeight;

        StarterCard(String title, String description, Icon accent, Palette palette) {
            super(title);
            this.description = description;
            this.accent = accent;
            this.palette = palette;
            setFont(ThemeManager.getFont().deriveFont(Font.BOLD, bodySize() + 1f));
            setForeground(palette.heading());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder());
            setRolloverEnabled(true);
            // The description is painted, not a child component — expose it so
            // screen readers announce the whole card, not just the title.
            getAccessibleContext().setAccessibleDescription(description);
            FontMetrics fm = getFontMetrics(getFont());
            titleHeight = fm.getHeight();
            descriptionHeight = getFontMetrics(descriptionFont()).getHeight();
        }

        private Font descriptionFont() {
            return ThemeManager.getFont().deriveFont(Font.PLAIN, smallSize());
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics titleFm = getFontMetrics(getFont());
            FontMetrics descFm = getFontMetrics(descriptionFont());
            int width = CARD_PAD + CARD_ICON + 12 + titleFm.stringWidth(getText()) + CARD_PAD;
            width = Math.max(width,
                CARD_PAD + descFm.stringWidth(description) + CARD_PAD);
            return new Dimension(width, CARD_PAD + titleHeight + 6 + descriptionHeight + CARD_PAD);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(palette.cardInner());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CARD_RADIUS, CARD_RADIUS);
                g2.setColor(getModel().isRollover()
                    ? blend(palette.accent(), palette.border(), 0.45) : palette.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CARD_RADIUS, CARD_RADIUS);

                accent.paintIcon(this, g2, CARD_PAD, CARD_PAD + (titleHeight - CARD_ICON) / 2);
                FontMetrics titleFm = g2.getFontMetrics(getFont());
                g2.setFont(getFont());
                g2.setColor(palette.heading());
                g2.drawString(getText(), CARD_PAD + CARD_ICON + 12,
                    CARD_PAD + titleFm.getAscent());

                g2.setFont(descriptionFont());
                g2.setColor(palette.muted());
                g2.drawString(description, CARD_PAD,
                    CARD_PAD + titleHeight + 6 + g2.getFontMetrics().getAscent());

                if (hasFocus()) {
                    g2.setColor(palette.accent());
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, CARD_RADIUS, CARD_RADIUS);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private static JButton createSelectionButton(String text, String subtext, Palette palette) {
        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(8, 0));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, 0),
                BorderFactory.createLineBorder(palette.border(), 1, true)),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
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
            subLabel.setForeground(palette.muted());
            textPanel.add(subLabel);
        }

        btn.add(textPanel, BorderLayout.CENTER);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(btn.getPreferredSize().height, 54)));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setOpaque(true);
                btn.setBackground(palette.cardInner());
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

    /** Colours derived from the active theme, so light mode gets the same design. */
    private record Palette(Color card, Color cardInner, Color border, Color rule, Color heading,
                           Color muted, Color accent, Color accentFg) {}

    /** Warm beige the light card leans towards; the dark card stays theme-warm. */
    private static final Color WARM_CARD = new Color(0xEB, 0xE1, 0xC3);

    private static Palette palette(ColorTheme theme) {
        boolean dark = theme.isDark();
        Color fg = theme.foreground() != null ? theme.foreground() : (dark ? Color.WHITE : Color.BLACK);
        Color warmCard = theme.bubbleAssistant() != null
            ? theme.bubbleAssistant() : new Color(0x333029);
        Color card = dark
            ? blend(warmCard, Color.BLACK, 0.12)
            : blend(blend(Color.WHITE, warmCard, 0.35), WARM_CARD, 0.5);
        Color inner = dark ? blend(card, Color.WHITE, 0.035) : blend(card, Color.WHITE, 0.6);
        Color border = blend(card, fg, dark ? 0.13 : 0.16);
        Color rule = blend(card, fg, dark ? 0.14 : 0.16);
        Color muted = theme.mutedForeground() != null
            ? theme.mutedForeground() : blend(fg, card, 0.45);
        Color accent = new Color(0x1F6FEB);
        return new Palette(card, inner, border, rule, fg, muted, accent,
            contrastingForeground(accent));
    }

    private static Color blend(Color a, Color b, double ratio) {
        double r = Math.max(0, Math.min(1, ratio));
        return new Color(
            (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * r),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * r),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * r));
    }

    private static Font underlined(Font font) {
        return font.deriveFont(Map.of(
            TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
    }

    private static Color contrastingForeground(Color background) {
        double luminance = 0.299 * background.getRed() + 0.587 * background.getGreen()
            + 0.114 * background.getBlue();
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private static float titleSize() {
        return ThemeManager.getFont().getSize2D() + 8f;
    }

    private static float headingSize() {
        return ThemeManager.getFont().getSize2D() + 12f;
    }

    private static float bodySize() {
        return ThemeManager.getFont().getSize2D() + 1f;
    }

    private static float smallSize() {
        return Math.max(11f, ThemeManager.getFont().getSize2D() - 1f);
    }
}
