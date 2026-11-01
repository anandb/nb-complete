package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Leading {@code Tool:} prefix in collapsible activity headers. */
public final class ToolPrefixPattern {
    private static final Pattern PATTERN = Pattern.compile("(?i)TOOL:?\\s*");

    private ToolPrefixPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String stripPrefix(String title) {
        return title == null ? "" : PATTERN.matcher(title).replaceFirst("").trim();
    }
}
