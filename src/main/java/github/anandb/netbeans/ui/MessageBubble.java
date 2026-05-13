package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;

import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.length;


import github.anandb.netbeans.ui.TableDetector.Segment;
import github.anandb.netbeans.ui.TableDetector.TableResult;
import github.anandb.netbeans.ui.TableDetector.TextSegment;
import github.anandb.netbeans.ui.TableDetector.TableSegment;


@NbBundle.Messages("HINT_CopyToInput=Copy to input")
public class MessageBubble extends JPanel implements Scrollable {

    private static final Logger LOG = new Logger(MessageBubble.class);
    private static final long serialVersionUID = 1L;

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
//    private boolean expanded = false;

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
        if ("user".equals(type)) {
            bgColor = theme.bubbleUser();
        } else if ("error".equals(type)) {
            bgColor = theme.errorBackground();
        } else {
            bgColor = TRANSPARENT;
        }

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

    public MessageBubble(MessageType type, String text, String messageId, String toolTitle) {
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
        // Only User messages get the prominent global bubble wrapper
        if ("user".equals(role)) {
            RoundedPanel p = new RoundedPanel(16);
            p.setLayout(new BorderLayout());
            p.setBorder(new EmptyBorder(10, 8, 10, 8));
            this.bubble = p;
        } else {
            this.bubble.setBorder(new EmptyBorder(2, 8, 6, 8));
        }
        this.bubble.add(segments, BorderLayout.CENTER);

        updateContent(theme, false);
        boolean isAssistant = "assistant".equals(role);

        Insets gbcInsets = new Insets(4, 12, 4, 12);
        int anchor = isAssistant ? GridBagConstraints.NORTHWEST : GridBagConstraints.WEST;
        int fill = GridBagConstraints.HORIZONTAL;

        applyBubbleTheme(theme, role);

        if ("user".equals(role)) {
            gbcInsets = new Insets(4, 12, 4, 12); // Full width, consistent with AI

            Icon userIcon = UIUtils.loadUserIcon();
            JLabel userLabel = new JLabel(userIcon);
            userLabel.setBorder(new javax.swing.border.EmptyBorder(6, 8, 0, 10));
            userLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            userLabel.setToolTipText(NbBundle.getMessage(MessageBubble.class, "HINT_CopyToInput"));

            userLabel.addMouseListener(new MessageCopyMouseAdapter(
                userLabel,
                userIcon,
                ThemeManager.getIcon("copy.svg", 32),
                ThemeManager.getIcon("check.svg", 32),
                messageId, role, this
            ));

            JPanel rightPanel = UIUtils.createTransparentPanel(new BorderLayout());
            rightPanel.add(userLabel, BorderLayout.NORTH);
            bubble.add(rightPanel, BorderLayout.EAST);
        } else {
            // AI messages use full width
            gbcInsets.left = 12;
            gbcInsets.right = 12;
        }

        add(bubble, UIUtils.createGbc(0, 0, 1.0, 0, fill, anchor, gbcInsets));

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
    }

    public boolean flushUpdate() {
        return flushUpdate(false);
    }

    public boolean flushUpdate(boolean force) {
        if (hasPendingTextUpdate) {
            hasPendingTextUpdate = false;
            updateContent(ThemeManager.getCurrentTheme(), true);
            return true;
        }

        return false;
    }

    public void setExpanded(boolean expanded) {
        if (segments.getComponentCount() > 0 && segments.getComponent(0) instanceof CollapsibleToolPane pane) {
            pane.setExpanded(expanded);
        }
    }

    public void toggleAllBlocks(boolean expanded) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleCodePane codePane) {
                codePane.setExpanded(expanded);
            } else if (c instanceof CollapsibleToolPane toolPane) {
                toolPane.setExpanded(expanded);
            }
        }
    }


    public String getRole() {
        return role;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRawText() {
        return text.toString();
    }

    public void setResponseTimeMs(long ms) {
        if (ms <= 0) return;
        this.responseTimeMs = ms;
        String label = formatElapsed(ms);
        JLabel ttftLabel = new JLabel(label);
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

    private void updateContent(ColorTheme theme, boolean expanded) {
        // Handle specialized tool rendering
        if ("tool".equals(role) || "thought".equals(role)) {
            String rawText = text.toString();
            String displayContent = rawText;
            String title = "thought".equals(role) ? "Thinking Process" : defaultIfBlank(toolTitle, "Tool");

            if (segments.getComponentCount() > 0 && segments.getComponent(0) instanceof CollapsibleToolPane ep) {
                ep.setTitle(title);
                ep.setContent(displayContent);
                ep.setExpanded(expanded);
            } else {
                segments.removeAll();
                CollapsibleToolPane toolPane = new CollapsibleToolPane(title, displayContent, expanded);
                segments.add(toolPane);
            }
            // Single revalidate at container level is sufficient
            revalidate();
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

    public void finalizeStreaming(boolean defaultExpanded) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleCodePane codePane) {
                codePane.setExpanded(defaultExpanded);
            } else if (c instanceof CollapsibleToolPane toolPane) {
                toolPane.setExpanded(defaultExpanded);
            }
        }
        revalidate();
        repaint();
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
