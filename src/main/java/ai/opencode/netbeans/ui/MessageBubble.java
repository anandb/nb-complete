package ai.opencode.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
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
import java.awt.Component;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class MessageBubble extends JPanel {

    private static final long serialVersionUID = 1L;
    private final String type;
    private final String messageId;
    private final StringBuilder text;
    private final JPanel segmentsContainer;
    private final ArrayList<CollapsibleState> codeStates = new ArrayList<>();

    private static class CollapsibleState {
        boolean expanded;
        CollapsibleState(boolean expanded) { this.expanded = expanded; }
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
        setBorder(new EmptyBorder(4, 8, 8, 8));

        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();

        segmentsContainer = new JPanel();
        segmentsContainer.setLayout(new BoxLayout(segmentsContainer, BoxLayout.Y_AXIS));
        segmentsContainer.setOpaque(false);

        RoundedPanel bubble = new RoundedPanel(16);
        bubble.setLayout(new BorderLayout());
        bubble.setBorder(new EmptyBorder(4, 12, 12, 12));
        bubble.add(segmentsContainer, BorderLayout.CENTER);

        updateContent(theme);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);

        if ("user".equals(type)) {
            bubble.setBackground(theme.getBubbleUser());
            bubble.setBaseColor(theme.getBubbleUser());
            gbc.anchor = GridBagConstraints.EAST;
        } else if ("error".equals(type)) {
            Color errorBg = new Color(255, 235, 238);
            bubble.setBackground(errorBg);
            bubble.setBaseColor(errorBg);
            gbc.anchor = GridBagConstraints.WEST;
            bubble.setBorder(new EmptyBorder(4, 12, 10, 12));
        } else if ("tool".equals(type) || "thought".equals(type)) {
            bubble.setBackground(new Color(0, 0, 0, 0));
            bubble.setBaseColor(null);
            gbc.anchor = GridBagConstraints.WEST;
            bubble.setBorder(new EmptyBorder(0, 4, 10, 12));
        } else {
            bubble.setBackground(new Color(0, 0, 0, 0));
            bubble.setBaseColor(null);
            gbc.anchor = GridBagConstraints.WEST;
            bubble.setBorder(new EmptyBorder(4, 0, 8, 12));
        }

        add(bubble, gbc);
    }

    private static class RoundedPanel extends JPanel {

        private static final long serialVersionUID = 1L;
        private final int radius;
        private Color baseColor;

        public RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        public void setBaseColor(Color color) {
            this.baseColor = color;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (baseColor != null) {
                g2.setColor(baseColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            }

            ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
            if (theme.getBubbleBorder() != null && theme.getBubbleBorder().getAlpha() > 0 && baseColor != null) {
                g2.setColor(theme.getBubbleBorder());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
        }
    }

    public void appendText(String newText) {
        this.text.append(newText);
        updateContent(ThemeManager.getCurrentTheme());
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

    private void updateContent(ThemeManager.Theme theme) {
        // Handle specialized tool rendering
        if ("tool".equals(type) || "thought".equals(type)) {
            String rawText = text.toString();
            String title = "thought".equals(type) ? "THINKING PROCESS" : "🛠️ Tool Call";
            String displayContent = rawText;

            // Try to extract a summary title from the tool call text
            if ("tool".equals(type)) {
                if (rawText.startsWith("Called")) {
                    int toolStart = rawText.indexOf("the ") + 4;
                    int toolEnd = rawText.indexOf(" tool");
                    if (toolStart > 3 && toolEnd > toolStart) {
                        title = "🛠️ Use " + rawText.substring(toolStart, toolEnd).trim();
                    }
                } else if (rawText.contains(":") && rawText.length() < 100) {
                    title = "🛠️ " + rawText;
                }
            } else {
                // For thought, we might want a different default expanded state but
                // CollapsibleToolPane takes that in constructor
            }

            // Reuse existing tool pane if possible to preserve expanded state
            if (segmentsContainer.getComponentCount() > 0 && segmentsContainer.getComponent(0) instanceof CollapsibleToolPane) {
                CollapsibleToolPane existingPane = (CollapsibleToolPane) segmentsContainer.getComponent(0);
                existingPane.setTitle(title);
                existingPane.setContent(displayContent);
            } else {
                segmentsContainer.removeAll();
                boolean defaultExpanded = "thought".equals(type);
                CollapsibleToolPane toolPane = new CollapsibleToolPane(title, displayContent, defaultExpanded);
                segmentsContainer.add(toolPane);
            }
            segmentsContainer.revalidate();
            segmentsContainer.repaint();
            return;
        }

        // Simple markdown splitting for code blocks: ```[lang]\n<code>```
        String rawText = text.toString();

        segmentsContainer.removeAll();

        // Pattern to find code blocks: ```[lang]...```
        // Robust pattern that handles nested blocks by requiring closer to be at start of a line and followed by space/newline/EOF
        Pattern pattern = Pattern.compile("\\s*```([\\w\\-\\+\\#\\.]*)\\R?(.*?)(?:\\s*```\\s*(?=\\R|$)|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawText);

        int lastEnd = 0;
        int codeIdx = 0;
        while (matcher.find()) {
            // Text before code block
            String textBefore = rawText.substring(lastEnd, matcher.start()).trim();
            if (!textBefore.isEmpty()) {
                addTextSegment(textBefore, theme);
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

            final int finalCodeIdx = codeIdx;
            CollapsibleCodePane codePane = new CollapsibleCodePane(lang, code, defaultExpanded) {
                @Override
                public void revalidate() {
                    super.revalidate();
                    // Update state when toggled
                    // Note: This is a hacky way to track state without deep listeners
                }
            };

            // Add a mouse listener to the header to track state changes
            // (Assuming CollapsibleCodePane handles its own internal clicks, but we want to update our list)
            // For now, simple rebuild is fine.

            segmentsContainer.add(codePane);

            lastEnd = matcher.end();
            codeIdx++;
        }

        // Remaining text after last code block
        if (lastEnd < rawText.length()) {
            String remaining = rawText.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                addTextSegment(remaining, theme);
            }
        }

        segmentsContainer.revalidate();
        segmentsContainer.repaint();
    }

    private void addTextSegment(String markdown, ThemeManager.Theme theme) {
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        
        String markdownWithTables = renderTablesAsHtml(markdown);
        String html = renderer.render(parser.parse(markdownWithTables));
        
        html = html.replace("<table>", "<table border='1' style='border-collapse: collapse; width: 100%;'>");
        html = html.replace("<th>", "<th style='background: #f0f0f0; padding: 8px; border: 1px solid #ddd;'>");
        html = html.replace("<td>", "<td style='padding: 8px; border: 1px solid #ddd;'>");
        html = html.replace("<tr>", "<tr>");

        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setOpaque(false);

        Color bg;
        if ("user".equals(type)) {
            bg = theme.getBubbleUser();
        } else if ("error".equals(type)) {
            bg = new Color(255, 235, 238);
        } else {
            bg = theme.getBackground();
        }

        boolean isAssistant = !"user".equals(type) && !"error".equals(type) && !"tool".equals(type);
        String customCss = theme.toCss(bg, isAssistant);
        if ("error".equals(type)) {
            customCss += " body { color: #D32F2F; font-weight: bold; }";
        } else if ("tool".equals(type)) {
            customCss += " body { color: #777777; font-size: 13px; }";
        }

        String styledHtml = "<html><head><style>" + customCss + "</style></head><body style='font-family: sans-serif; margin: 0;'>" + html + "</body></html>";
        pane.setText(styledHtml);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        segmentsContainer.add(pane);
        segmentsContainer.add(Box.createVerticalStrut(8));
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
            
            if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                String trimmedLine = line.trim();
                // Strip leading and trailing pipes
                String content = trimmedLine.substring(1, trimmedLine.length() - 1);
                // Split by pipes not preceded by a backslash
                String[] cells = content.split("(?<!\\\\)\\|", -1);                
                List<String> rowCells = new ArrayList<>();
                for (String cell : cells) {
                    rowCells.add(cell.trim().replace("\\|", "|"));
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
