package github.anandb.netbeans.support;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.logging.Level;
import javax.swing.KeyStroke;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

public final class ShortcutUtils {

    private static final Logger LOG = Logger.from(ShortcutUtils.class);

    private ShortcutUtils() {
    }

    /** Look up the assigned shortcut for an action, including user-assigned shortcuts from the Keymap. */
    @SuppressWarnings("unchecked")
    public static String resolveShortcut(String actionId) {
        try {
            ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
            Class<?> cls = cl != null ? cl.loadClass("org.netbeans.core.options.keymap.api.KeyStrokeUtils")
                                      : Class.forName("org.netbeans.core.options.keymap.api.KeyStrokeUtils");
            java.lang.reflect.Method m = cls.getMethod("getKeyStrokesForAction", String.class, KeyStroke.class);

            // Try with dots
            List<KeyStroke[]> all = (List<KeyStroke[]>) m.invoke(null, actionId, null);

            // Try with hyphens (NetBeans @ActionID generated standard)
            if (all == null || all.isEmpty()) {
                all = (List<KeyStroke[]>) m.invoke(null, actionId.replace('.', '-'), null);
            }

            if (all != null && !all.isEmpty() && all.get(0) != null && all.get(0).length > 0) {
                StringBuilder sb = new StringBuilder();
                for (KeyStroke k : all.get(0)) {
                    if (k != null) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(formatKeyStroke(k));
                    }
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.INFO, "Could not resolve shortcut for " + actionId, t);
        }
        return "";
    }

    public static String formatKeyStroke(KeyStroke ks) {
        if (ks == null) return "";
        StringBuilder sb = new StringBuilder();
        int mod = ks.getModifiers();
        boolean mac = Utilities.isMac();

        if ((mod & InputEvent.CTRL_DOWN_MASK) != 0 || (mod & InputEvent.CTRL_MASK) != 0) {
            sb.append("Ctrl + ");
        }
        if ((mod & InputEvent.META_DOWN_MASK) != 0 || (mod & InputEvent.META_MASK) != 0) {
            sb.append(mac ? "Cmd + " : "Meta + ");
        }
        if ((mod & InputEvent.ALT_DOWN_MASK) != 0 || (mod & InputEvent.ALT_MASK) != 0) {
            sb.append(mac ? "Option + " : "Alt + ");
        }
        if ((mod & InputEvent.SHIFT_DOWN_MASK) != 0 || (mod & InputEvent.SHIFT_MASK) != 0) {
            sb.append("Shift + ");
        }

        int code = ks.getKeyCode();
        if (code != KeyEvent.VK_UNDEFINED) {
            sb.append(KeyEvent.getKeyText(code));
        } else {
            sb.append(ks.getKeyChar());
        }
        return sb.toString();
    }
}
