package github.anandb.netbeans.ui.platform;

/**
 * Access seam for the NetBeans {@code ProcessControl} service. Hides the
 * {@code Lookup.getDefault().lookup(ProcessControl.class)} call so DSL-bound
 * views don't reach into NetBeans APIs directly.
 * <p>
 * Swing-free.
 */
public interface ProcessService {
    github.anandb.netbeans.contract.ProcessControl get();
}
