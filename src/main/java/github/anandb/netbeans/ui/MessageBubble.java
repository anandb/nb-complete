package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.Timer;

import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.length;


import github.anandb.netbeans.ui.TableDetector.Segment;
import github.anandb.netbeans.ui.TableDetector.TableResult;
import github.anandb.netbeans.ui.TableDetector.TextSegment;
import github.anandb.netbeans.ui.TableDetector.TableSegment;


public class MessageBubble extends JPanel implements Scrollable {

    private static final Logger LOG = Logger.from(MessageBubble.class);
    private static final long serialVersionUID = 1L;

    /** Determines where the user avatar is positioned relative to the message bubble. */
    enum AvatarPosition { LEFT, RIGHT, NONE }

    // Static cached Pattern for code block parsing - compiled once
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```([\\w\\-\\+\\#\\.]*)\\R?(.*?)(?:```(?=\\R|$)|$)", Pattern.DOTALL
    );

    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final MessageType type;
    private final String role;
    private final String messageId;
    private final StringBuilder text;
    private String toolTitle;
    private final JPanel segments;
    private JPanel bubble;
    private final ArrayList<CollapsibleState> codeStates = new ArrayList<>();
    private transient HierarchyListener hierarchyListener;
    private long responseTimeMs = -1L;
    /** Tracks how much of the text has already been displayed (for incremental streaming updates). */
    private int lastDisplayedLength = 0;
    private boolean isStreaming = false;
    private JTextArea streamingTextArea;
    /** True when finalizeStreaming() was called but HTML rebuild is deferred
     *  (waiting for more deltas).  The streaming text area is still visible. */
    private boolean isFinalizingDeferred = false;
    private Timer deferredFinalizeTimer;
    /** Saved expanded state for when deferred finalization fires after the
     *  caller's scope has exited. */
    private boolean savedCollapseState;

    private static class CollapsibleState {
        boolean expanded;

        CollapsibleState(boolean expanded) {
            this.expanded = expanded;
        }
    }

    /**
     * Apply background color and RoundedPanel base color for a message bubble.
     *
     * @param theme the current theme
     * @param type  the message type (user, error, assistant, tool, thought)
     */
    private void applyBubbleTheme(ColorTheme theme, String type) {
        Color bgColor;
        if (null == type) {
            bgColor = TRANSPARENT;
        } else bgColor = switch (type) {
            case "assistant" -> theme.bubbleAssistant();
            case "user" -> theme.bubbleUser();
            case "error" -> theme.errorBackground();
            default -> TRANSPARENT;
        };

        setBackground(theme.sunkenBackground());
        setOpaque(true);

        bubble.setBackground(bgColor);
        bubble.setOpaque(true);
        segments.setBackground(bgColor);
        segments.setOpaque(false);

        if (bubble instanceof RoundedPanel rp) {
            rp.setBaseColor(bgColor);
            rp.setOpaque(false);
        }
    }

    @Override
    public float getAlignmentX() {
        return Component.LEFT_ALIGNMENT;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public MessageBubble(MessageType type, String text, String messageId, String toolTitle, AvatarPosition avatarPosition) {
        this(type, text, messageId, toolTitle, avatarPosition, false);
    }

    public MessageBubble(MessageType type, String text, String messageId, String toolTitle, AvatarPosition avatarPosition, boolean streaming) {
        this.type = type;
        this.role = type.roleName();
        this.messageId = messageId;
        this.text = new StringBuilder(text);
        this.toolTitle = toolTitle;

        ColorTheme theme = ThemeManager.getCurrentTheme();
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(theme.sunkenBackground());
        setDoubleBuffered(true);
        setBorder(new EmptyBorder(2, 8, 2, 8));

        segments = new JPanel();
        segments.setLayout(new BoxLayout(segments, BoxLayout.Y_AXIS));
        segments.setAlignmentX(Component.LEFT_ALIGNMENT);
        segments.setDoubleBuffered(true);

        this.bubble = new JPanel(new BorderLayout());
        this.bubble.setDoubleBuffered(true);
        if ("user".equals(role)) {
            RoundedPanel p = new RoundedPanel(16);
            p.setLayout(new BorderLayout());
            p.setBorder(new EmptyBorder(10, 8, 10, 8));
            this.bubble = p;
        } else if ("assistant".equals(role)) {
            RoundedPanel p = new RoundedPanel(16);
            p.setLayout(new BorderLayout());
            p.setBorder(new EmptyBorder(8, 10, 8, 10));
            this.bubble = p;
        } else {
            this.bubble.setBorder(new EmptyBorder(2, 8, 2, 8));
        }
        this.bubble.add(segments, BorderLayout.CENTER);

        this.isStreaming = "assistant".equals(role) && streaming;
        if (this.isStreaming) {
            streamingTextArea = createStreamingTextArea(theme, text);
            segments.add(streamingTextArea);
            lastDisplayedLength = this.text.length();
        } else {
            updateContent(theme, false);
        }
        boolean isAssistant = "assistant".equals(role);

        Insets gbcInsets = new Insets(4, 12, 4, 12);
        int anchor = isAssistant ? GridBagConstraints.NORTHWEST : GridBagConstraints.WEST;
        int fill = GridBagConstraints.HORIZONTAL;

        applyBubbleTheme(theme, role);

        if (!"user".equals(role)) {
            // AI messages use full width
            gbcInsets.left = 12;
            gbcInsets.right = 12;
        }

        // Copy button for assistant messages at bottom right — visible only on hover
        if ("assistant".equals(role)) {
            final JButton[] copyBtn = new JButton[1];
            copyBtn[0] = UIUtils.createToolbarButton("copy.svg", 20,
                NbBundle.getMessage(MessageBubble.class, "HINT_CopyAssistantMessage"),
                e -> copyMessageToClipboard(copyBtn[0]));
            copyBtn[0].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.bubbleBorder()),
                    BorderFactory.createEmptyBorder(1, 2, 1, 2)));
            copyBtn[0].setContentAreaFilled(false);
            copyBtn[0].setOpaque(false);
            copyBtn[0].setForeground(theme.foreground());
            copyBtn[0].setVisible(false);

            // Reserve space in SOUTH with a fixed-size placeholder so the
            // layout never shifts when toggling button visibility (avoids jitter).
            JPanel copyPlaceholder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            copyPlaceholder.setOpaque(false);
            Dimension btnSize = copyBtn[0].getPreferredSize();
            copyPlaceholder.setPreferredSize(btnSize);
            copyPlaceholder.setMinimumSize(btnSize);
            copyPlaceholder.setMaximumSize(btnSize);
            copyPlaceholder.add(copyBtn[0]);
            bubble.add(copyPlaceholder, BorderLayout.SOUTH);

            // Show copy button + frame on hover over the bubble.
            // mouseExited only hides when truly leaving the bubble, not when
            // entering a child (otherwise the button vanishes on approach).
            bubble.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    copyBtn[0].setVisible(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!bubble.contains(e.getPoint())) {
                        copyBtn[0].setVisible(false);
                    }
                }
            });
        }

        if ("user".equals(role) && avatarPosition != AvatarPosition.NONE) {
            // Wrap bubble + external avatar in a content row
            JPanel contentRow = new JPanel(new BorderLayout());
            contentRow.setOpaque(false);
            contentRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel userLabel = createUserAvatar();
            JPanel avatarWrapper = UIUtils.createTransparentPanel(new BorderLayout());
            avatarWrapper.add(userLabel, BorderLayout.NORTH);

            if (avatarPosition == AvatarPosition.LEFT) {
                contentRow.add(avatarWrapper, BorderLayout.WEST);
            } else {
                contentRow.add(avatarWrapper, BorderLayout.EAST);
            }
            contentRow.add(bubble, BorderLayout.CENTER);
            add(contentRow, UIUtils.createGbc(0, 0, 1.0, 0, fill, anchor, gbcInsets));
        } else {
            add(bubble, UIUtils.createGbc(0, 0, 1.0, 0, fill, anchor, gbcInsets));
        }

        // Fix for "garbled" text in scroll panes: force a repaint when the component becomes visible
        if ("user".equals(role)) {
            this.hierarchyListener = e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                    SwingUtilities.invokeLater(() -> {
                        repaint();
                        bubble.repaint();
                        segments.repaint();
                    });
                }
            };
            addHierarchyListener(this.hierarchyListener);
        }
    }

    /**
     * Creates the user avatar label with click-to-copy behavior.
     * Used when the avatar is positioned outside the bubble (LEFT or RIGHT).
     */
    private JLabel createUserAvatar() {
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
            messageId, role, this
        ));
        return userLabel;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (hierarchyListener != null) {
            removeHierarchyListener(hierarchyListener);
            hierarchyListener = null;
        }
        // Stop deferred finalization timer to prevent phantom UI updates
        // after the bubble is removed from the component tree.
        if (deferredFinalizeTimer != null && deferredFinalizeTimer.isRunning()) {
            deferredFinalizeTimer.stop();
        }
    }

    private volatile boolean hasPendingTextUpdate = false;

    public void appendText(String newText) {
        appendText(newText, "");
    }

    public void appendText(String newText, String toolTitle) {
        if (newText == null || newText.isEmpty()) {
            return;
        }

        this.text.append(newText);
        this.toolTitle = length(this.toolTitle) < length(toolTitle) ? toolTitle : this.toolTitle;
        hasPendingTextUpdate = true;

        // Deferred finalization is in flight — new data arrived, so keep
        // the bubble in streaming mode and reset the cooldown timer.
        if (isFinalizingDeferred && deferredFinalizeTimer != null) {
            deferredFinalizeTimer.restart();
        }
    }

    public boolean flushUpdate() {
        return flushUpdate(false);
    }

    public boolean flushUpdate(boolean force) {
        if (!hasPendingTextUpdate && !force) {
            return false;
        }
        hasPendingTextUpdate = false;

        // Use the fast streaming path for both actively-streaming bubbles
        // and deferred-finalization bubbles (still have a visible JTextArea).
        if (isStreaming || isFinalizingDeferred) {
            if (streamingTextArea != null && text.length() > lastDisplayedLength) {
                String delta = text.substring(lastDisplayedLength);
                javax.swing.text.Document doc = streamingTextArea.getDocument();
                try {
                    doc.insertString(doc.getLength(), delta, null);
                } catch (javax.swing.text.BadLocationException ignored) {
                    streamingTextArea.append(delta);
                }
                lastDisplayedLength = text.length();
                return true;
            }
            return false;
        }

        updateContent(ThemeManager.getCurrentTheme(), true);
        return true;
    }

    /**
     * Registers the tool/activity pane inside this bubble with an accordion group,
     * so it participates in cross-bubble accordion behavior.
     * No-op if this bubble does not contain a tool/thought pane.
     */
    public void registerWithAccordionGroup(AccordionGroup group) {
        if (segments.getComponentCount() > 0) {
            Component first = segments.getComponent(0);
            if (first instanceof CollapsibleToolPane toolPane) {
                toolPane.setAccordionGroup(group);
            } else if (first instanceof CollapsibleActivityPane activityPane) {
                activityPane.setAccordionGroup(group);
            }
        }
    }

    public void setExpanded(boolean expanded) {
        if (segments.getComponentCount() > 0) {
            Component first = segments.getComponent(0);
            if (first instanceof CollapsibleToolPane pane) {
                pane.setExpanded(expanded);
            } else if (first instanceof CollapsibleActivityPane pane) {
                pane.setExpanded(expanded);
            }
        }
    }

    public void toggleAllBlocks(boolean expanded) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleCodePane codePane) {
                codePane.setExpanded(expanded);
            } else if (c instanceof CollapsibleToolPane toolPane) {
                toolPane.setExpanded(expanded);
            } else if (c instanceof CollapsibleActivityPane activityPane) {
                activityPane.setExpanded(expanded);
            }
        }
    }

    public String getRole() {
        return role;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getToolTitle() {
        return toolTitle;
    }

    public String getRawText() {
        return text.toString();
    }

    /** Returns true if this bubble is still in streaming mode (not yet converted to HTML)
     *  or if finalization has been deferred pending a cooldown period. */
    public boolean isStreaming() {
        return isStreaming || isFinalizingDeferred;
    }

    public void setResponseTimeMs(long ms) {
        if (ms <= 0) return;
        this.responseTimeMs = ms;
        String label = formatElapsed(ms);
        JLabel ttftLabel = new JLabel(label);
        ttftLabel.setToolTipText("Time to First Token: " + label);
        ttftLabel.setFont(ThemeManager.getFont().deriveFont(10f));
        ttftLabel.setForeground(Color.GRAY);
        ttftLabel.setBorder(new EmptyBorder(0, 0, 0, 12));
        add(ttftLabel, UIUtils.createGbc(0, 1, 1.0, 0,
                GridBagConstraints.NONE, GridBagConstraints.SOUTHEAST,
                new Insets(0, 12, 2, 12)));
        revalidate();
    }

    private static String formatElapsed(long ms) {
        if (ms < 10000) return String.format("%.1fs", ms / 1000.0);
        if (ms < 60000) return String.format("%ds", ms / 1000);
        long mins = ms / 60000;
        long secs = (ms % 60000) / 1000;
        return String.format("%dm %ds", mins, secs);
    }

    private JTextArea createStreamingTextArea(ColorTheme theme, String initialText) {
        JTextArea ta = new JTextArea(initialText) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Short.MAX_VALUE, pref.height);
            }

            @Override
            public float getAlignmentX() {
                return Component.LEFT_ALIGNMENT;
            }
        };
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setOpaque(false);
        ta.setBackground(TRANSPARENT);
        ta.setForeground(theme.assistantForeground());
        ta.setFont(ThemeManager.getFont());
        ta.setBorder(new EmptyBorder(4, 20, 4, 6));
        ta.setCaretPosition(ta.getDocument().getLength());
        return ta;
    }

    /**
     * Handles incremental update of tool/thought content in the activity pane.
     * Reuses existing CollapsibleActivityPane or CollapsibleToolPane if present,
     * otherwise creates a new CollapsibleActivityPane.
     */
    private void handleToolThoughtContent(ColorTheme theme, boolean expanded) {
        String displayContent = text.toString();
        String title = toolTitle != null ? toolTitle : "Execution Steps";
        if (segments.getComponentCount() > 0) {
            Component first = segments.getComponent(0);
            if (first instanceof BaseCollapsiblePane pane) {
                updatePaneContent(pane, title, displayContent, expanded);
            }
        } else {
            segments.removeAll();
            segments.add(new CollapsibleActivityPane(title, displayContent, expanded));
            lastDisplayedLength = displayContent.length();
        }
        revalidate();
    }

    private void updatePaneContent(BaseCollapsiblePane pane, String title, String content, boolean expanded) {
        pane.setTitle(title);
        if (content.length() > lastDisplayedLength) {
            pane.appendContent(content.substring(lastDisplayedLength));
        } else if (content.length() < lastDisplayedLength) {
            pane.setContent(content);
        }
        lastDisplayedLength = content.length();
        pane.setExpanded(expanded);
    }

    private void updateContent(ColorTheme theme, boolean expanded) {
        if ("tool".equals(role) || "thought".equals(role)) {
            handleToolThoughtContent(theme, expanded);
            return;
        }

        // For user messages, we don't need complex code panels.
        // Just render the whole thing as markdown to get simple <pre> blocks.
        if ("user".equals(role)) {
            updateOrAddTextSegment(text.toString(), theme, 0, false);
            while (segments.getComponentCount() > 1) {
                segments.remove(segments.getComponentCount() - 1);
            }
            // Single revalidate at top level cascades to children
            revalidate();
            return;
        }

        // Simple markdown splitting for code blocks: ```[lang]\n<code>```
        String rawText = text.toString();

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(rawText);

        // Track current components to reuse them
        int currentCompIdx = 0;

        int lastEnd = 0;
        int codeIdx = 0;
        while (matcher.find()) {
            // Text before code block (may contain tables)
            String textBefore = rawText.substring(lastEnd, matcher.start());
            if (!textBefore.isEmpty()) {
                currentCompIdx = addTextAndTableSegments(textBefore, theme, currentCompIdx, expanded);
            }

            String lang = matcher.group(1);
            String code = matcher.group(2);

            // Code blocks collapsed by default to avoid flicker from syntax highlighting
            boolean defaultExpanded = false;

            // Persist expanded state if we already had it for this index
            if (codeIdx < codeStates.size()) {
                defaultExpanded = codeStates.get(codeIdx).expanded;
            } else {
                codeStates.add(new CollapsibleState(defaultExpanded));
            }

            updateOrAddCodeSegment(lang, code, defaultExpanded, codeIdx, currentCompIdx++);

            lastEnd = matcher.end();
            codeIdx++;
        }

        // Remaining text after last code block
        if (lastEnd < rawText.length()) {
            String remaining = rawText.substring(lastEnd);
            if (!remaining.isEmpty()) {
                currentCompIdx = addTextAndTableSegments(remaining, theme, currentCompIdx, expanded);
            }
        }

        // Remove extra old components
        while (segments.getComponentCount() > currentCompIdx) {
            segments.remove(segments.getComponentCount() - 1);
        }

        // Single revalidate at top level cascades to all children
        revalidate();
    }

    private void copyMessageToClipboard(JButton copyBtn) {
        String textToCopy = text.toString();
        if (textToCopy.isEmpty()) {
            return;
        }

        StringSelection selection = new StringSelection(textToCopy);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        Icon originalIcon = copyBtn.getIcon();
        Icon checkIcon = ThemeManager.getIcon("check.svg", 14);
        copyBtn.setIcon(checkIcon);

        Timer timer = new Timer(2000, e -> {
            if (copyBtn.isShowing()) {
                copyBtn.setIcon(originalIcon);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Finalizes streaming, converting from JTextArea to rich HTML.
     * @param expanded initial collapse state for code/tool panes
     */
    public void finalizeStreaming(boolean expanded) {
        finalizeStreaming(expanded, false);
    }

    /**
     * Finalizes streaming.
     * @param expanded  initial collapse state for code/tool panes
     * @param immediate if true, build HTML now; if false, defer for 300 ms
     *                  to let additional deltas accumulate (avoids repeated
     *                  expensive rebuilds during section splits).
     */
    public void finalizeStreaming(boolean expanded, boolean immediate) {
        // If neither streaming nor deferred—just toggle child panes
        if (!isStreaming && !isFinalizingDeferred) {
            for (Component c : segments.getComponents()) {
                if (c instanceof CollapsibleCodePane codePane) {
                    codePane.setExpanded(expanded);
                } else if (c instanceof CollapsibleToolPane toolPane) {
                    toolPane.setExpanded(expanded);
                } else if (c instanceof CollapsibleActivityPane activityPane) {
                    activityPane.setExpanded(expanded);
                }
            }
            revalidate();
            repaint();
            return;
        }

        savedCollapseState = expanded;

        if (immediate) {
            performFinalization();
        } else {
            // Defer: start a 300ms cooldown timer. If appendText() is
            // called before it fires, the timer resets.
            isFinalizingDeferred = true;
            if (deferredFinalizeTimer == null) {
                deferredFinalizeTimer = new Timer(300, e -> performFinalization());
                deferredFinalizeTimer.setRepeats(false);
            }
            deferredFinalizeTimer.restart();
        }
    }

    /** Actually removes the streaming JTextArea and builds the rich HTML content. */
    private void performFinalization() {
        isStreaming = false;
        isFinalizingDeferred = false;
        if (deferredFinalizeTimer != null) {
            deferredFinalizeTimer.stop();
        }
        if (streamingTextArea != null) {
            segments.remove(streamingTextArea);
            streamingTextArea = null;
        }
        updateContent(ThemeManager.getCurrentTheme(), savedCollapseState);
        revalidate();
        repaint();
    }

    /**
     * Replaces the tool/thought content with segmented (markdown-rendered) blocks.
     * Each block is a consecutive run of same-type chunks with a distinct background.
     */
    public void setSegmentedToolContent(List<CollapsibleToolPane.ToolSegment> blocks) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleActivityPane pane) {
                pane.setSegmentedContent(blocks);
                return;
            } else if (c instanceof CollapsibleToolPane pane) {
                pane.setSegmentedContent(blocks);
                return;
            }
        }
    }

    /**
     * Updates both the title and segmented content of the combined activity pane.
     * Used by applyTypeFilters() when re-filtering changes the visible count.
     */
    public void updateCombinedContent(List<CollapsibleToolPane.ToolSegment> blocks, String title) {
        this.toolTitle = title;
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleActivityPane pane) {
                pane.setTitle(title);
                pane.setSegmentedContent(blocks);
                return;
            } else if (c instanceof CollapsibleToolPane pane) {
                pane.setTitle(title);
                pane.setSegmentedContent(blocks);
                return;
            }
        }
    }

    private void updateOrAddCodeSegment(String lang, String code, boolean expanded, int codeIdx, int compIdx) {
        if (compIdx < segments.getComponentCount()) {
            Component c = segments.getComponent(compIdx);
            if (c instanceof CollapsibleCodePane pane) {
                pane.updateContent(lang, code);
                pane.setVisible(code != null && !code.trim().isEmpty());
                return;
            }
        }

        // If we can't reuse, we have to rebuild from here down to be safe
        // But for simplicity, we'll just insert/replace
        CollapsibleCodePane codePane = new CollapsibleCodePane(lang, code, expanded);
        codePane.setVisible(code != null && !code.trim().isEmpty());
        if (compIdx < segments.getComponentCount()) {
            segments.remove(compIdx);
            segments.add(codePane, compIdx);
        } else {
            segments.add(codePane);
        }
    }

    private void updateOrAddTextSegment(String markdown, ColorTheme theme, int compIdx, boolean incremental) {
        String styledHtml = HtmlContentPreparer.prepareHtml(markdown, theme, role, incremental);
        Color bg = UIUtils.getBubbleBackground(theme, role);

        if (compIdx < segments.getComponentCount()) {
            Component c = segments.getComponent(compIdx);
            if (c instanceof FitEditorPane pane) {
                pane.setBackground(TRANSPARENT);
                pane.setOpaque(false);
                pane.setText(styledHtml);
                return;
            }
        }

        FitEditorPane pane = FitEditorPane.createHtmlPane(styledHtml, bg, role, true);
        if (compIdx < segments.getComponentCount()) {
            segments.remove(compIdx);
            segments.add(pane, compIdx);
        } else {
            segments.add(pane);
        }
    }

    private int addTextAndTableSegments(String text, ColorTheme theme, int compIdx, boolean incremental) {
        TableResult result = TableDetector.detectTables(text, incremental);
        int currentIdx = compIdx;

        for (Segment seg : result.segments()) {
            if (seg instanceof TextSegment ts) {
                updateOrAddTextSegment(ts.text(), theme, currentIdx++, false);
            } else if (seg instanceof TableSegment tbl) {
                updateOrAddTableSegment(tbl.markdown(), theme, currentIdx++);
            }
        }

        return currentIdx;
    }

    private void updateOrAddTableSegment(String tableMarkdown, ColorTheme theme, int compIdx) {
        String styledHtml = HtmlContentPreparer.prepareHtml(tableMarkdown, theme, role, false);

        if (compIdx < segments.getComponentCount()) {
            Component c = segments.getComponent(compIdx);
            if (c instanceof RoundedPanel rp && rp.getComponentCount() > 0 && rp.getComponent(0) instanceof FitEditorPane pane) {
                rp.setBaseColor(theme.tableBackground());
                rp.setBorderColor(theme.tableBorder());
                pane.setText(styledHtml);
                return;
            }
        }

        RoundedPanel rp = new RoundedPanel(12);
        rp.setBaseColor(theme.tableBackground());
        rp.setBorderColor(theme.tableBorder());
        rp.setLayout(new BorderLayout());
        rp.setBorder(new EmptyBorder(1, 1, 1, 1));

        FitEditorPane pane = FitEditorPane.createHtmlPane(styledHtml, theme.tableBackground(), role, false);
        pane.setOpaque(false);
        pane.setBorder(new EmptyBorder(8, 8, 8, 8));
        rp.add(pane, BorderLayout.CENTER);

        if (compIdx < segments.getComponentCount()) {
            segments.remove(compIdx);
            segments.add(rp, compIdx);
        } else {
            segments.add(rp);
        }
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // This is the secret sauce for wrapping
    }

    // Other Scrollable methods can return default values
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 10;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 100;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
