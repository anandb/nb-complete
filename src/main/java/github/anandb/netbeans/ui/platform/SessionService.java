package github.anandb.netbeans.ui.platform;

import github.anandb.netbeans.contract.SessionControl;

/**
 * Access seam for the NetBeans {@code SessionControl} service. Hides the
 * {@code Lookup.getDefault().lookup(SessionControl.class)} call so DSL-bound
 * views don't reach into NetBeans APIs directly. The default implementation
 * resolves via Lookup; a headless test harness can swap it.
 * <p>
 * Swing-free. The returned {@link SessionControl}
 * is the existing contract/ port — this interface only hides how it is obtained.
 */
public interface SessionService {
    SessionControl get();
}
