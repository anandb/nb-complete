package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arming pulse is one controlled beat: full, two gentle dips, full, then still. */
class PulsingToggleButtonTest {

    private static final float MIN_ALPHA = 0.55f;
    private static final float DELTA = 0.02f;

    @Test
    void startsAndEndsFullyOpaque() {
        assertEquals(1f, PulsingToggleButton.alphaFor(0), 0.0001f);
        assertEquals(1f, PulsingToggleButton.alphaFor(30), 0.0001f);
        assertEquals(1f, PulsingToggleButton.alphaFor(-5), 0.0001f);
    }

    @Test
    void hasTwoDipsAtFullOpacityBetween() {
        assertEquals(MIN_ALPHA, PulsingToggleButton.alphaFor(7), DELTA);
        assertEquals(1f, PulsingToggleButton.alphaFor(15), 0.0001f);
        assertEquals(MIN_ALPHA, PulsingToggleButton.alphaFor(22), DELTA);
    }

    @Test
    void neverDimsBelowTheConfiguredFloor() {
        for (int frame = 0; frame <= 30; frame++) {
            float alpha = PulsingToggleButton.alphaFor(frame);
            assertTrue(alpha >= MIN_ALPHA - 0.0001f, "frame " + frame + " alpha " + alpha);
            assertTrue(alpha <= 1f + 0.0001f, "frame " + frame + " alpha " + alpha);
        }
    }
}
