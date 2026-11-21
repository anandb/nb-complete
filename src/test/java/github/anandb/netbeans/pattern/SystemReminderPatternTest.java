package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SystemReminderPatternTest {

    @Test
    void matcherFindsReminderBlock() {
        assertTrue(SystemReminderPattern.matcher(
                "x <system-reminder>be nice</system-reminder> y").find());
        assertFalse(SystemReminderPattern.matcher("plain text").find());
    }

    @Test
    void stripFromRemovesBlockAcrossLines() {
        assertEquals("a\n\nb", SystemReminderPattern.stripFrom(
                "a\n<system-reminder>\nbe nice\n</system-reminder>\nb"));
    }

    @Test
    void stripFromRemovesEveryBlock() {
        assertEquals(" keep ", SystemReminderPattern.stripFrom(
                "<system-reminder>one</system-reminder> keep "
                + "<system-reminder>two</system-reminder>"));
    }

    @Test
    void stripFromNullIsNull() {
        assertNull(SystemReminderPattern.stripFrom(null));
    }
}
