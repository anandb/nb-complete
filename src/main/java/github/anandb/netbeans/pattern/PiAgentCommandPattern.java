package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** PI agent permission title: {@code Current agent requested bash command '...'}. */
public final class PiAgentCommandPattern {
    private static final Pattern PATTERN =
            Pattern.compile("Current agent requested bash command '(.+?)' ");

    private PiAgentCommandPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
