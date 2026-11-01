package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Major version in {@code java -version} output, e.g. {@code version "21.0.5"}. */
public final class JavaVersionPattern {
    private static final Pattern PATTERN = Pattern.compile("version \"(\\d+)");

    private JavaVersionPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
