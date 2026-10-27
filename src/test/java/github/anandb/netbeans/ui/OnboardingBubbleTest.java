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

    @Test
    void showsTheHarnessSignInCommandAndHint() {
        // The chooser must spell out how to authenticate, not just warn about it.
        OnboardingBubble bubble = new OnboardingBubble(
                java.util.List.of(new BinaryResolver.FoundBinary("kilo", "Kilo Code", "/usr/local/bin/kilo")),
                (id, path) -> { });
        java.util.List<String> texts = new java.util.ArrayList<>();
        collectTexts(bubble, texts);
        assertTrue(texts.stream().anyMatch(t -> t.contains("kilo auth login")),
                "sign-in command missing from chooser: " + texts);
        assertTrue(texts.stream().anyMatch(t -> t.contains("/connect")),
                "sign-in hint missing from chooser: " + texts);
    }

    private static void collectTexts(java.awt.Container container, java.util.List<String> out) {
        for (java.awt.Component comp : container.getComponents()) {
            if (comp instanceof javax.swing.JLabel label && label.getText() != null) {
                out.add(label.getText());
            } else if (comp instanceof javax.swing.JTextArea area) {
                out.add(area.getText());
            }
            if (comp instanceof java.awt.Container child) {
                collectTexts(child, out);
            }
        }
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
