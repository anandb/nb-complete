package github.anandb.netbeans.ui;

import javax.swing.SwingUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.actions.NodeAction;
import org.openide.nodes.Node;

/**
 * Projects / Files window action: sends the selected file's path to the
 * Mini Assistant input.
 */
@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SendToAssistantFileAction")
@ActionRegistration(displayName = "#CTL_SendToAssistantAction", lazy = false)
@ActionReferences({
    @ActionReference(path = "Projects/Actions", position = 200),
    @ActionReference(path = "UI/ToolActions/Files", position = 2057)
})
public final class SendToAssistantFileAction extends NodeAction {

    @Override
    protected void performAction(Node[] activatedNodes) {
        if (activatedNodes.length != 1) {
            return;
        }
        FileObject fo = activatedNodes[0].getLookup().lookup(FileObject.class);
        if (fo == null || fo.isFolder()) {
            return;
        }
        SwingUtilities.invokeLater(() -> AssistantTarget.showWithText(fo.getPath()));
    }

    @Override
    protected boolean enable(Node[] activatedNodes) {
        if (activatedNodes.length != 1) {
            return false;
        }
        FileObject fo = activatedNodes[0].getLookup().lookup(FileObject.class);
        return fo != null && !fo.isFolder();
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(SendToAssistantFileAction.class, "CTL_SendToAssistantAction");
    }

    @Override
    public org.openide.util.HelpCtx getHelpCtx() {
        return org.openide.util.HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return true;
    }
}
