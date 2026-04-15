package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.View;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class MessageBubble extends JPanel {

    private static final long serialVersionUID = 1L;
    private final String type;
    private final String messageId;
    private final StringBuilder text;
    private final JPanel segmentsContainer;
    private JPanel bubble;
    private final ArrayList<CollapsibleState> codeStates = new ArrayList<>();

    private static class CollapsibleState {
        boolean expanded;
        CollapsibleState(boolean expanded) { this.expanded = expanded; }
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private static final class FitEditorPane extends JEditorPane {
        private int lastComputedHeight = 0;
        private int lastComputedWidth = 0;
        private String lastText = null;

        @Override
        public void setText(String t) {
            if (t != null && t.equals(lastText)) {
                return;
            }
            lastText = t;
            super.setText(t);
            lastComputedHeight = 0; // Reset cache
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getWidth();
            if (w <= 0 && getParent() != null) {
                w = getParent().getWidth();
            }
            if (w <= 20) {
                w = 400;
            }

            if (w == lastComputedWidth && lastComputedHeight > 0) {
                return new Dimension(w, lastComputedHeight + 12);
            }

            try {
                View root = getUI().getRootView(this);
                if (root != null) {
                    root.setSize(w, Short.MAX_VALUE);
                    float h = root.getPreferredSpan(View.Y_AXIS);
                    if (h > 0) {
                        lastComputedHeight = (int) Math.ceil(h);
                        lastComputedWidth = w;
                        return new Dimension(w, lastComputedHeight + 12);
                    }
                }
            } catch (Exception ignored) {
            }

            if (lastComputedHeight > 0) {
                return new Dimension(w, Math.max(30, lastComputedHeight + 12));
            }
            return new Dimension(w, Math.max(30, super.getPreferredSize().height));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    public MessageBubble(String type, String text) {
        this(type, text, null);
    }

    public MessageBubble(String type, String text, String messageId) {
        this.type = type;
        this.messageId = messageId;
        this.text = new StringBuilder(text);

        setLayout(new GridBagLayout());
        setOpaque(false);
        setDoubleBuffered(true);
        setBorder(new EmptyBorder(4, 16, 8, 16));

        ColorTheme theme = ThemeManager.getCurrentTheme();

        segmentsContainer = new JPanel();
        segmentsContainer.setLayout(new BoxLayout(segmentsContainer, BoxLayout.Y_AXIS));
        segmentsContainer.setOpaque(false);
        segmentsContainer.setDoubleBuffered(true);

        this.bubble = new JPanel(new BorderLayout());
        bubble.setOpaque(false);
        // Only User messages get the prominent global bubble wrapper
        if ("user".equals(type)) {
            RoundedPanel p = new RoundedPanel(16);
            p.setLayout(new BorderLayout());
            p.setBorder(new EmptyBorder(8, 16, 12, 16));
            this.bubble = p;
        } else {
            this.bubble.setBorder(new EmptyBorder(4, 12, 12, 12));
        }
        this.bubble.add(segmentsContainer, BorderLayout.CENTER);

        updateContent(theme);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);

        if ("user".equals(type)) {
            if (bubble instanceof RoundedPanel rp) { rp.setBaseColor(theme.getBubbleUser()); }
            bubble.setBackground(theme.getBubbleUser());
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(4, 12, 12, 12);

            Icon copyIcon = ThemeManager.getIcon("copy.svg", 20);
            JButton copyBtn = new JButton(copyIcon);
            copyBtn.setToolTipText("Copy to input");
            copyBtn.setFocusPainted(false);
            copyBtn.setContentAreaFilled(false);
            copyBtn.setBorder(new EmptyBorder(2, 8, 2, 8));
            copyBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

            copyBtn.addActionListener(e -> {
                ACPChatTopComponent.findInstance().setInputText(this.text.toString());
                
                // Visual feedback
                Icon originalIcon = copyBtn.getIcon();
                Icon checkIcon = ThemeManager.getIcon("check.svg", 20);
                copyBtn.setIcon(checkIcon);
                javax.swing.Timer timer = new javax.swing.Timer(1500, ev -> copyBtn.setIcon(originalIcon));
                timer.setRepeats(false);
                timer.start();
            });
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            footer.add(copyBtn, BorderLayout.EAST);
            bubble.add(footer, BorderLayout.SOUTH);
        } else if ("error".equals(type)) {
            Color errorBg = new Color(255, 235, 238);
            bubble.setBackground(errorBg);
            if (bubble instanceof RoundedPanel rp) { rp.setBaseColor(errorBg); }
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(4, 12, 12, 12);
        } else if ("tool".equals(type) || "thought".equals(type)) {
            bubble.setBackground(new Color(0, 0, 0, 0));
            if (bubble instanceof RoundedPanel rp) { rp.setBaseColor(null); }
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(4, 12, 12, 12);
        } else {
            bubble.setBackground(new Color(0, 0, 0, 0));
            if (bubble instanceof RoundedPanel rp) { rp.setBaseColor(null); }
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(4, 12, 12, 12);
        }

        add(bubble, gbc);
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
        if (segmentsContainer.getComponentCount() > 0 && segmentsContainer.getComponent(0) instanceof CollapsibleToolPane pane) {
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

    public void refreshTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        if ("user".equals(type)) {
            bubble.setBackground(theme.getBubbleUser());
            if (bubble instanceof RoundedPanel rp) { rp.setBaseColor(theme.getBubbleUser()); }
        } else if ("error".equals(type)) {
            Color errorBg = new Color(255, 235, 238);
            bubble.setBackground(errorBg);
            if (bubble instanceof RoundedPanel rp) { rp.setBaseColor(errorBg); }
        } else {
            bubble.setBackground(new Color(0, 0, 0, 0));
            if (bubble instanceof RoundedPanel rp) { rp.setBaseColor(null); }
        }

        for (Component c : segmentsContainer.getComponents()) {
            if (c instanceof CollapsibleCodePane pane) {
                pane.refreshTheme();
            } else if (c instanceof CollapsibleToolPane pane) {
                pane.refreshTheme();
            }
        }

        updateContent(theme);
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
                if (rawText.startsWith("Called")) {
                    int toolStart = rawText.indexOf("the ") + 4;
                    int toolEnd = rawText.indexOf(" tool");
                    if (toolStart > 3 && toolEnd > toolStart) {
                        title = "Tool: Use " + rawText.substring(toolStart, toolEnd).trim();
                    }
                } else if (rawText.contains(":") && rawText.length() < 100) {
                    title = "Tool: " + rawText;
                }
            }

            // Reuse existing tool pane if possible to preserve expanded state
            if (segmentsContainer.getComponentCount() > 0 && segmentsContainer.getComponent(0) instanceof CollapsibleToolPane) {
                CollapsibleToolPane existingPane = (CollapsibleToolPane) segmentsContainer.getComponent(0);
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
            segmentsContainer.revalidate();
            return;
        }

        // Simple markdown splitting for code blocks: ```[lang]\n<code>```
        String rawText = text.toString();

        Pattern pattern = Pattern.compile("```([\\w\\-\\+\\#\\.]*)\\R?(.*?)(?:```(?=\\R|$)|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);

        // Track current components to reuse them
        int currentCompIdx = 0;

        int lastEnd = 0;
        int codeIdx = 0;
        while (matcher.find()) {
            // Text before code block
            String textBefore = rawText.substring(lastEnd, matcher.start());
            if (!textBefore.isEmpty()) {
                updateOrAddTextSegment(textBefore, theme, currentCompIdx++, incremental);
            }

            String lang = matcher.group(1);
            String code = matcher.group(2);

            // Determine default expanded state: User messages collapse by default
            boolean defaultExpanded = !"user".equals(type);

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
                updateOrAddTextSegment(remaining, theme, currentCompIdx++, incremental);
            }
        }

        // Remove extra old components
        while (segmentsContainer.getComponentCount() > currentCompIdx) {
            segmentsContainer.remove(segmentsContainer.getComponentCount() - 1);
        }

        segmentsContainer.revalidate();
        bubble.revalidate();
        this.revalidate();
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
                return;
            }
        }

        // If we can't reuse, we have to rebuild from here down to be safe
        // But for simplicity, we'll just insert/replace
        CollapsibleCodePane codePane = new CollapsibleCodePane(lang, code, expanded);
        if (compIdx < segmentsContainer.getComponentCount()) {
            segmentsContainer.remove(compIdx);
            segmentsContainer.add(codePane, compIdx);
        } else {
            segmentsContainer.add(codePane);
        }
    }

    private void updateOrAddTextSegment(String markdown, ColorTheme theme, int compIdx, boolean incremental) {
        String styledHtml = prepareHtml(markdown, theme);
        Color bg = getBubbleBackground(theme);

        if (compIdx < segmentsContainer.getComponentCount()) {
            Component c = segmentsContainer.getComponent(compIdx);
            if (c instanceof FitEditorPane pane) {
                pane.setBackground(bg);
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



    private String prepareHtml(String markdown, ColorTheme theme) {
        MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        String markdownWithTables = renderTablesAsHtml(markdown);
        String html = renderer.render(parser.parse(markdownWithTables));

        html = html.replace("<table>", "<table border='1' style='border-collapse: collapse; width: 100%; margin: 8px 0;'>");
        html = html.replace("<th>", "<th style='background: #f0f0f0; padding: 8px; border: 1px solid #ddd; text-align: left;'>");
        html = html.replace("<td>", "<td style='padding: 8px; border: 1px solid #ddd; vertical-align: top;'>");

        // Hack to prevent space collapse in JEditorPane
        // Replace double spaces with space + &nbsp;
        html = html.replace("  ", " &nbsp;");

        Color bg;
        if ("user".equals(type)) {
            bg = theme.getBubbleUser();
        } else if ("error".equals(type)) {
            bg = new Color(255, 235, 238);
        } else {
            bg = theme.getSunkenBackground();
        }

        boolean isAssistant = !"user".equals(type) && !"error".equals(type) && !"tool".equals(type);
        String customCss = theme.toCss(bg, isAssistant);
        if ("error".equals(type)) {
            customCss += " body { color: #D32F2F; font-weight: bold; }";
        } else if ("tool".equals(type)) {
            customCss += " body { color: #777777; font-size: 13px; }";
        }

        // Removed the <pre> wrap which was causing nested <p> segments to render incorrectly and overlap.
        // We rely on 'white-space: pre-wrap' in the base CSS to preserve whitespace.
        return "<html><head><style>" + customCss + "</style></head><body style='margin: 0; padding: 8px;'>" + html + "</body></html>";
    }

    private Color getBubbleBackground(ColorTheme theme) {
        if ("user".equals(type)) {
            return theme.getBubbleUser();
        } else if ("error".equals(type)) {
            return new Color(255, 235, 238);
        } else {
            return theme.getSunkenBackground();
        }
    }

    private FitEditorPane createHtmlPane(String styledHtml, Color bg) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        FitEditorPane pane = new FitEditorPane();
        pane.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setOpaque(true);
        pane.setBackground(bg);
        pane.setForeground(theme.getForeground());
        pane.setDoubleBuffered(true);
        pane.setText(styledHtml);
        pane.setFont(ThemeManager.getFont());
        pane.setBorder(new javax.swing.border.EmptyBorder(0, 0, 10, 0));
        return pane;
    }

    private String renderTablesAsHtml(String markdown) {
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
                String content = line.substring(line.indexOf("|") + 1, line.lastIndexOf("|"));
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
        StringBuilder html = new StringBuilder();
        html.append("<table>\n<thead><tr>");
        for (String h : headers) {
            html.append("<th>").append(escapeHtml(h)).append("</th>");
        }
        html.append("</tr></thead>\n<tbody>");
        for (List<String> row : rows) {
            html.append("<tr>");
            // Ensure row has same columns as header
            for (int j = 0; j < headers.size(); j++) {
                String cell = j < row.size() ? row.get(j) : "";
                html.append("<td>").append(escapeHtml(cell)).append("</td>");
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
}
