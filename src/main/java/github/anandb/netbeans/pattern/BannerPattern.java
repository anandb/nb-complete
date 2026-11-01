package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Harness startup banner line, e.g. {@code pi v0.84.4}. */
public final class BannerPattern {
    private static final Pattern PATTERN = Pattern.compile("^(\\S+) v(\\S+)$");

    private BannerPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String input) {
        return input != null && PATTERN.matcher(input).matches();
    }
}
