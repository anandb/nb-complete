package github.anandb.netbeans.support;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.lang.reflect.Method;
import java.util.logging.Level;
import javax.swing.KeyStroke;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

public final class ShortcutUtils {

    private static final Logger LOG = Logger.from(ShortcutUtils.class);

    private ShortcutUtils() {
    }

    /** Look up the assigned shortcut for an action, including user-assigned shortcuts from the Keymap. */
    @SuppressWarnings("unchecked")
    public static String resolveShortcut(String actionId) {
        // Primary: try KeyStrokeUtils API via reflection
        String result = resolveViaKeyStrokeUtils(actionId);
        if (!result.isEmpty()) return result;

        // Fallback: scan Shortcuts folder in system filesystem for layer.xml shadow files
        return resolveViaFilesystem(actionId);
    }

    @SuppressWarnings("unchecked")
    private static String resolveViaKeyStrokeUtils(String actionId) {
        try {
            ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
            Class<?> cls = cl != null ? cl.loadClass("org.netbeans.core.options.keymap.api.KeyStrokeUtils")
                                      : Class.forName("org.netbeans.core.options.keymap.api.KeyStrokeUtils");
            Method m = cls.getMethod("getKeyStrokesForAction", String.class, KeyStroke.class);

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
            LOG.log(Level.FINE, "KeyStrokeUtils lookup failed for {0}: {1}", new Object[]{actionId, ExceptionUtils.getMessage(t)});
        }
        return "";
    }

    /** Scan the system filesystem Shortcuts folder for shadow files pointing to our action. */
    private static String resolveViaFilesystem(String actionId) {
        try {
            FileObject shortcuts = FileUtil.getConfigFile("Shortcuts");
            if (shortcuts == null) return "";

            String hyphenatedId = actionId.replace('.', '-');

            for (FileObject child : shortcuts.getChildren()) {
                if (!"shadow".equals(child.getExt())) continue;

                Object originalAttr = child.getAttribute("originalFile");
                if (!(originalAttr instanceof FileObject)) continue;

                FileObject original = (FileObject) originalAttr;
                String path = original.getPath();

                if (path.contains(hyphenatedId)) {
                    String parsed = parseShortcutFilename(child.getName());
                    if (!parsed.isEmpty()) {
                        LOG.log(Level.FINE, "Resolved shortcut for {0} via filesystem: {1}",
                                new Object[]{actionId, parsed});
                        return parsed;
                    }
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.FINE, "Filesystem shortcut lookup failed for {0}: {1}",
                    new Object[]{actionId, ExceptionUtils.getMessage(t)});
        }
        return "";
    }

    /**
     * Parse a NetBeans shortcut filename (e.g. "DA-J", "D-L", "DS-L") into a
     * human-readable string like "Ctrl + Alt + J".
     *
     * NetBeans key notation: D=Ctrl, S=Shift, A=Alt, M=Meta.
     * Format: modifiers-key (e.g. "DA-J" = Ctrl+Alt+J, "D-L" = Ctrl+L).
     */
    private static String parseShortcutFilename(String name) {
        int lastDash = name.lastIndexOf('-');
        if (lastDash <= 0 || lastDash >= name.length() - 1) return "";

        String mods = name.substring(0, lastDash);
        String key = name.substring(lastDash + 1);

        boolean mac = Utilities.isMac();
        StringBuilder sb = new StringBuilder();

        for (char c : mods.toCharArray()) {
            switch (c) {
                case 'D':
                    sb.append(mac ? "Cmd + " : "Ctrl + ");
                    break;
                case 'S':
                    sb.append("Shift + ");
                    break;
                case 'A':
                    sb.append(mac ? "Option + " : "Alt + ");
                    break;
                case 'M':
                    sb.append(mac ? "Ctrl + " : "Meta + ");
                    break;
                default:
                    break;
            }
        }

        sb.append(formatNetBeansKeyName(key));
        return sb.toString().trim();
    }

    /** Convert a NetBeans key name (from shortcut filename) to a human-readable label. */
    private static String formatNetBeansKeyName(String key) {
        switch (key) {
            case "DELETE": return "Delete";
            case "INSERT": return "Insert";
            case "ENTER": return "Enter";
            case "PAGE_UP": return "Page Up";
            case "PAGE_DOWN": return "Page Down";
            case "HOME": return "Home";
            case "END": return "End";
            case "ESCAPE": return "Escape";
            case "TAB": return "Tab";
            case "SPACE": return "Space";
            case "BACK_SPACE": return "Backspace";
            case "UP": return "\u2191";
            case "DOWN": return "\u2193";
            case "LEFT": return "\u2190";
            case "RIGHT": return "\u2192";
            default:
                if (key.startsWith("F") && key.length() <= 3) return key;
                if (key.length() == 1) return key;
                return key.substring(0, 1).toUpperCase() + key.substring(1).toLowerCase();
        }
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
