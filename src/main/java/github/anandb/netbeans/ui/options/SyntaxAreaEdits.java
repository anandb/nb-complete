package github.anandb.netbeans.ui.options;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Applies programmatic text replacements in the option editors so undo keeps working
 * across them: Ctrl+Z restores the text that was there before the replacement.
 */
final class SyntaxAreaEdits {

    private SyntaxAreaEdits() {
    }

    /**
     * Replaces the whole text as one undo step. Undo/redo stay enabled; a single Ctrl+Z
     * reverts to the previous text. Plain {@code setText} records the removal and the
     * insertion as two separate edits, so Ctrl+Z would undo only the insertion and
     * leave an empty editor.
     */
    static void replaceAll(RSyntaxTextArea area, String text) {
        area.beginAtomicEdit();
        try {
            area.setText(text);
        } finally {
            area.endAtomicEdit();
        }
    }
}
