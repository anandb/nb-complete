package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code {{slot}}} placeholder in SQL templates. */
public final class SqlSlotPattern {
    private static final Pattern PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}");

    private SqlSlotPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
