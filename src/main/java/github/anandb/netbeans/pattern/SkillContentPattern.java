package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code <skill_content name="...">} marker in tool output. */
public final class SkillContentPattern {
    private static final Pattern PATTERN = Pattern.compile("<skill_content name=\"([^\"]{2,})\"");

    private SkillContentPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
