package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TableDetector {

    private TableDetector() {}

    private static final Pattern NEWLINE_SPLIT = Pattern.compile("\n");

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

        String[] lines = NEWLINE_SPLIT.split(text, -1);
        List<Segment> segments = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();
        List<String> tableLines = new ArrayList<>();
        boolean inTable = false;
        boolean headerFound = false;

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
            segments.add(new TableSegment(String.join("\n", tableLines)));
        } else if (inTable) {
            for (String l : tableLines) {
                textBuffer.append(l).append("\n");
            }
        }

        if (textBuffer.length() > 0) {
            segments.add(new TextSegment(textBuffer.toString()));
        }

        return new TableResult(segments, segments.size());
    }
}
