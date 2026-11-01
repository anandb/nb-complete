package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code <path>...</path>} target in tool output. */
public final class PathTagPattern {
    private static final Pattern PATTERN = Pattern.compile("<path>([^<]{10,})</path>");

    private PathTagPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
