package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code "filePath": "..."} target in raw tool-call JSON. */
public final class FilePathJsonPattern {
    private static final Pattern PATTERN = Pattern.compile("\"filePath\"\\s*:\\s*\"([^\"]{10,})\"");

    private FilePathJsonPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
