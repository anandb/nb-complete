package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs of whitespace, for splitting and collapsing. */
public final class WhitespacePattern {
    private static final Pattern PATTERN = Pattern.compile("\\s+");

    private WhitespacePattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String[] split(CharSequence input) {
        return PATTERN.split(input);
    }

    public static String[] split(CharSequence input, int limit) {
        return PATTERN.split(input, limit);
    }

    public static String collapseToSingleSpaces(String input) {
        return input == null ? "" : PATTERN.matcher(input).replaceAll(" ").trim();
    }
}
