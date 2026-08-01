package github.anandb.netbeans.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.actions.NodeAction;
import org.openide.nodes.Node;

import github.anandb.netbeans.support.Logger;
import static org.apache.commons.lang3.exception.ExceptionUtils.getMessage;

/**
 * Projects / Files window action: sends the selected file's content to the
 * Mini Assistant input.
 */
@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SendToAssistantFileAction")
@ActionRegistration(displayName = "#CTL_SendToAssistantAction", lazy = false)
@ActionReferences({
    @ActionReference(path = "Projects/Actions", position = 200),
    @ActionReference(path = "UI/ToolActions/Files", position = 200)
})
public final class SendToAssistantFileAction extends NodeAction {

    private static final Logger LOG = Logger.from(SendToAssistantFileAction.class);

    @Override
    protected void performAction(Node[] activatedNodes) {
        if (activatedNodes.length != 1) {
            return;
        }
        FileObject fo = activatedNodes[0].getLookup().lookup(FileObject.class);
        if (fo == null || fo.isFolder()) {
            return;
        }
        final String text = readFile(fo);
        if (text == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> MiniAssistantDialog.getInstance().showWithText(text));
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

    private static String readFile(FileObject fo) {
        try {
            Path path = FileUtil.toFile(fo).toPath();
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Cannot read file {0}: {1}", fo.getPath(), getMessage(ex));
            return null;
        }
    }
}
