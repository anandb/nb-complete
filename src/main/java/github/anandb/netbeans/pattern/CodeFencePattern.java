package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fenced code block with optional language tag. */
public final class CodeFencePattern {
    private static final Pattern PATTERN =
            Pattern.compile("```([\\w\\-\\+\\#\\.]*)\\R?(.*?)(?:```(?=\\R|$)|$)", Pattern.DOTALL);

    private CodeFencePattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
