package github.anandb.netbeans.pattern;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits {@code PATH}-style values on the platform path separator. */
public final class PathSeparatorPattern {
    private static final Pattern PATTERN = Pattern.compile(Pattern.quote(File.pathSeparator));

    private PathSeparatorPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String[] split(CharSequence input) {
        return PATTERN.split(input);
    }
}
