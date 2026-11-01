package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Leading {@code fatal: } prefix lines in git output. */
public final class FatalPrefixPattern {
    private static final Pattern PATTERN = Pattern.compile("(?m)^fatal: ");

    private FatalPrefixPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String stripFrom(String output) {
        return output == null ? "" : PATTERN.matcher(output).replaceAll("");
    }
}
