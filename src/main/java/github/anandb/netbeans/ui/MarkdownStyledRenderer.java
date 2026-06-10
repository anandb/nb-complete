package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Renders markdown text into a {@link JTextPane} with a {@link StyledDocument}.
 * Supports code blocks, tables, headers, bold, italic, and inline code.
 * <p>
 * Avoids the Swing HTML renderer entirely, eliminating the reflow and
 * width-calculation bugs that plague {@link javax.swing.JEditorPane}.
 */
public final class MarkdownStyledRenderer {

    private MarkdownStyledRenderer() {
    }

    /**
     * Creates a {@link JTextPane} with markdown content rendered as styled text.
     */
    public static JTextPane render(String markdown, ColorTheme theme) {
        JTextPane pane = new JTextPane() {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Short.MAX_VALUE, pref.height);
            }
        };
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBackground(null);
        pane.setFont(ThemeManager.getFont().deriveFont(ThemeManager.getFont().getSize() - 1f));
        pane.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 20, 8, 6));

        StyledDocument doc = pane.getStyledDocument();
        Font baseFont = ThemeManager.getFont();
        Color fg = theme.foreground();
        Color codeFg = theme.isDark() ? Color.WHITE : Color.BLACK;

        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setFontFamily(base, baseFont.getFamily());
        StyleConstants.setFontSize(base, baseFont.getSize() - 1);
        StyleConstants.setForeground(base, fg);
        StyleConstants.setSpaceAbove(base, 4);
        StyleConstants.setSpaceBelow(base, 4);
        doc.setParagraphAttributes(0, doc.getLength(), base, true);

        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();
        List<String> tableBuffer = new ArrayList<>();

        try {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];

                if (line.startsWith("```")) {
                    if (inCodeBlock) {
                        // End of code block
                        SimpleAttributeSet codeAttr = new SimpleAttributeSet();
                        StyleConstants.setFontFamily(codeAttr, "monospace");
                        StyleConstants.setFontSize(codeAttr, baseFont.getSize());
                        StyleConstants.setForeground(codeAttr, codeFg);
                        StyleConstants.setSpaceAbove(codeAttr, 8);
                        StyleConstants.setSpaceBelow(codeAttr, 8);
                        String code = codeBuffer.toString();
                        if (code.endsWith("\n")) {
                            code = code.substring(0, code.length() - 1);
                        }
                        doc.insertString(doc.getLength(), code + "\n", codeAttr);
                        codeBuffer.setLength(0);
                        inCodeBlock = false;
                    } else {
                        inCodeBlock = true;
                    }
                    continue;
                }

                if (inCodeBlock) {
                    codeBuffer.append(line).append("\n");
                    continue;
                }

                // Table detection
                if (line.trim().startsWith("|")) {
                    tableBuffer.add(line);
                    continue;
                } else if (!tableBuffer.isEmpty()) {
                    // End of table — flush it
                    insertTable(doc, tableBuffer, base, codeFg);
                    tableBuffer.clear();
                }

                // Headers
                int headerLevel = 0;
                String tempLine = line;
                while (tempLine.startsWith("#") && headerLevel < 6) {
                    headerLevel++;
                    tempLine = tempLine.substring(1);
                }
                if (headerLevel > 0 && (tempLine.isEmpty() || tempLine.startsWith(" "))) {
                    line = tempLine.trim();
                    SimpleAttributeSet hAttr = new SimpleAttributeSet(base);
                    StyleConstants.setBold(hAttr, true);
                    int size = baseFont.getSize() + 1;
                    if (headerLevel == 1) {
                        size += 6;
                    } else if (headerLevel == 2) {
                        size += 4;
                    } else if (headerLevel == 3) {
                        size += 2;
                    }
                    StyleConstants.setFontSize(hAttr, size);
                    StyleConstants.setSpaceAbove(hAttr, 8);
                    StyleConstants.setSpaceBelow(hAttr, 4);
                    doc.insertString(doc.getLength(), line + "\n", hAttr);
                    continue;
                }

                // Blockquotes
                SimpleAttributeSet currentBase = base;
                boolean isBlockquote = false;
                if (line.trim().startsWith(">")) {
                    isBlockquote = true;
                    int startIdx = line.indexOf('>');
                    line = line.substring(startIdx + 1);
                    if (line.startsWith(" ")) {
                        line = line.substring(1);
                    }
                    currentBase = new SimpleAttributeSet(base);
                    StyleConstants.setItalic(currentBase, true);
                    StyleConstants.setLeftIndent(currentBase, 16f);
                }

                int startOffset = doc.getLength();

                // Inline formatting
                line = line + "\n";
                int idx = 0;
                while (idx < line.length()) {
                    // Bold **text**
                    int boldStart = line.indexOf("**", idx);
                    while (boldStart != -1 && isEscaped(line, boldStart)) {
                        boldStart = line.indexOf("**", boldStart + 2);
                    }
                    // Italic *text*
                    int italicStart = line.indexOf("*", idx);
                    while (italicStart != -1 && isEscaped(line, italicStart)) {
                        italicStart = line.indexOf("*", italicStart + 1);
                    }
                    // Strikethrough ~~text~~
                    int strikeStart = line.indexOf("~~", idx);
                    while (strikeStart != -1 && isEscaped(line, strikeStart)) {
                        strikeStart = line.indexOf("~~", strikeStart + 2);
                    }
                    // Code `text`
                    int codeStart = line.indexOf("`", idx);
                    while (codeStart != -1 && isEscaped(line, codeStart)) {
                        codeStart = line.indexOf("`", codeStart + 1);
                    }

                    int nextSpecial = Integer.MAX_VALUE;
                    String specialType = null;
                    if (boldStart != -1 && boldStart < nextSpecial) {
                        nextSpecial = boldStart;
                        specialType = "BOLD";
                    }
                    if (italicStart != -1 && italicStart < nextSpecial && italicStart != boldStart) {
                        nextSpecial = italicStart;
                        specialType = "ITALIC";
                    }
                    if (strikeStart != -1 && strikeStart < nextSpecial) {
                        nextSpecial = strikeStart;
                        specialType = "STRIKE";
                    }
                    if (codeStart != -1 && codeStart < nextSpecial) {
                        nextSpecial = codeStart;
                        specialType = "CODE";
                    }

                    if (specialType == null) {
                        // No more formatting — insert rest as plain text
                        if (idx < line.length()) {
                            doc.insertString(doc.getLength(), unescape(line.substring(idx)), currentBase);
                        }
                        break;
                    }

                    // Insert plain text before the special marker
                    if (nextSpecial > idx) {
                        doc.insertString(doc.getLength(), unescape(line.substring(idx, nextSpecial)), currentBase);
                    }

                    // Find the closing marker
                    int end = -1;
                    if ("BOLD".equals(specialType)) {
                        end = line.indexOf("**", nextSpecial + 2);
                        while (end != -1 && isEscaped(line, end)) {
                            end = line.indexOf("**", end + 2);
                        }
                        if (end != -1) {
                            SimpleAttributeSet b = new SimpleAttributeSet(currentBase);
                            StyleConstants.setBold(b, true);
                            doc.insertString(doc.getLength(), unescape(line.substring(nextSpecial + 2, end)), b);
                            idx = end + 2;
                        }
                    } else if ("ITALIC".equals(specialType)) {
                        end = line.indexOf("*", nextSpecial + 1);
                        while (end != -1 && isEscaped(line, end)) {
                            end = line.indexOf("*", end + 1);
                        }
                        if (end != -1) {
                            SimpleAttributeSet italicAttr = new SimpleAttributeSet(currentBase);
                            StyleConstants.setItalic(italicAttr, true);
                            doc.insertString(doc.getLength(), unescape(line.substring(nextSpecial + 1, end)), italicAttr);
                            idx = end + 1;
                        }
                    } else if ("STRIKE".equals(specialType)) {
                        end = line.indexOf("~~", nextSpecial + 2);
                        while (end != -1 && isEscaped(line, end)) {
                            end = line.indexOf("~~", end + 2);
                        }
                        if (end != -1) {
                            SimpleAttributeSet sAttr = new SimpleAttributeSet(currentBase);
                            StyleConstants.setStrikeThrough(sAttr, true);
                            doc.insertString(doc.getLength(), unescape(line.substring(nextSpecial + 2, end)), sAttr);
                            idx = end + 2;
                        }
                    } else if ("CODE".equals(specialType)) {
                        end = line.indexOf("`", nextSpecial + 1);
                        while (end != -1 && isEscaped(line, end)) {
                            end = line.indexOf("`", end + 1);
                        }
                        if (end != -1) {
                            SimpleAttributeSet c = new SimpleAttributeSet(currentBase);
                            StyleConstants.setFontFamily(c, "monospace");
                            StyleConstants.setFontSize(c, baseFont.getSize());
                            StyleConstants.setForeground(c, codeFg);
                            doc.insertString(doc.getLength(), unescape(line.substring(nextSpecial + 1, end)), c);
                            idx = end + 1;
                        }
                    }

                    if (end == -1) {
                        // No closing marker found — insert the marker as plain text
                        int markerLen = "BOLD".equals(specialType) || "STRIKE".equals(specialType) ? 2 : 1;
                        doc.insertString(doc.getLength(), line.substring(nextSpecial, nextSpecial + markerLen), currentBase);
                        idx = nextSpecial + markerLen;
                    }
                }

                if (isBlockquote) {
                    doc.setParagraphAttributes(startOffset, doc.getLength() - startOffset, currentBase, false);
                }
            }

            // Flush remaining table at end of document
            if (!tableBuffer.isEmpty()) {
                insertTable(doc, tableBuffer, base, codeFg);
            }
        } catch (BadLocationException e) {
            // Should never happen with valid indices
            throw new RuntimeException(e);
        }

        return pane;
    }

    /**
     * Formats a markdown table as monospaced text with column alignment and
     * inserts it into the document.  The separator row (e.g. |---|---|) is
     * skipped; headers are bolded.
     */
    private static void insertTable(StyledDocument doc, List<String> tableBuffer,
                                     SimpleAttributeSet base, Color codeFg) throws BadLocationException {
        if (tableBuffer.isEmpty()) {
            return;
        }

        // Determine max column widths
        List<List<String>> rows = new ArrayList<>();
        int maxCols = 0;
        for (String line : tableBuffer) {
            String[] cells = line.split("(?<!\\\\)\\|");
            List<String> row = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                if (i == 0 || i == cells.length - 1) {
                    // Skip leading/trailing empty cells caused by outer pipes
                    if (cells[i].trim().isEmpty()) {
                        continue;
                    }
                }
                row.add(unescape(cells[i].trim()));
            }
            if (!row.isEmpty()) {
                rows.add(row);
                maxCols = Math.max(maxCols, row.size());
            }
        }

        if (rows.isEmpty()) {
            return;
        }

        // Check if second row is a separator (contains only dashes and colons)
        boolean hasHeader = false;
        if (rows.size() >= 2 && isSeparatorRow(rows.get(1))) {
            hasHeader = true;
        }

        // Calculate column widths
        int[] widths = new int[maxCols];
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        // Ensure minimum width for readability
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(widths[i], 3);
        }

        // Insert as monospaced block
        SimpleAttributeSet tableAttr = new SimpleAttributeSet();
        StyleConstants.setFontFamily(tableAttr, "monospace");
        StyleConstants.setFontSize(tableAttr, StyleConstants.getFontSize(base) - 1);
        StyleConstants.setForeground(tableAttr, codeFg);
        StyleConstants.setSpaceAbove(tableAttr, 4);
        StyleConstants.setSpaceBelow(tableAttr, 4);

        for (int r = 0; r < rows.size(); r++) {
            if (hasHeader && r == 1) {
                continue; // Skip separator row
            }
            List<String> row = rows.get(r);
            StringBuilder rowSb = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                rowSb.append(cell);
                // Pad to column width
                for (int j = cell.length(); j < widths[i]; j++) {
                    rowSb.append(' ');
                }
                if (i < row.size() - 1) {
                    rowSb.append("  "); // 2-space gap between columns
                }
            }
            rowSb.append("\n");

            SimpleAttributeSet rowAttr = new SimpleAttributeSet(tableAttr);
            if (hasHeader && r == 0) {
                StyleConstants.setBold(rowAttr, true);
            }
            doc.insertString(doc.getLength(), rowSb.toString(), rowAttr);
        }
    }

    private static boolean isSeparatorRow(List<String> row) {
        for (String cell : row) {
            String trimmed = cell.trim();
            if (!trimmed.isEmpty() && !trimmed.matches("^:?-+:?$")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEscaped(String s, int index) {
        int count = 0;
        int i = index - 1;
        while (i >= 0 && s.charAt(i) == '\\') {
            count++;
            i--;
        }
        return count % 2 != 0;
    }

    private static String unescape(String s) {
        if (s == null || !s.contains("\\")) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == '\\' || next == '*' || next == '`' || next == '~' || next == '|') {
                    sb.append(next);
                    i++; // skip next character
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
