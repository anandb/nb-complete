package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Characters unsafe in file names and preference keys; replaced with {@code _}. */
public final class TitleSanitizePattern {
    private static final Pattern PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");

    private TitleSanitizePattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String sanitize(String input) {
        return input == null ? "" : PATTERN.matcher(input).replaceAll("_");
    }
}
