package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.List;

public final class TableDetector {

    private TableDetector() {}

    public record TableResult(List<Segment> segments, int componentCount) {}

    public sealed interface Segment permits TextSegment, TableSegment {
        String content();
    }

    public record TextSegment(String text) implements Segment {
        @Override public String content() { return text; }
    }

    public record TableSegment(String markdown) implements Segment {
        @Override public String markdown() { return markdown; }

        @Override public String content() { return markdown; }
    }

    public static TableResult detectTables(String text, boolean incremental) {
        if (text.isEmpty()) {
            return new TableResult(List.of(), 0);
        }

        if (incremental) {
            return new TableResult(List.of(new TextSegment(text)), 1);
        }

        if (!text.contains("|")) {
            return new TableResult(List.of(new TextSegment(text)), 1);
        }

        // Walk newlines via indexOf('\n') to avoid allocating a String[] for
        // the entire document.
        List<Segment> segments = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();
        List<String> tableLines = new ArrayList<>();
        boolean inTable = false;
        boolean headerFound = false;

        int lineStart = 0;
        int docLen = text.length();
        while (lineStart <= docLen) {
            int nl = text.indexOf('\n', lineStart);
            boolean hasMore = nl >= 0;
            String line = hasMore ? text.substring(lineStart, nl) : text.substring(lineStart);
            lineStart = hasMore ? nl + 1 : docLen + 1;
            // Determine whether this is the last line (no trailing newline).
            boolean isLast = !hasMore;
            String trimmed = line.trim();
            boolean isTableRow = !trimmed.isEmpty() && trimmed.charAt(0) == '|'
                    && trimmed.charAt(trimmed.length() - 1) == '|';

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
                                segments.add(new TextSegment(textBuffer.toString()));
                                textBuffer.setLength(0);
                            }
                        }
                    }
                }
            } else {
                if (inTable) {
                    if (headerFound) {
                        segments.add(new TableSegment(String.join("\n", tableLines)));
                    } else {
                        for (String l : tableLines) {
                            textBuffer.append(l).append('\n');
                        }
                    }
                    tableLines.clear();
                    inTable = false;
                    headerFound = false;
                }
                textBuffer.append(line);
                if (!isLast) {
                    textBuffer.append('\n');
                }
            }
        }

        if (inTable && headerFound) {
            segments.add(new TableSegment(String.join("\n", tableLines)));
        } else if (inTable) {
            for (String l : tableLines) {
                textBuffer.append(l).append('\n');
            }
        }

        if (textBuffer.length() > 0) {
            segments.add(new TextSegment(textBuffer.toString()));
        }

        return new TableResult(segments, segments.size());
    }
}
