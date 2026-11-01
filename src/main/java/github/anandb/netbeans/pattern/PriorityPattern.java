package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Single uppercase A-Z letter (todo.txt priority). */
public final class PriorityPattern {
    private static final Pattern PATTERN = Pattern.compile("[A-Z]");

    private PriorityPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String input) {
        return input != null && PATTERN.matcher(input).matches();
    }
}
