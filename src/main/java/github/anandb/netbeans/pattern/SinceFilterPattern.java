package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date filter for {@code git log --since} / {@code hg log --date}. */
public final class SinceFilterPattern {
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9 .:/_+~-]+$");

    private SinceFilterPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String input) {
        return input != null && PATTERN.matcher(input).matches();
    }
}
