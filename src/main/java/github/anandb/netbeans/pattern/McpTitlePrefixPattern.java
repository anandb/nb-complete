package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MCP {@code nb: <tool>} title prefix. */
public final class McpTitlePrefixPattern {
    private static final Pattern PATTERN = Pattern.compile("^(?:nb|mcp):\\s*");

    private McpTitlePrefixPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
