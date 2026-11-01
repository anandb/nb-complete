package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Horizontal-rule section separator: a line containing only {@code ---}. */
public final class SectionSplitPattern {
    private static final Pattern PATTERN = Pattern.compile("(?m)^---[ \\t]*$");

    private SectionSplitPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String[] split(CharSequence input) {
        return PATTERN.split(input);
    }
}
