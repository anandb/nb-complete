package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Trailing {@code -RELEASE} / {@code .RELEASE} qualifier, stripped for ordering. */
public final class ReleaseSuffixPattern {
    private static final Pattern PATTERN = Pattern.compile("[-.]RELEASE$", Pattern.CASE_INSENSITIVE);

    private ReleaseSuffixPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String stripFrom(String version) {
        return version == null ? "" : PATTERN.matcher(version.trim()).replaceFirst("");
    }
}
