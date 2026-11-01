package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Vim swap/backup files: {@code *.swp}, {@code *.swo}, {@code *~}. */
public final class SwapFilePattern {
    private static final Pattern PATTERN = Pattern.compile(".*\\.(?:swp|swo)$|.*~$");

    private SwapFilePattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String name) {
        return name != null && PATTERN.matcher(name).matches();
    }
}
