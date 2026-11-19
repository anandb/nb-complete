package github.anandb.netbeans.ui.editor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.contract.DiffRangeControl;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.VcsDiffRanges;
import github.anandb.netbeans.support.VcsUtils;
import github.anandb.netbeans.ui.editor.StashDiffAction.FileDiff;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Opens a multi-file diff for any two revisions, or from a revision to the
 * working tree, for Git and Mercurial. Reuses the stash diff viewer
 * ({@link StashDiffAction}) for the file list and side-by-side panes.
 */
@ServiceProvider(service = DiffRangeControl.class)
public final class DiffRangeViewer implements DiffRangeControl {

    private static final Logger LOG = Logger.from(DiffRangeViewer.class);
    private static final String WORKING_TREE = "Working tree";

    /** Content loading runs off the EDT; a few parallel fetches keep large ranges responsive. */
    private static final RequestProcessor LOAD_RP = new RequestProcessor("DiffRange-load", 4);

    @Override
    public String openDiffRange(File repoDir, String fromRevision, String toRevision, Integer lastCommits) {
        if (repoDir == null) {
            return "Repository directory is required";
        }
        VcsUtils.VcsInfo info = VcsDiffRanges.findRepo(repoDir.getAbsolutePath());
        if (info == null) {
            return "Not a Git or Mercurial repository: " + repoDir;
        }
        String defaultTip = VcsDiffRanges.defaultTip(info.type());
        boolean workingTree = isBlank(toRevision);
        String tip = workingTree ? defaultTip : toRevision.trim();
        String targetName = workingTree ? "working tree" : tip;
        try {
            String error = VcsDiffRanges.validateRevision(info.rootDir(), info.type(), tip);
            if (error != null) {
                return error;
            }
            String from;
            String title;
            if (lastCommits != null) {
                from = VcsDiffRanges.resolveFromRevision(info.rootDir(), info.type(), tip, lastCommits);
                title = "Last " + lastCommits + " commit" + (lastCommits == 1 ? "" : "s");
                if (workingTree) {
                    title += " + working tree";
                } else if (!tip.equals(defaultTip)) {
                    title += " to " + tip;
                }
            } else {
                if (isBlank(fromRevision)) {
                    return "fromRevision is required unless lastCommits is set";
                }
                from = fromRevision.trim();
                error = VcsDiffRanges.validateRevision(info.rootDir(), info.type(), from);
                if (error != null) {
                    return error;
                }
                title = from + ".." + targetName;
            }

            String to = workingTree ? null : tip;
            List<VcsDiffRanges.ChangedFile> changed =
                    VcsDiffRanges.listChanges(info.rootDir(), info.type(), from, to);
            if (changed.isEmpty()) {
                return "No changes between " + from + " and " + targetName;
            }
            title += " (" + changed.size() + " file" + (changed.size() == 1 ? "" : "s") + ")";
            loadAndOpen(info, from, to, changed, title);
            return null;
        } catch (Exception e) {
            String message = e.getMessage();
            return message != null && !message.isBlank() ? message : "Failed to open diff range";
        }
    }

    private static void loadAndOpen(VcsUtils.VcsInfo info, String from, String to,
            List<VcsDiffRanges.ChangedFile> changed, String title) {
        File root = info.rootDir();
        VcsUtils.VcsType type = info.type();
        String leftLabel = VcsDiffRanges.shortRevision(root, type, from);
        String rightLabel = to == null ? WORKING_TREE : VcsDiffRanges.shortRevision(root, type, to);
        LOAD_RP.post(() -> {
            try {
                List<FileDiff> diffs = new ArrayList<>(changed.size());
                for (VcsDiffRanges.ChangedFile file : changed) {
                    VcsDiffRanges.FileContent before =
                            VcsDiffRanges.loadContent(root, type, from, file.path());
                    VcsDiffRanges.FileContent after =
                            VcsDiffRanges.loadContent(root, type, to, file.path());
                    boolean binary = before.binary() || after.binary();
                    diffs.add(FileDiff.forRange(file.path(), String.valueOf(file.status()),
                            binary ? "" : before.text(), binary ? "" : after.text(),
                            leftLabel, rightLabel, binary));
                }
                SwingUtilities.invokeLater(() -> StashDiffAction.openMultiFileDiff(title, diffs));
            } catch (Exception e) {
                LOG.warn("Failed to load diff range {0}..{1}: {2}", from, to,
                        ExceptionUtils.getMessage(e));
            }
        });
    }
}
