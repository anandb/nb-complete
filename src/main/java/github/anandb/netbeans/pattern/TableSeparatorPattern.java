package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standard Markdown table separator cell: {@code :--}, {@code --:}, etc. */
public final class TableSeparatorPattern {
    private static final Pattern PATTERN = Pattern.compile("^:?-+:?$");

    private TableSeparatorPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String input) {
        return input != null && PATTERN.matcher(input).matches();
    }
}
