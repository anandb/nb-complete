package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.Scrollable;

import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.View;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TextScanner;
import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.length;


@NbBundle.Messages("HINT_CopyToInput=Copy to input")
public class MessageBubble extends JPanel implements Scrollable {

    private static final Logger LOG = new Logger(MessageBubble.class);
    private static final long serialVersionUID = 1L;

    // Static cached Pattern for code block parsing - compiled once
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```([\\w\\-\\+\\#\\.]*)\\R?(.*?)(?:```(?=\\R|$)|$)", Pattern.DOTALL
    );


    // Static cached Flexmark parser/renderer - created once
    private static final Parser FLEXMARK_PARSER;
    private static final HtmlRenderer FLEXMARK_RENDERER;
    private static final Pattern NEWLINE_SPLIT = Pattern.compile("\n");
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    static {
        MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        FLEXMARK_PARSER = Parser.builder(options).build();
        FLEXMARK_RENDERER = HtmlRenderer.builder(options).build();
    }

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

    private static final class FitEditorPane extends JTextPane {
        private static final long serialVersionUID = 1L;

        private int lastComputedHeight = 0;
        private int lastComputedWidth = 0;
        private String lastText = null;
        private Dimension cachedSize = null;

        @Override
        public void setText(String t) {
            if (t != null && t.equals(lastText)) {
                return;
            }
            lastText = t;
            super.setText(t);
            // Invalidate cached size when text changes
            lastComputedHeight = 0;
            cachedSize = null;
        }

        @Override
        public Dimension getPreferredSize() {
            Insets insets = getInsets();

            // Prefer width from parent hierarchy to handle wrapping correctly before being fully laid out
            int w = getWidth();
            if (w <= 0 || (getParent() != null && getParent().getWidth() > 0 && getParent().getWidth() != w)) {
                Component p = getParent();
                while (p != null) {
                    if (p.getWidth() > 0) {
                        w = p.getWidth();
                        break;
                    }
                    p = p.getParent();
                }
            }

            if (w <= 0) {
                w = 500; // Better default for calculation
            }

            // Fast path: return cached size if dimensions match and cache is valid
            if (w == lastComputedWidth && lastComputedHeight > 0 && cachedSize != null) {
                return cachedSize;
            }

            try {
                View root = getUI().getRootView(this);
                if (root != null) {
                    // Subtract insets to get actual content width for wrapping calculation
                    int contentWidth = Math.max(1, w - insets.left - insets.right);
                    root.setSize(contentWidth, Integer.MAX_VALUE);

                    // Tables sometimes need a second pass or a bit more space to resolve layout
                    float h = root.getPreferredSpan(View.Y_AXIS);
                    if (h > 0) {
                        lastComputedHeight = (int) Math.ceil(h);
                        lastComputedWidth = w;
                        // Add insets back and a 20px safety buffer to prevent clipping (especially for tables)
                        cachedSize = new Dimension(w, lastComputedHeight + insets.top + insets.bottom + 20);
                        return cachedSize;
                    }
                }
            } catch (Exception ex) {
                LOG.fine("View sizing failed, using fallback: {0}", ex.getMessage());
            }

            if (lastComputedHeight > 0) {
                cachedSize = new Dimension(w, Math.max(30, lastComputedHeight + insets.top + insets.bottom + 20));
                return cachedSize;
            }
            Dimension superSize = super.getPreferredSize();
            cachedSize = new Dimension(w, Math.max(30, superSize.height + insets.top + insets.bottom + 20));
            return cachedSize;
        }

        @Override
        public float getAlignmentX() {
            return Component.LEFT_ALIGNMENT;
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
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
        String styledHtml = prepareHtml(markdown, theme, incremental);
        Color bg = UIUtils.getBubbleBackground(theme, role);

        if (compIdx < segments.getComponentCount()) {
            Component c = segments.getComponent(compIdx);
            if (c instanceof FitEditorPane pane) {
                pane.setBackground(TRANSPARENT);
                pane.setOpaque(false);
                // FitEditorPane.setText now includes a dirty check
                pane.setText(styledHtml);
                return;
            }
        }

        FitEditorPane pane = createHtmlPane(styledHtml, bg);
        if (compIdx < segments.getComponentCount()) {
            segments.remove(compIdx);
            segments.add(pane, compIdx);
        } else {
            segments.add(pane);
        }
    }

    private int addTextAndTableSegments(String text, ColorTheme theme, int compIdx, boolean incremental) {
        if (text.isEmpty()) {
            return compIdx;
        }

        // incremental=true during streaming flushes (updateContent called from flushUpdate).
        // During incremental updates, table rows arrive in partial chunks — the heuristic
        // detection (lines starting/ending with "|") would flicker as components swap between
        // FitEditorPane (text) and RoundedPanel (table). Bypass detection: render as plain
        // markdown text. Flexmark still produces <table> HTML inside the FitEditorPane, so
        // tables are visible but without the cosmetic RoundedPanel wrapper. On final render
        // (incremental=false, from constructor or non-streaming addMessage), the full table
        // detection + RoundedPanel wrapping runs cleanly.
        if (incremental) {
            updateOrAddTextSegment(text, theme, compIdx++, true);
            return compIdx;
        }

        // Fast-path for non-table text
        if (!text.contains("|")) {
            updateOrAddTextSegment(text, theme, compIdx++, false);
            return compIdx;
        }

        String[] lines = NEWLINE_SPLIT.split(text, -1);
        StringBuilder textBuffer = new StringBuilder();
        List<String> tableLines = new ArrayList<>();
        boolean inTable = false;
        boolean headerFound = false;
        int currentIdx = compIdx;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isTableRow = line.contains("|") && line.trim().startsWith("|") && line.trim().endsWith("|");

            if (isTableRow) {
                if (!inTable) {
                    inTable = true;
                    tableLines.add(line);
                } else {
                    tableLines.add(line);
                    if (!headerFound) {
                        if (UIUtils.isSeparatorRowLine(line)) {
                            headerFound = true;
                            if (textBuffer.length() > 0) {
                                updateOrAddTextSegment(textBuffer.toString(), theme, currentIdx++, false);
                                textBuffer.setLength(0);
                            }
                        }
                    }
                }
            } else {
                if (inTable) {
                    if (headerFound) {
                        updateOrAddTableSegment(String.join("\n", tableLines), theme, currentIdx++);
                    } else {
                        for (String l : tableLines) {
                            textBuffer.append(l).append("\n");
                        }
                    }
                    tableLines.clear();
                    inTable = false;
                    headerFound = false;
                }
                textBuffer.append(line);
                if (i < lines.length - 1) {
                    textBuffer.append("\n");
                }
            }
        }

        if (inTable && headerFound) {
            updateOrAddTableSegment(String.join("\n", tableLines), theme, currentIdx++);
        } else if (inTable) {
            for (String l : tableLines) {
                textBuffer.append(l).append("\n");
            }
        }

        if (textBuffer.length() > 0) {
            updateOrAddTextSegment(textBuffer.toString(), theme, currentIdx++, false);
        }

        return currentIdx;
    }


    private void updateOrAddTableSegment(String tableMarkdown, ColorTheme theme, int compIdx) {
        String styledHtml = prepareHtml(tableMarkdown, theme, false);

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
        rp.setBorder(new EmptyBorder(1, 1, 1, 1)); // 1px margin for the rounded border

        FitEditorPane pane = createHtmlPane(styledHtml, theme.tableBackground());
        pane.setOpaque(false); // Let RoundedPanel background show through
        pane.setBorder(new EmptyBorder(8, 8, 8, 8));
        rp.add(pane, BorderLayout.CENTER);

        if (compIdx < segments.getComponentCount()) {
            segments.remove(compIdx);
            segments.add(rp, compIdx);
        } else {
            segments.add(rp);
        }
    }

    private String prepareHtml(String markdown, ColorTheme theme, boolean incremental) {
        String html = FLEXMARK_RENDERER.render(FLEXMARK_PARSER.parse(markdown));

        // During streaming, skip all post-processing — the full render will fix
        // everything on the final tick. Avoids O(n) replace chain, ASCII art scan,
        // and space replacement each 100ms.
        if (incremental) {
            return html;
        }

        String tableBg = theme.toHtmlHex(theme.tableBackground());
        String headerBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String borderColor = theme.toHtmlHex(theme.tableBorder());

        // Single-pass tag replacement
        String tableTag = "<table align='left' border='1' bordercolor='" + borderColor
                + "' cellspacing='0' cellpadding='8' style='border-collapse: collapse; width: 100%; margin: 8px 0; background-color: "
                + tableBg + ";'>";
        String thTag = "<th align='left' bgcolor='" + headerBg
                + "' style='padding: 8px; border: 1px solid " + borderColor + "; text-align: left;'><b>";
        String tdTag = "<td align='left' style='padding: 8px; border: 1px solid " + borderColor + "; vertical-align: top;'>";
        String pTag = "<p align='left' style='text-align: left !important;'>";
        String divTag = "<div align='left' style='text-align: left !important;'>";

        html = html.replace("</th>", "</b></th>")
                   .replace("<table>", tableTag)
                   .replace("<th>", thTag)
                   .replace("<td>", tdTag)
                   .replace("<p>", pTag)
                   .replace("<div>", divTag);

        // Hack to prevent space collapse in JEditorPane
        // Replace double spaces with space + &nbsp; but ONLY if it's not ASCII art
        boolean hasArt = TextScanner.containsAsciiArt(markdown);
        if (!hasArt && !"user".equals(role)) {
            html = html.replace("  ", " &nbsp;");
        } else if (hasArt) {
            LOG.fine("Contains ASCII art, not replacing spaces");
        }

        Color bg = UIUtils.getBubbleBackground(theme, role);

        boolean isAssistant = !"user".equals(role) && !"error".equals(role) && !"tool".equals(role);
        String customCss = theme.toCss(null, isAssistant);
        if ("error".equals(role)) {
            customCss += " body { color: #D32F2F; font-weight: bold; }";
        } else if ("user".equals(role)) {
            customCss += " body { font-weight: 300; }";
        }
        // Detect if the content looks like ASCII art (contains box drawing characters)
        String bodyStyle = "margin: 0; padding: 0; text-align: left !important; width: 100%;";
        if (hasArt) {
            // Avoid pre tag to prevent theme conflicts (black box).
            // Use manual line breaks and nbsp to preserve structure in old renderers.
            String monoStack = FontStacks.MONO_STACK;
            customCss += " .ascii-art { font-family: " + monoStack + "; line-height: 1.0; }";

            // Flexmark might have already wrapped it in <p> or added other tags.
            // We need to be careful with replacement.
            html = html.replace("  ", " &nbsp;"); // Replace double spaces at least
            html = html.replace("\n", "<br/>");
            html = "<div class='ascii-art'>" + html + "</div>";
        }

        return "<html><head><style>" + customCss + "</style></head><body style='" + bodyStyle + "'>"
                + html
                + "</body></html>";
    }


    private FitEditorPane createHtmlPane(String styledHtml, Color bg) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        FitEditorPane pane = new FitEditorPane();
        pane.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setEditable(false);
        pane.setContentType("text/html");
        // Use opaque background for User bubbles to prevent "garbled" text artifacts in scroll panes
        if ("user".equals(role) && bg != null && bg.getAlpha() > 0) {
            pane.setOpaque(true);
            pane.setBackground(bg);
        } else {
            pane.setOpaque(false);
            pane.setBackground(TRANSPARENT);
        }
        pane.setMargin(new Insets(0, 0, 0, 0));
        pane.setForeground(theme.foreground());
        pane.setDoubleBuffered(true);
        pane.setFont(ThemeManager.getFont());
        // Default AI text alignment: 8 (bubble) + 20 (pane padding) = 28px
        pane.setBorder(new EmptyBorder(6, 20, 10, 6));
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.setText(styledHtml);
        return pane;
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
