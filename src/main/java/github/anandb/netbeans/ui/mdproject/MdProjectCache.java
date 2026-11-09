package github.anandb.netbeans.ui.mdproject;

import java.util.logging.Level;

import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.ProjectManagerImplementation;
import org.openide.util.Lookup;

import github.anandb.netbeans.support.Logger;

/**
 * Clears the project manager's cache of "this directory is not a project" answers.
 *
 * <p>Needed in two places: after a conversion writes the marker, and when <em>Enable Markdown
 * Project Type</em> is toggled — the factory refuses every directory while the type is off, so
 * without the clear a folder that was probed in the disabled state stays invisible after the
 * option is switched back on.</p>
 *
 * <p>The implementation is looked up before the manager is touched: on a bare unit-test classpath
 * there is no {@link ProjectManagerImplementation}, and {@link ProjectManager#getDefault()} throws
 * {@link IllegalStateException} rather than returning null.</p>
 */
final class MdProjectCache {

    private static final Logger LOG = Logger.from(MdProjectCache.class);

    private MdProjectCache() {
    }

    static void clear() {
        if (Lookup.getDefault().lookup(ProjectManagerImplementation.class) == null) {
            return;
        }
        try {
            ProjectManager.getDefault().clearNonProjectCache();
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Could not clear the non-project cache", ex);
        }
    }
}
