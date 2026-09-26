package github.anandb.netbeans.support;

import java.util.Locale;

/**
 * Pure formatting for the token-stats panel: thousands separators, USD amounts
 * and K/M token magnitudes. No Swing, no theme, no I/O — safe to unit test
 * headless.
 */
public final class UsageStatsFormat {

    private UsageStatsFormat() {
    }

    /** Formats an integer with thousands separators, e.g. {@code 1,293}. */
    public static String thousands(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** Formats a USD amount with a symbol and exactly two decimals, e.g. {@code $1.53}. */
    public static String cost(double amount) {
        return "$" + String.format(Locale.ROOT, "%.2f", amount);
    }

    /**
     * Abbreviates a token magnitude at K/M with one decimal, e.g. {@code 77.9K},
     * {@code 2.8M}, {@code 3412.6M}. Values below 1000 are rendered as integers.
     * The reference does not roll millions up to billions.
     */
    public static String abbreviated(double value) {
        double magnitude = Math.abs(value);
        if (magnitude >= 1_000_000d) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000d);
        }
        if (magnitude >= 1_000d) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000d);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
