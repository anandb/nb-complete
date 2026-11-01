package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** goose {@code <kind> · <detail>} title prefix. */
public final class ToolTitlePrefixPattern {
    private static final Pattern PATTERN = Pattern.compile("^([a-z][a-z0-9_-]*)\\s+\\u00b7\\s+");

    private ToolTitlePrefixPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
