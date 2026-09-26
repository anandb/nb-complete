package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsageStatsFormatTest {

    @Test
    void thousandsAddsSeparators() {
        assertEquals("0", UsageStatsFormat.thousands(0));
        assertEquals("1,293", UsageStatsFormat.thousands(1293));
        assertEquals("42,972", UsageStatsFormat.thousands(42972));
    }

    @Test
    void costUsesASymbolAndTwoDecimals() {
        assertEquals("$0.00", UsageStatsFormat.cost(0));
        assertEquals("$1.50", UsageStatsFormat.cost(1.5));
        assertEquals("$137.33", UsageStatsFormat.cost(137.33));
    }

    @Test
    void abbreviatedUsesKAndMOnly() {
        assertEquals("512", UsageStatsFormat.abbreviated(512));
        assertEquals("77.9K", UsageStatsFormat.abbreviated(77_900));
        assertEquals("2.8M", UsageStatsFormat.abbreviated(2_800_000));
        // The reference does not roll millions up to billions.
        assertEquals("3412.6M", UsageStatsFormat.abbreviated(3_412_600_000L));
    }
}
