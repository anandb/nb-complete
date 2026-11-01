package github.anandb.netbeans.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local-echo correlation for steered messages: each steer echo is skipped exactly once. */
class SteerEchoesTest {

    @BeforeEach
    @AfterEach
    void reset() {
        SteerEchoes.clear();
    }

    @Test
    void expectedEchoIsConsumedOnce() {
        SteerEchoes.expect("s1", "steer_1");

        assertTrue(SteerEchoes.consume("s1", "steer_1"));
        assertFalse(SteerEchoes.consume("s1", "steer_1"), "the echo renders only once");
    }

    @Test
    void unrelatedMessageIdsAreNotConsumed() {
        SteerEchoes.expect("s1", "steer_1");

        assertFalse(SteerEchoes.consume("s1", "other"));
        assertFalse(SteerEchoes.consume("s2", "steer_1"), "expectations are per session");
    }

    @Test
    void clearingASessionLetsTheEchoRender() {
        SteerEchoes.expect("s1", "steer_1");
        SteerEchoes.clear("s1");

        assertFalse(SteerEchoes.consume("s1", "steer_1"));
    }

    @Test
    void blankIdsAreIgnored() {
        SteerEchoes.expect("s1", null);
        SteerEchoes.expect("s1", "");
        SteerEchoes.expect(null, "steer_1");

        assertFalse(SteerEchoes.consume("s1", null));
        assertFalse(SteerEchoes.consume(null, "steer_1"));
    }
}
