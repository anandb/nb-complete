package github.anandb.netbeans.ui.editor;

import javax.swing.Action;

import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;

import github.anandb.netbeans.ui.UIBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The Keymap dialog drops actions whose {@code NAME} is empty, which made
 * Toggle Annotations impossible to bind.
 */
class ShowAnnotationsActionTest {

    @Test
    void exposesDisplayNameSoKeymapCanListIt() {
        ShowAnnotationsAction action = new ShowAnnotationsAction();

        assertEquals(NbBundle.getMessage(UIBundle.class, "CTL_ShowAnnotationsAction"),
                action.getValue(Action.NAME));
        assertFalse(((String) action.getValue(Action.NAME)).isBlank());
    }
}
