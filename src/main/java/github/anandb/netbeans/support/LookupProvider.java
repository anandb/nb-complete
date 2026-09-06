package github.anandb.netbeans.support;

import org.openide.util.Lookup;

/**
 * Thin wrapper around {@link Lookup#getDefault()} to allow test substitution.
 * Production code calls {@code LookupProvider.getDefault()} instead of
 * {@code Lookup.getDefault()} directly, so tests can inject a mock.
 */
public final class LookupProvider {

    private static volatile Lookup override;

    private LookupProvider() {}

    /** Returns the global Lookup. Overridden in tests via {@link #setOverride}. */
    public static Lookup getDefault() {
        Lookup ovr = override;
        return ovr != null ? ovr : Lookup.getDefault();
    }

    /** Test-only: inject a mock Lookup. Pass {@code null} to restore default. */
    public static void setOverride(Lookup lookup) {
        override = lookup;
    }
}
