package github.anandb.netbeans.support;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Restricts {@code run_command} with an allowlist and a denylist of executable
 * names or glob patterns ({@code *} and {@code ?}). With both empty the previous behaviour is kept (any command); as soon
 * as either list is set, shell operators and environment assignments are
 * rejected so an allowed binary cannot chain an arbitrary one. A binary on both
 * lists is denied.
 *
 * <p>This is a guardrail, not a sandbox: an allowlisted interpreter ({@code bash},
 * {@code python}) can still execute arbitrary code by design.</p>
 */
public final class RunCommandPolicy {

    /** Shell control operators and command substitution. */
    private static final Pattern SHELL_OPERATORS = Pattern.compile("[;&|<>`]|\\$\\(");

    /** A single glob star, which matches every executable name. */
    private static final String MATCH_ALL = "*";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private RunCommandPolicy() {
    }

    /** Parses the comma/space-separated setting into lower-case executable names. */
    public static Set<String> parse(String allowlist) {
        Set<String> names = new LinkedHashSet<>();
        if (allowlist == null || allowlist.isBlank()) {
            return names;
        }
        for (String token : allowlist.split("[,\\s]+")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            names.add(executableName(name));
        }
        return names;
    }

    /**
     * Checks one command against the allowlist.
     *
     * @return the rejection message, or {@code null} when the command may run
     */
    public static String rejection(String command, String allowlist, String denylist) {
        Set<String> allowed = parse(allowlist);
        Set<String> denied = parse(denylist);
        if (allowed.isEmpty() && denied.isEmpty()) {
            return null;
        }
        if (command == null || command.isBlank()) {
            return "command is required";
        }
        if (SHELL_OPERATORS.matcher(command).find()) {
            return "Shell operators are not allowed while a run_command allowlist is set.";
        }
        String first = WHITESPACE.split(command.trim(), 2)[0];
        if (first.indexOf('=') >= 0) {
            return "Environment assignments are not allowed while a run_command allowlist is set.";
        }
        String executable = executableName(first);
        if (matchesAny(executable, denied)) {
            return "Command is denied: " + executable + ".";
        }
        if (!allowed.isEmpty() && !matchesAny(executable, allowed)) {
            return "Command not allowlisted: " + executable
                    + ". Allowed: " + String.join(", ", allowed) + ".";
        }
        return null;
    }

    /**
     * True when no command can run: the denylist contains {@code *}.
     * Used by the policy editor to explain the setting.
     */
    public static boolean deniesAllCommands(String allowlist, String denylist) {
        return parse(denylist).contains(MATCH_ALL);
    }

    /**
     * True when every command is permitted: nothing is denied and the allowlist
     * is empty or {@code *}.
     */
    public static boolean allowsAllCommands(String allowlist, String denylist) {
        Set<String> denied = parse(denylist);
        if (!denied.isEmpty()) {
            return false;
        }
        Set<String> allowed = parse(allowlist);
        return allowed.isEmpty() || allowed.contains(MATCH_ALL);
    }

    /** Matches an executable name against glob patterns ({@code *} and {@code ?}). */
    private static boolean matchesAny(String executable, Set<String> patterns) {
        for (String pattern : patterns) {
            if (Pattern.compile(globToRegex(pattern)).matcher(executable).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Converts a simple glob to a regex, escaping every other character. */
    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder(glob.length() * 2);
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\[](){}<>^$|.+".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
        }
        return regex.toString();
    }

    /** Last path segment, lower-cased, so {@code ./mvnw} matches {@code mvnw}. */
    private static String executableName(String token) {
        String normalized = token.replace('\\', '/');
        String name = new File(normalized).getName();
        return name.toLowerCase(Locale.ROOT);
    }
}
