package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe git revision token (hash, ref, range, ref:path); rejects leading {@code -}. */
public final class GitDiffTargetPattern {
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._/~^:-]+$");

    private GitDiffTargetPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String input) {
        return input != null && PATTERN.matcher(input).matches();
    }
}
