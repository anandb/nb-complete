package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code -- name: <key>} block marker in {@code queries.sql}. */
public final class SqlNamePattern {
    private static final Pattern PATTERN = Pattern.compile("(?m)^--\\s*name:\\s*([a-z0-9_]+)\\s*$");

    private SqlNamePattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
