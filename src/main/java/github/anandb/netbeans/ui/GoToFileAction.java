package github.anandb.netbeans.ui;

import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

import org.openide.awt.ActionID;
import org.openide.util.NbBundle;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Opens the "Jump to file" dialog. Registered in layer.xml with Ctrl+N
 * shortcut (remappable via NetBeans Tools &gt; Options &gt; Keymap).
 *
 * <p>Unlike NetBeans' built-in Go To File (Ctrl+O), this dialog:
 * <ul>
 *   <li>Always uses case-insensitive search</li>
 *   <li>Always searches all open projects</li>
 *   <li>Respects .gitignore via {@code FileCacheManager}</li>
 * </ul>
 */
@ActionID(category = "Navigate", id = "github.anandb.netbeans.ui.GoToFileAction")
@NbBundle.Messages("CTL_GoToFileAction=Jump to file")
public class GoToFileAction extends AbstractAction {

    public GoToFileAction() {
        putValue(NAME, Bundle.CTL_GoToFileAction());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!PluginSettings.isQuickJumpEnabled()) return;
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
