package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits a Markdown table row on unescaped {@code |}. */
public final class TableCellSplitPattern {
    private static final Pattern PATTERN = Pattern.compile("(?<!\\\\)\\|");

    private TableCellSplitPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String[] split(CharSequence input) {
        return PATTERN.split(input);
    }

    public static String[] split(CharSequence input, int limit) {
        return PATTERN.split(input, limit);
    }
}
