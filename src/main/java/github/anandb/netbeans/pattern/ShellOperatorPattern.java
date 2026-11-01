package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shell control operators and command substitution. */
public final class ShellOperatorPattern {
    private static final Pattern PATTERN = Pattern.compile("[;&|<>`]|\\$\\(");

    private ShellOperatorPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean containsOperator(String command) {
        return command != null && PATTERN.matcher(command).find();
    }
}
