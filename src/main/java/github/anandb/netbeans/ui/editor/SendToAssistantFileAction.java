package github.anandb.netbeans.ui.editor;

import javax.swing.SwingUtilities;
import org.netbeans.api.project.FileOwnerQuery;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.NodeAction;
import org.openide.nodes.Node;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.rendering.ThemeManager;

/**
 * Projects / Files window action: sends the selected file to the Mini Assistant
 * input. Files inside an open project travel as a path reference the agent can
 * read via {@code fs/read_text_file}; project-external files travel as embedded
 * attachments (the client reads the bytes), because the path guard rejects
 * external reads.
 */
@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SendToAssistantFileAction")
@ActionRegistration(displayName = "#CTL_SendToAssistantAction", lazy = false)
@ActionReferences({
    @ActionReference(path = "Projects/Actions", position = 200),
    @ActionReference(path = "UI/ToolActions/Files", position = 2057)
})
public final class SendToAssistantFileAction extends NodeAction {

    public SendToAssistantFileAction() {
        setIcon(ThemeManager.getIcon("send-to-assistant.svg", 16));
    }

    @Override
    protected void performAction(Node[] activatedNodes) {
        if (activatedNodes.length != 1) {
            return;
        }
        FileObject fo = activatedNodes[0].getLookup().lookup(FileObject.class);
        if (fo == null || fo.isFolder()) {
            return;
        }
        boolean external = FileOwnerQuery.getOwner(fo) == null;
        SwingUtilities.invokeLater(() -> {
            if (external) {
                AssistantTarget.showExternalFile(fo);
            } else {
                AssistantTarget.showWithText(fo.getPath());
            }
        });
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
        return NbBundle.getMessage(UIBundle.class, "CTL_SendToAssistantAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return true;
    }
}
