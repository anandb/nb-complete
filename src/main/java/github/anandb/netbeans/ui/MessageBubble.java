package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import javax.swing.text.View;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TextScanner;

import static github.anandb.netbeans.manager.ToolParamsExtractor.extractToolTitle;

public class MessageBubble extends JPanel implements Scrollable {

    private static final Logger LOG = new Logger(MessageBubble.class);
    private static final long serialVersionUID = 1L;

    // Static cached Pattern for code block parsing - compiled once
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```([\\w\\-\\+\\#\\.]*)\\R?(.*?)(?:```(?=\\R|$)|$)", Pattern.DOTALL);

    // Static cached Flexmark parser/renderer - created once
    private static final Parser FLEXMARK_PARSER;
    private static final HtmlRenderer FLEXMARK_RENDERER;
    static {
        MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        FLEXMARK_PARSER = Parser.builder(options).build();
        FLEXMARK_RENDERER = HtmlRenderer.builder(options).build();
    }

    private final String type;
    private final String messageId;
    private final StringBuilder text;
    private final String copyableText;
    private final String kind;
    private final JPanel segmentsContainer;
    private JPanel bubble;
    private final ArrayList<CollapsibleState> codeStates = new ArrayList<>();

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
            bgColor = new Color(0, 0, 0, 0);
        }

        setBackground(theme.sunkenBackground());
        setOpaque(true);

        bubble.setBackground(bgColor);
        bubble.setOpaque(true);
        segmentsContainer.setBackground(bgColor);
        segmentsContainer.setOpaque(true);

        if (bubble instanceof RoundedPanel) {
            RoundedPanel rp = (RoundedPanel) bubble;
            rp.setBaseColor(bgColor);
            rp.setOpaque(false);
        }
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
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
            int w = getWidth();
            if (w <= 0 && getParent() != null) {
                w = getParent().getWidth();
            }
            if (w <= 20) {
                w = 400;
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
                    root.setSize(contentWidth, Short.MAX_VALUE);
                    float h = root.getPreferredSpan(View.Y_AXIS);
                    if (h > 0) {
                        lastComputedHeight = (int) Math.ceil(h);
                        lastComputedWidth = w;
                        // Add insets back and a 4px safety buffer to prevent clipping
                        cachedSize = new Dimension(w, lastComputedHeight + insets.top + insets.bottom + 4);
                        return cachedSize;
                    }
                }
            } catch (Exception ignored) {
            }

            if (lastComputedHeight > 0) {
                cachedSize = new Dimension(w, Math.max(30, lastComputedHeight + insets.top + insets.bottom + 4));
                return cachedSize;
            }
            Dimension superSize = super.getPreferredSize();
            cachedSize = new Dimension(w, Math.max(30, superSize.height + insets.top + insets.bottom + 4));
            return cachedSize;
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }
    }

    public MessageBubble(String type, String text) {
        this(type, text, null, text, null);
    }

    public MessageBubble(String type, String text, String messageId) {
        this(type, text, messageId, text, null);
    }

    public MessageBubble(String type, String text, String messageId, String copyableText, String kind) {
        this.type = type;
        this.messageId = messageId;
        this.text = new StringBuilder(text);
        this.copyableText = copyableText;
        this.kind = kind;

        ColorTheme theme = ThemeManager.getCurrentTheme();
        setLayout(new java.awt.GridBagLayout());
        setOpaque(true);
        setBackground(theme.sunkenBackground());
        setDoubleBuffered(true);
        setBorder(new EmptyBorder(2, 8, 2, 8));

        segmentsContainer = new JPanel();
        segmentsContainer.setLayout(new BoxLayout(segmentsContainer, BoxLayout.Y_AXIS));
        segmentsContainer.setDoubleBuffered(true);

        this.bubble = new JPanel(new BorderLayout());
        this.bubble.setDoubleBuffered(true);
        // Only User messages get the prominent global bubble wrapper
        if ("user".equals(type)) {
            RoundedPanel p = new RoundedPanel(16);
            p.setLayout(new BorderLayout());
            p.setBorder(new EmptyBorder(6, 12, 6, 12));
            this.bubble = p;
        } else {
            this.bubble.setBorder(new EmptyBorder(2, 8, 6, 8));
        }
        this.bubble.add(segmentsContainer, BorderLayout.CENTER);

        updateContent(theme);

        Insets gbcInsets = new Insets(4, 12, 4, 12);
        int anchor = GridBagConstraints.WEST;

        applyBubbleTheme(theme, type);

        if ("user".equals(type)) {
            anchor = GridBagConstraints.WEST;
            gbcInsets = new Insets(2, 12, 2, 6);

            JButton copyBtn = UIUtils.createToolbarButton("copy.svg", 20, "Copy to input", null);
            copyBtn.setBorder(new javax.swing.border.EmptyBorder(2, 8, 2, 8));

            copyBtn.addActionListener(e -> {
                AssistantTopComponent.findInstance().setInputText(this.copyableText);
                Icon originalIcon = copyBtn.getIcon();
                copyBtn.setIcon(ThemeManager.getIcon("check.svg", 20));
                javax.swing.Timer timer = new javax.swing.Timer(1500, ev -> copyBtn.setIcon(originalIcon));
                timer.setRepeats(false);
                timer.start();
            });
            JPanel sidePanel = UIUtils.createTransparentPanel(new BorderLayout());
            sidePanel.add(copyBtn, BorderLayout.SOUTH);
            bubble.add(sidePanel, BorderLayout.EAST);
        }

        add(bubble, UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL, anchor, gbcInsets));
        LOG.info("Created MessageBubble: type={0}, id={1}, textLength={2}", type, messageId, text.length());
    }

    private boolean hasPendingTextUpdate = false;
    private boolean hasSeenFirstNewline = false;

    public void appendText(String newText) {
        if (newText == null || newText.isEmpty()) {
            return;
        }
        // Preserve all content exactly as it comes from the stream
        this.text.append(newText);

        // Use a more robust check for first content to reveal
        if (!hasSeenFirstNewline) {
            if (newText.contains("\n") || text.indexOf("\n") != -1) {
                hasSeenFirstNewline = true;
            } else if (text.length() > 60) {
                // If we've buffered a lot of text without a newline, reveal it anyway
                hasSeenFirstNewline = true;
            }
        }

        hasPendingTextUpdate = true;
    }

    public boolean flushUpdate() {
        return flushUpdate(false);
    }

    public boolean flushUpdate(boolean force) {
        if (hasPendingTextUpdate) {
            // Buffer till the first newline before displaying assistant messages
            // This avoids showing partial metadata or transient lines at the start
            if (!force && !hasSeenFirstNewline && "assistant".equals(type)) {
                return false;
            }

            hasPendingTextUpdate = false;
            updateContent(ThemeManager.getCurrentTheme(), true);
            return true;
        }
        return false;
    }

    public void setExpanded(boolean expanded) {
        if (segmentsContainer.getComponentCount() > 0
                && segmentsContainer.getComponent(0) instanceof CollapsibleToolPane pane) {
            pane.setExpanded(expanded);
        }
    }

    public void toggleAllBlocks(boolean expanded) {
        for (Component c : segmentsContainer.getComponents()) {
            if (c instanceof CollapsibleCodePane codePane) {
                codePane.setExpanded(expanded);
            } else if (c instanceof CollapsibleToolPane toolPane) {
                toolPane.setExpanded(expanded);
            }
        }
    }

    public String getType() {
        return type;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRawText() {
        return text.toString();
    }

    private void updateContent(ColorTheme theme) {
        updateContent(theme, false);
    }

    private void updateContent(ColorTheme theme, boolean incremental) {
        // Handle specialized tool rendering
        if ("tool".equals(type) || "thought".equals(type)) {
            String rawText = text.toString();
            String title = "thought".equals(type) ? "Thinking Process" : "Tool";
            String displayContent = rawText;

            // Try to extract a summary title from the tool call text
            if ("tool".equals(type)) {
                title = extractToolTitle(messageId, rawText, kind);
            }

            // Reuse existing tool pane if possible to preserve expanded state
            if (segmentsContainer.getComponentCount() > 0
                    && segmentsContainer.getComponent(0) instanceof CollapsibleToolPane existingPane) {
                existingPane.setTitle(title);
                existingPane.setContent(displayContent);
                // Auto-collapse if we are no longer in incremental mode
                if (!incremental) {
                    existingPane.setExpanded(false);
                }
            } else {
                segmentsContainer.removeAll();
                // Thoughts and Tools expand only during active creation (incremental)
                boolean defaultExpanded = incremental;
                CollapsibleToolPane toolPane = new CollapsibleToolPane(title, displayContent, defaultExpanded);
                segmentsContainer.add(toolPane);
            }
            // Single revalidate at container level is sufficient
            revalidate();
            return;
        }

        // For user messages, we don't need complex code panels.
        // Just render the whole thing as markdown to get simple <pre> blocks.
        if ("user".equals(type)) {
            updateOrAddTextSegment(text.toString(), theme, 0, incremental);
            while (segmentsContainer.getComponentCount() > 1) {
                segmentsContainer.remove(segmentsContainer.getComponentCount() - 1);
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
                currentCompIdx = addTextAndTableSegments(textBefore, theme, currentCompIdx, incremental);
            }

            String lang = matcher.group(1);
            String code = matcher.group(2);

            // Determine default expanded state: Assistant messages expand by default
            boolean defaultExpanded = true;

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
                currentCompIdx = addTextAndTableSegments(remaining, theme, currentCompIdx, incremental);
            }
        }

        // Remove extra old components
        while (segmentsContainer.getComponentCount() > currentCompIdx) {
            segmentsContainer.remove(segmentsContainer.getComponentCount() - 1);
        }

        // Single revalidate at top level cascades to all children
        revalidate();
    }

    public void finalizeStreaming() {
        // When streaming ends, collapse all process blocks (Tools/Thoughts)
        // but leave Code blocks open
        for (Component c : segmentsContainer.getComponents()) {
            if (c instanceof CollapsibleToolPane toolPane) {
                toolPane.setExpanded(false);
            }
        }
        revalidate();
        repaint();
    }

    private void updateOrAddCodeSegment(String lang, String code, boolean expanded, int codeIdx, int compIdx) {
        if (compIdx < segmentsContainer.getComponentCount()) {
            Component c = segmentsContainer.getComponent(compIdx);
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
        if (compIdx < segmentsContainer.getComponentCount()) {
            segmentsContainer.remove(compIdx);
            segmentsContainer.add(codePane, compIdx);
        } else {
            segmentsContainer.add(codePane);
        }
    }

    private void updateOrAddTextSegment(String markdown, ColorTheme theme, int compIdx, boolean incremental) {
        String styledHtml = prepareHtml(markdown, theme, incremental);
        Color bg = getBubbleBackground(theme);

        if (compIdx < segmentsContainer.getComponentCount()) {
            Component c = segmentsContainer.getComponent(compIdx);
            if (c instanceof FitEditorPane pane) {
                pane.setBackground(bg);
                pane.setOpaque(true);
                // FitEditorPane.setText now includes a dirty check
                pane.setText(styledHtml);
                return;
            }
        }

        FitEditorPane pane = createHtmlPane(styledHtml, bg);
        if (compIdx < segmentsContainer.getComponentCount()) {
            segmentsContainer.remove(compIdx);
            segmentsContainer.add(pane, compIdx);
        } else {
            segmentsContainer.add(pane);
        }
    }

    private int addTextAndTableSegments(String text, ColorTheme theme, int compIdx, boolean incremental) {
        if (text.isEmpty()) {
            return compIdx;
        }

        // Fast-path for non-table text
        if (!text.contains("|")) {
            updateOrAddTextSegment(text, theme, compIdx++, incremental);
            return compIdx;
        }

        String[] lines = text.split("\n", -1);
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
                        if (isSeparatorRowLine(line)) {
                            headerFound = true;
                            if (textBuffer.length() > 0) {
                                updateOrAddTextSegment(textBuffer.toString(), theme, currentIdx++, incremental);
                                textBuffer.setLength(0);
                            }
                        }
                    }
                }
            } else {
                if (inTable) {
                    if (headerFound) {
                        updateOrAddTableSegment(String.join("\n", tableLines), theme, currentIdx++, incremental);
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
            updateOrAddTableSegment(String.join("\n", tableLines), theme, currentIdx++, incremental);
        } else if (inTable) {
            for (String l : tableLines) {
                textBuffer.append(l).append("\n");
            }
        }

        if (textBuffer.length() > 0) {
            updateOrAddTextSegment(textBuffer.toString(), theme, currentIdx++, incremental);
        }

        return currentIdx;
    }

    private boolean isSeparatorRowLine(String line) {
        String content = line.trim();
        if (content.startsWith("|") && content.endsWith("|")) {
            content = content.substring(1, content.length() - 1);
        }
        String[] cells = content.split("(?<!\\\\)\\|", -1);
        List<String> rowCells = new ArrayList<>();
        for (String cell : cells) {
            rowCells.add(cell.replace("\\|", "|"));
        }
        return isSeparatorRow(rowCells);
    }

    private void updateOrAddTableSegment(String tableMarkdown, ColorTheme theme, int compIdx, boolean incremental) {
        String styledHtml = prepareHtml(tableMarkdown, theme, incremental);

        if (compIdx < segmentsContainer.getComponentCount()) {
            Component c = segmentsContainer.getComponent(compIdx);
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

        if (compIdx < segmentsContainer.getComponentCount()) {
            segmentsContainer.remove(compIdx);
            segmentsContainer.add(rp, compIdx);
        } else {
            segmentsContainer.add(rp);
        }
    }

    private String prepareHtml(String markdown, ColorTheme theme, boolean incremental) {
        String markdownWithTables = incremental ? markdown : renderTablesAsHtml(markdown);
        // Use cached static parser/renderer instead of creating new instances
        String html = FLEXMARK_RENDERER.render(FLEXMARK_PARSER.parse(markdownWithTables));

        String tableBg = theme.toHtmlHex(theme.tableBackground());
        String headerBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String borderColor = theme.toHtmlHex(theme.tableBorder());

        html = html.replace("<table>",
                "<table border='1' bordercolor='" + borderColor + "' cellspacing='0' cellpadding='8' style='border-collapse: collapse; width: 100%; margin: 8px 0; background-color: " + tableBg + ";'>");
        html = html.replace("<th>",
                "<th bgcolor='" + headerBg + "' style='padding: 8px; border: 1px solid " + borderColor + "; text-align: left;'><b>");
        html = html.replace("</th>", "</b></th>");
        html = html.replace("<td>", "<td style='padding: 8px; border: 1px solid " + borderColor + "; vertical-align: top;'>");

        // Hack to prevent space collapse in JEditorPane
        // Replace double spaces with space + &nbsp; but ONLY if it's not ASCII art
        boolean hasArt = TextScanner.containsAsciiArt(markdown);
        if (!hasArt) {
            html = html.replace("  ", " &nbsp;");
        } else {
            LOG.fine("Contains ASCII art, not replacing spaces");
        }

        Color bg;
        if ("user".equals(type)) {
            bg = theme.bubbleUser();
        } else if ("error".equals(type)) {
            bg = theme.errorBackground();
        } else {
            bg = theme.sunkenBackground();
        }

        boolean isAssistant = !"user".equals(type) && !"error".equals(type) && !"tool".equals(type);
        String customCss = theme.toCss(bg, isAssistant);
        if ("error".equals(type)) {
            customCss += " body { color: #D32F2F; font-weight: bold; }";
        }
        // Detect if the content looks like ASCII art (contains box drawing characters)
        String bodyStyle = "margin: 0; padding: 0; text-align: left; word-wrap: break-word; overflow-wrap: break-word; max-width: 100%;";
        if (hasArt) {
            // Avoid pre tag to prevent theme conflicts (black box).
            // Use manual line breaks and nbsp to preserve structure in old renderers.
            String monoStack = theme.getMonoStack();
            customCss += " .ascii-art { font-family: " + monoStack + "; line-height: 1.0; }";

            // Flexmark might have already wrapped it in <p> or added other tags.
            // We need to be careful with replacement.
            html = html.replace("  ", " &nbsp;"); // Replace double spaces at least
            html = html.replace("\n", "<br/>");
            html = "<div class='ascii-art'>" + html + "</div>";
        }

        return "<html><head><style>" + customCss + "</style></head><body style='" + bodyStyle + "'>" + html
                + "</body></html>";
    }

    private Color getBubbleBackground(ColorTheme theme) {
        if ("user".equals(type)) {
            return theme.bubbleUser();
        } else if ("error".equals(type)) {
            return theme.errorBackground();
        } else {
            return theme.sunkenBackground();
        }
    }

    private FitEditorPane createHtmlPane(String styledHtml, Color bg) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        FitEditorPane pane = new FitEditorPane();
        pane.putClientProperty(javax.swing.JTextPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setOpaque(true);
        pane.setBackground(bg);
        pane.setForeground(theme.foreground());
        pane.setDoubleBuffered(true);
        pane.setFont(ThemeManager.getFont());
        pane.setBorder(new EmptyBorder(4, 12, 8, 12));
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.setText(styledHtml);
        return pane;
    }

    private String renderTablesAsHtml(String markdown) {
        // Fast-path: skip if no table markers present
        if (!markdown.contains("|")) {
            return markdown;
        }

        StringBuilder result = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        boolean inTable = false;
        boolean headerFound = false;
        List<String> headerCells = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];

            if (line == null || line.isEmpty()) {
                if (inTable && headerFound) {
                    result.append(convertTableToHtml(headerCells, rows));
                    headerCells.clear();
                    rows.clear();
                    headerFound = false;
                    inTable = false;
                }
                result.append(line).append("\n");
                i++;
                continue;
            }

            if (line.contains("|") && line.trim().startsWith("|") && line.trim().endsWith("|")) {
                int firstPipe = line.indexOf("|");
                int lastPipe = line.lastIndexOf("|");
                if (firstPipe >= lastPipe) {
                    // Malformed table row (single pipe or empty content)
                    result.append(line).append("\n");
                    i++;
                    continue;
                }
                String content = line.substring(firstPipe + 1, lastPipe);
                // Split by pipes not preceded by a backslash
                String[] cells = content.split("(?<!\\\\)\\|", -1);
                List<String> rowCells = new ArrayList<>();
                for (String cell : cells) {
                    rowCells.add(cell.replace("\\|", "|"));
                }

                if (rowCells.isEmpty()) {
                    if (inTable && headerFound) {
                        result.append(convertTableToHtml(headerCells, rows));
                        headerCells.clear();
                        rows.clear();
                        headerFound = false;
                        inTable = false;
                    }
                    result.append(line).append("\n");
                    i++;
                    continue;
                }

                if (inTable && isSeparatorRow(rowCells)) {
                    i++;
                    continue;
                }

                if (!inTable) {
                    inTable = true;
                    headerCells = rowCells;
                    headerFound = true;
                } else if (headerFound) {
                    rows.add(rowCells);
                } else {
                    rows.add(rowCells);
                }
            } else {
                if (inTable && headerFound) {
                    result.append(convertTableToHtml(headerCells, rows));
                    headerCells.clear();
                    rows.clear();
                    headerFound = false;
                    inTable = false;
                }
                result.append(line).append("\n");
            }
            i++;
        }

        if (inTable && headerFound) {
            result.append(convertTableToHtml(headerCells, rows));
        }

        return result.toString();
    }

    private boolean isSeparatorRow(List<String> row) {
        for (String cell : row) {
            if (!cell.matches("-+") && !cell.matches(":-+-.*") && !cell.matches(".*:-+") &&
                    !cell.matches("-+:") && !cell.matches(":.*-+")) {
                return false;
            }
        }
        return true;
    }

    private String convertTableToHtml(List<String> headers, List<List<String>> rows) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String borderColor = theme.toHtmlHex(theme.tableBorder());
        String headerBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String tableBg = theme.toHtmlHex(theme.tableBackground());

        StringBuilder html = new StringBuilder();
        html.append("<table border='1' bordercolor='").append(borderColor)
            .append("' cellspacing='0' cellpadding='8' style='border-collapse: collapse; width: 100%; margin: 8px 0; background-color: ")
            .append(tableBg).append(";'>\n<thead><tr bgcolor='").append(headerBg).append("'>");
        for (String h : headers) {
            html.append("<th style='border: 1px solid ").append(borderColor).append("; text-align: left;'><b>")
                .append(escapeHtml(h)).append("</b></th>");
        }
        html.append("</tr></thead>\n<tbody>");
        for (List<String> row : rows) {
            html.append("<tr>");
            // Ensure row has same columns as header
            for (int j = 0; j < headers.size(); j++) {
                String cell = j < row.size() ? row.get(j) : "";
                html.append("<td style='border: 1px solid ").append(borderColor).append("; vertical-align: top;'>")
                    .append(escapeHtml(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>\n");
        return html.toString();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
