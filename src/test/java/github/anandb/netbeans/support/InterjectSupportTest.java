package github.anandb.netbeans.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The probe gate: unknown (not yet answered) and false both mean "queue", not "steer". */
class InterjectSupportTest {

    @BeforeEach
    void reset() {
        InterjectSupport.reset();
    }

    @Test
    void unknownIsNotSupported() {
        assertFalse(InterjectSupport.isSupported(), "a pending probe must queue");
    }

    @Test
    void probeAnswerIsHonored() {
        InterjectSupport.set(true);
        assertTrue(InterjectSupport.isSupported());
        InterjectSupport.set(false);
        assertFalse(InterjectSupport.isSupported());
    }

    @Test
    void resetForgetsTheAnswer() {
        InterjectSupport.set(true);
        InterjectSupport.reset();
        assertFalse(InterjectSupport.isSupported());
    }
}
