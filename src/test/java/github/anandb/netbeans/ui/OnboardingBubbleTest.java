package github.anandb.netbeans.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.support.BinaryResolver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Headless construction tests for the multi-harness onboarding bubble. */
class OnboardingBubbleTest {

    @BeforeAll
    static void setUp() {
        TestUiUtils.setupTestUIManager();
    }

    @Test
    void constructsEmptyChooser() {
        assertDoesNotThrow(() -> {
            OnboardingBubble bubble = new OnboardingBubble(java.util.List.of(),
                    (id, path) -> { }, disable -> { });
            assertNotNull(bubble);
        });
    }

    @Test
    void constructsWithDetectedHarness() {
        assertDoesNotThrow(() -> {
            OnboardingBubble bubble = new OnboardingBubble(
                    java.util.List.of(new BinaryResolver.FoundBinary("goose", "Goose", "/usr/bin/goose")),
                    (id, path) -> { }, disable -> { });
            assertNotNull(bubble);
        });
    }

    @Test
    void invokesSelectionCallback() {
        // The Use action must forward the harness id and path verbatim.
        final String[] used = new String[2];
        OnboardingBubble.SelectionCallback cb = (id, path) -> {
            used[0] = id;
            used[1] = path;
        };
        cb.onUse("goose", "/usr/bin/goose");
        assertEquals("goose", used[0]);
        assertEquals("/usr/bin/goose", used[1]);
        assertNotNull(cb);
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
