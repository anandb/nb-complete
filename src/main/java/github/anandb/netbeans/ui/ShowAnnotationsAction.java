package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.Action;
import org.netbeans.modules.versioning.spi.VCSAnnotator;
import org.netbeans.modules.versioning.spi.VCSContext;
import org.netbeans.modules.versioning.spi.VersioningSupport;
import org.netbeans.modules.versioning.spi.VersioningSystem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Editor context menu action: toggles the versioning system's annotations
 * (blame) for the file in the current editor. Works for any versioning system
 * that implements the annotation SPI (git, Mercurial, Subversion, Local
 * History). Gated by the plugin's "Toggle Annotations" option.
 */
@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.ShowAnnotationsAction")
@ActionRegistration(displayName = "#CTL_ShowAnnotationsAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 9020)
public final class ShowAnnotationsAction extends BaseVersioningEditorAction {

    @Override
    protected String getDisplayNameKey() {
        return "CTL_ShowAnnotationsAction";
    }

    @Override
    protected boolean isMenuEnabled() {
        return PluginSettings.isShowAnnotationsEnabled();
    }

    @Override
    protected void perform(File file, ActionEvent e) {
        VersioningSystem vcs = VersioningSupport.getOwner(file);
        if (vcs == null) {
            return;
        }
        VCSAnnotator annotator = vcs.getVCSAnnotator();
        if (annotator == null) {
            return;
        }
        Node node = nodeForFile(file);
        if (node == null) {
            return;
        }
        VCSContext context = VCSContext.forNodes(new Node[]{node});
        Action[] actions = annotator.getActions(context, VCSAnnotator.ActionDestination.PopupMenu);
        if (actions == null) {
            return;
        }
        for (Action a : actions) {
            if (a == null) {
                continue;
            }
            String name = (String) a.getValue(Action.NAME);
            if (name != null && name.toLowerCase().contains("annot")) {
                a.actionPerformed(e);
                return;
            }
        }
    }

    /** Returns the real node delegate for the file, carrying the cookies the
     *  versioning actions rely on (DataObject, EditorCookie). */
    private static Node nodeForFile(File file) {
        try {
            FileObject fo = FileUtil.toFileObject(file);
            return fo == null ? null : DataObject.find(fo).getNodeDelegate();
        } catch (Exception ex) {
            return null;
        }
    }
}
