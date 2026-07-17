package github.anandb.netbeans.ui;

import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

import org.openide.util.NbBundle;

/**
 * Opens the "Go To File" dialog. Registered in layer.xml with Ctrl+N
 * shortcut (remappable via NetBeans Tools &gt; Options &gt; Keymap).
 *
 * <p>Unlike NetBeans' built-in Go To File (Ctrl+O), this dialog:
 * <ul>
 *   <li>Always uses case-insensitive search</li>
 *   <li>Always searches all open projects</li>
 *   <li>Respects .gitignore via {@code FileCacheManager}</li>
 * </ul>
 */
@NbBundle.Messages("CTL_GoToFileAction=Go To File")
public class GoToFileAction extends AbstractAction {

    public GoToFileAction() {
        putValue(NAME, Bundle.CTL_GoToFileAction());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Window owner = findOwnerWindow();
        GoToFileDialog dialog = new GoToFileDialog(owner);
        dialog.setVisible(true);
    }

    /** Finds the focused window, or falls back to the first visible window. */
    private static Window findOwnerWindow() {
        Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getFocusedWindow();
        if (focused != null) {
            return focused;
        }
        for (Window w : Window.getWindows()) {
            if (w.isVisible()) {
                return w;
            }
        }
        return null;
    }
}
