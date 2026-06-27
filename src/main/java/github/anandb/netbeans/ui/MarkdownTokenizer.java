package github.anandb.netbeans.ui;

import java.text.StringCharacterIterator;

/**
 * Tokenizes markdown text into structured elements for rendering.
 * Handles inline formatting detection (bold, italic, code, strikethrough).
 */
final class MarkdownTokenizer {

    private MarkdownTokenizer() {
    }

    /**
     * Result of scanning for the next inline formatting marker.
     */
    record FormatMatch(int position, String type) {
    }

    /**
     * Scans {@code line} starting at {@code startIndex} for the next inline
     * formatting marker ({@code **}, {@code *}, {@code ~~}, {@code `}).
     *
     * @return the next match, or {@code null} if none found
     */
    static FormatMatch nextFormatMarker(String line, int startIndex) {
        int boldStart = line.indexOf("**", startIndex);
        while (boldStart != -1 && isEscaped(line, boldStart)) {
            boldStart = line.indexOf("**", boldStart + 2);
        }
        int italicStart = line.indexOf("*", startIndex);
        while (italicStart != -1 && isEscaped(line, italicStart)) {
            italicStart = line.indexOf("*", italicStart + 1);
        }
        int strikeStart = line.indexOf("~~", startIndex);
        while (strikeStart != -1 && isEscaped(line, strikeStart)) {
            strikeStart = line.indexOf("~~", strikeStart + 2);
        }
        int codeStart = line.indexOf("`", startIndex);
        while (codeStart != -1 && isEscaped(line, codeStart)) {
            codeStart = line.indexOf("`", codeStart + 1);
        }

        int best = Integer.MAX_VALUE;
        String bestType = null;
        if (boldStart != -1 && boldStart < best) {
            best = boldStart;
            bestType = "BOLD";
        }
        if (italicStart != -1 && italicStart < best && italicStart != boldStart) {
            best = italicStart;
            bestType = "ITALIC";
        }
        if (strikeStart != -1 && strikeStart < best) {
            best = strikeStart;
            bestType = "STRIKE";
        }
        if (codeStart != -1 && codeStart < best) {
            best = codeStart;
            bestType = "CODE";
        }
        return bestType != null ? new FormatMatch(best, bestType) : null;
    }

    /**
     * Finds the closing marker for the given format type starting after {@code markerStart}.
     *
     * @return the index of the closing marker, or -1 if not found
     */
    static int findClosingMarker(String line, String type, int markerStart) {
        String marker = switch (type) {
            case "BOLD" -> "**";
            case "STRIKE" -> "~~";
            case "ITALIC" -> "*";
            case "CODE" -> "`";
            default -> type;
        };
        int markerLen = ("BOLD".equals(type) || "STRIKE".equals(type)) ? 2 : 1;
        int pos = line.indexOf(marker, markerStart + markerLen);
        while (pos != -1 && isEscaped(line, pos)) {
            pos = line.indexOf(marker, pos + markerLen);
        }
        return pos;
    }

    /**
     * Returns the length of the opening marker for the given type.
     */
    static int markerLength(String type) {
        return ("BOLD".equals(type) || "STRIKE".equals(type)) ? 2 : 1;
    }

    static boolean isEscaped(String s, int index) {
        int count = 0;
        int i = index - 1;
        while (i >= 0 && s.charAt(i) == '\\') {
            count++;
            i--;
        }
        return count % 2 != 0;
    }

    static String unescape(String s) {
        if (s == null || !s.contains("\\")) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        StringCharacterIterator it = new StringCharacterIterator(s);
        while (it.current() != StringCharacterIterator.DONE) {
            char c = it.current();
            if (c == '\\') {
                char next = it.next();
                if (next != StringCharacterIterator.DONE && (next == '\\' || next == '*' || next == '`' || next == '~' || next == '|')) {
                    sb.append(next);
                    it.next();
                    continue;
                }
                it.previous();
            }
            sb.append(c);
            it.next();
        }
        return sb.toString();
    }
}
