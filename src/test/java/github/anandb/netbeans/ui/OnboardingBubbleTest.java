package github.anandb.netbeans.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.support.BinaryResolver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                    (id, path) -> { });
            assertNotNull(bubble);
        });
    }

    @Test
    void constructsWithDetectedHarness() {
        assertDoesNotThrow(() -> {
            OnboardingBubble bubble = new OnboardingBubble(
                    java.util.List.of(new BinaryResolver.FoundBinary("goose", "Goose", "/usr/bin/goose")),
                    (id, path) -> { });
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

    @Test
    void closeButtonPresentOnlyWhenDismissable() {
        String closeLabel = org.openide.util.NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Button.Close");

        OnboardingBubble dismissable = new OnboardingBubble(java.util.List.of(),
                (id, path) -> { }, () -> { });
        assertTrue(containsButton(dismissable, closeLabel),
                "Close button must be offered when a dismiss callback is supplied");

        OnboardingBubble nonDismissable = new OnboardingBubble(java.util.List.of(),
                (id, path) -> { });
        org.junit.jupiter.api.Assertions.assertFalse(containsButton(nonDismissable, closeLabel),
                "First-install view must be non-dismissable (no Close button)");
    }

    private static boolean containsButton(java.awt.Container container, String label) {
        for (java.awt.Component comp : container.getComponents()) {
            if (comp instanceof javax.swing.JButton button && label.equals(button.getText())) {
                return true;
            }
            if (comp instanceof java.awt.Container child && containsButton(child, label)) {
                return true;
            }
        }
        return false;
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
