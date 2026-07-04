package github.anandb.netbeans.ui.platform;

/**
 * Access seam for {@code NbBundle.getMessage(...)}. Hides the NetBeans
 * localization API from DSL-bound views so they don't carry
 * {@code NbBundle}/{@code NbBundle.getMessage} calls inline.
 * <p>
 * Swing-free.
 */
public interface Bundle {
    String message(Class<?> clazz, String key);
    String message(Class<?> clazz, String key, Object... params);
}
