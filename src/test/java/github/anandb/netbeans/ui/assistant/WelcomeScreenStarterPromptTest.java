package github.anandb.netbeans.ui.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;

import github.anandb.netbeans.ui.UIBundle;

/**
 * Starter chips draft a prompt for the composer. File-scoped prompts need the open editor's
 * context; this runs with no editor attached, so every file-scoped chip must fall back to its
 * project-scoped prompt instead of asking about a file that is not open.
 */
class WelcomeScreenStarterPromptTest {

    @Test
    void fileChipsFallBackToTheProjectPromptWithoutAnEditor() throws Exception {
        AtomicReference<String> drafted = new AtomicReference<>();
        JPanel container = new JPanel();
        SwingUtilities.invokeAndWait(() -> WelcomeScreen.show(container, List.of(),
            id -> { }, () -> { }, drafted::set));

        List<JButton> chips = new ArrayList<>();
        collectButtons(container, chips);

        JButton explain = chip(chips, "BTN_StarterExplain");
        JButton findBug = chip(chips, "BTN_StarterFindBug");
        JButton writeTest = chip(chips, "BTN_StarterWriteTest");
        JButton review = chip(chips, "BTN_StarterReview");

        explain.doClick();
        assertEquals(message("LBL_PromptExplainProject"), drafted.get());
        findBug.doClick();
        assertEquals(message("LBL_PromptFindBugProject"), drafted.get());
        writeTest.doClick();
        assertEquals(message("LBL_PromptWriteTestProject"), drafted.get());

        review.doClick();
        assertEquals(message("LBL_PromptReview"), drafted.get());
        assertFalse(drafted.get().startsWith("!"), "the prompt key must resolve");
    }

    private static JButton chip(List<JButton> chips, String labelKey) {
        String label = message(labelKey);
        return chips.stream()
            .filter(b -> label.equals(b.getText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing chip: " + label));
    }

    private static String message(String key) {
        return NbBundle.getMessage(UIBundle.class, key);
    }

    private static void collectButtons(Component c, List<JButton> out) {
        if (c instanceof JButton b) {
            out.add(b);
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectButtons(child, out);
            }
        }
    }
}
