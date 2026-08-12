package github.anandb.netbeans.ui;

import java.io.File;
import javax.swing.Action;
import org.netbeans.modules.versioning.spi.VCSHistoryProvider;
import org.netbeans.modules.versioning.spi.VersioningSupport;
import org.netbeans.modules.versioning.spi.VersioningSystem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Editor context menu action: opens the versioning system's history view for
 * the file in the current editor. Works for any versioning system that
 * implements the history SPI (git, Mercurial, Subversion, Local History).
 * Gated by the plugin's "View File History" option.
 */
@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.ViewFileHistoryAction")
@ActionRegistration(displayName = "#CTL_ViewFileHistoryAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 9030)
public final class ViewFileHistoryAction extends BaseVersioningEditorAction {

    @Override
    protected String getDisplayNameKey() {
        return "CTL_ViewFileHistoryAction";
    }

    @Override
    protected boolean isMenuEnabled() {
        return PluginSettings.isViewFileHistoryEnabled();
    }

    @Override
    protected void perform(File file, java.awt.event.ActionEvent e) {
        VersioningSystem vcs = VersioningSupport.getOwner(file);
        if (vcs == null) {
            return;
        }
        VCSHistoryProvider history = vcs.getVCSHistoryProvider();
        if (history == null) {
            return;
        }
        Action action = history.createShowHistoryAction(new File[]{file});
        if (action != null) {
            action.actionPerformed(e);
        }
    }
}
