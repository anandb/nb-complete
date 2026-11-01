package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Valid mdproject name: word char followed by word chars, dots or dashes. */
public final class ProjectNamePattern {
    private static final Pattern PATTERN = Pattern.compile("\\w[\\w\\-.]*");

    private ProjectNamePattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String name) {
        return name != null && PATTERN.matcher(name).matches();
    }
}
