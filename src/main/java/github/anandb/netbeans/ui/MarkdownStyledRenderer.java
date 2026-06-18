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

    /** Max characters per table cell. Longer content truncated with '\u2026'. */
    private static final int MAX_CELL_WIDTH = 55;

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
        Color codeFg = theme.inlineCodeForeground();

        SimpleAttributeSet base = StyleResolver.baseStyle(baseFont, fg);
        doc.setParagraphAttributes(0, doc.getLength(), base, true);

        // Walk newlines via indexOf('\n') to avoid allocating a String[] for
        // the entire document. Each iteration extracts the next line as a
        // substring view; trim/code/table operations downstream still work
        // on the line String.
        int docLen = markdown.length();
        int lineStart = 0;
        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();
        List<String> tableBuffer = new ArrayList<>();

        try {
            while (lineStart <= docLen) {
                int nl = markdown.indexOf('\n', lineStart);
                String line = (nl < 0) ? markdown.substring(lineStart) : markdown.substring(lineStart, nl);
                lineStart = (nl < 0) ? docLen + 1 : nl + 1;

                if (line.startsWith("```")) {
                    if (inCodeBlock) {
                        SimpleAttributeSet codeAttr = StyleResolver.codeBlockStyle(baseFont, codeFg);
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
                    SimpleAttributeSet hAttr = StyleResolver.headerStyle(base, baseFont, headerLevel);
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
                    currentBase = StyleResolver.blockquoteStyle(base, baseFont, codeFg);
                }

                int startOffset = doc.getLength();

                // Inline formatting — batch adjacent same-style runs into a single
                // insertString call to avoid firing a DocumentEvent per token.
                line = line + "\n";
                int idx = 0;
                StringBuilder runBuf = new StringBuilder();
                SimpleAttributeSet runAttr = currentBase;
                while (idx < line.length()) {
                    MarkdownTokenizer.FormatMatch match = MarkdownTokenizer.nextFormatMarker(line, idx);

                    if (match == null) {
                        if (idx < line.length()) {
                            String tail = MarkdownTokenizer.unescape(line.substring(idx));
                            runAttr = appendRun(doc, runBuf, runAttr, tail, currentBase);
                        }
                        break;
                    }

                    // Insert plain text before the marker
                    if (match.position() > idx) {
                        String plain = MarkdownTokenizer.unescape(line.substring(idx, match.position()));
                        runAttr = appendRun(doc, runBuf, runAttr, plain, currentBase);
                    }

                    int end = MarkdownTokenizer.findClosingMarker(line, match.type(), match.position());
                    int markerLen = MarkdownTokenizer.markerLength(match.type());

                    if (end != -1) {
                        String content = MarkdownTokenizer.unescape(line.substring(match.position() + markerLen, end));
                        SimpleAttributeSet fmtAttr = resolveStyle(match.type(), currentBase, baseFont, codeFg);
                        runAttr = appendRun(doc, runBuf, runAttr, content, fmtAttr);
                        idx = end + markerLen;
                    } else {
                        String marker = line.substring(match.position(), match.position() + markerLen);
                        runAttr = appendRun(doc, runBuf, runAttr, marker, currentBase);
                        idx = match.position() + markerLen;
                    }
                }
                flushRun(doc, runBuf, runAttr);

                if (isBlockquote) {
                    doc.setParagraphAttributes(startOffset, doc.getLength() - startOffset, currentBase, false);
                }
            }

            // Flush remaining table at end of document
            if (!tableBuffer.isEmpty()) {
                insertTable(doc, tableBuffer, base, codeFg);
            }
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        return pane;
    }

    private static SimpleAttributeSet resolveStyle(String type, SimpleAttributeSet base,
                                                    Font baseFont, Color codeFg) {
        return switch (type) {
            case "BOLD" -> StyleResolver.boldStyle(base, baseFont, codeFg);
            case "ITALIC" -> StyleResolver.italicStyle(base, baseFont, codeFg);
            case "STRIKE" -> StyleResolver.strikethroughStyle(base, baseFont, codeFg);
            case "CODE" -> StyleResolver.inlineCodeStyle(base, baseFont, codeFg);
            default -> base;
        };
    }

    /**
     * Appends {@code text} to the run buffer. If the attribute set matches the
     * current run, the text is buffered for a single batched insertString.
     * Otherwise, the existing run is flushed and a new one starts. Returns
     * the (possibly updated) run attribute so the caller can keep the local
     * variable in sync.
     */
    private static SimpleAttributeSet appendRun(StyledDocument doc, StringBuilder runBuf,
                                                SimpleAttributeSet runAttr, String text, SimpleAttributeSet newAttr) {
        if (text.isEmpty()) {
            return runAttr;
        }
        if (runAttr == newAttr || (runAttr != null && runAttr.equals(newAttr))) {
            runBuf.append(text);
            return runAttr;
        }
        flushRun(doc, runBuf, runAttr);
        runBuf.append(text);
        return newAttr;
    }

    /** Flushes the pending run buffer to the document in a single insertString call. */
    private static void flushRun(StyledDocument doc, StringBuilder runBuf, SimpleAttributeSet runAttr) {
        if (runBuf.length() == 0) {
            return;
        }
        try {
            doc.insertString(doc.getLength(), runBuf.toString(), runAttr);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        runBuf.setLength(0);
    }

    private static void insertTable(StyledDocument doc, List<String> tableBuffer,
                                     SimpleAttributeSet base, Color codeFg) throws BadLocationException {
        if (tableBuffer.isEmpty()) {
            return;
        }

        List<List<String>> rows = new ArrayList<>();
        int maxCols = 0;
        for (String line : tableBuffer) {
            String[] cells = line.split("(?<!\\\\)\\|");
            List<String> row = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                if (i == 0 || i == cells.length - 1) {
                    if (cells[i].trim().isEmpty()) {
                        continue;
                    }
                }
                row.add(MarkdownTokenizer.unescape(cells[i].trim()));
            }
            if (!row.isEmpty()) {
                rows.add(row);
                maxCols = Math.max(maxCols, row.size());
            }
        }

        if (rows.isEmpty()) {
            return;
        }

        boolean hasHeader = false;
        if (rows.size() >= 2 && isSeparatorRow(rows.get(1))) {
            hasHeader = true;
        }

        int[] widths = new int[maxCols];
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(widths[i], 3);
        }
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.min(widths[i], MAX_CELL_WIDTH);
        }

        SimpleAttributeSet tableAttr = StyleResolver.tableStyle(base, codeFg);

        for (int r = 0; r < rows.size(); r++) {
            if (hasHeader && r == 1) {
                continue;
            }
            List<String> row = rows.get(r);
            StringBuilder rowSb = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                if (cell.length() > widths[i]) {
                    cell = cell.substring(0, Math.max(1, widths[i] - 1)) + "\u2026";
                }
                rowSb.append(cell);
                for (int j = cell.length(); j < widths[i]; j++) {
                    rowSb.append(' ');
                }
                if (i < row.size() - 1) {
                    rowSb.append("  ");
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
}
