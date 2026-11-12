package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code <system-reminder>} block injected by a harness into echoed message text. */
public final class SystemReminderPattern {
    private static final Pattern PATTERN =
            Pattern.compile("<system-reminder>.*?</system-reminder>", Pattern.DOTALL);

    private SystemReminderPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String stripFrom(String text) {
        return text == null ? null : PATTERN.matcher(text).replaceAll("");
    }
}
