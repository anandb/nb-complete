package github.anandb.netbeans.ui;

import org.openide.modules.OnStart;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Adds a "Copy Message" item to the right-click menu of the commit rows in the
 * IDE's git and Mercurial "Show History" windows, copying the selected
 * commit message (or messages, joined by a blank line) to the system clipboard.
 *
 * <p>The history popup is built inside the versioning module's
 * {@code AbstractSummaryView} with no layer/SPI extension point, and the IDE
 * modules cannot be modified. This follows the {@link ShowChangesLocateHook}
 * runtime-hook pattern: an AWT listener scopes popup-trigger clicks to a
 * history window, then a short poll injects the item into the popup once it is
 * on screen. The invoker is the history list; selection recovery walks the
 * package-private {@code Item.getUserData()} (reflection) to the public
 * {@code LogEntry.getMessage()} every git/Hg log entry implements, so rows that
 * are files rather than commits (whose user data has no message) are skipped.
 * Gated at startup on the plugin's "Enable View File History" option.</p>
 */
@OnStart
public final class ShowHistoryCopyMessageHook implements Runnable {

    private static final Logger LOG = Logger.from(ShowHistoryCopyMessageHook.class);

    /** Popup client property marking a popup as already injected (idempotency). */
    private static final String CLIENT_PROP_INJECTED = "beanbot.copyMessageInjected"; // NOI18N

    /** The toolkit keeps AWT listeners via weak references; hold a strong one. */
    private static ShowHistoryCopyMessageHook instance;

    /** Popup-trigger hook; the field keeps it strongly reachable. */
    private PopupProbe probe;

    @Override
    public void run() {
        if (instance != null) {
            return; // @OnStart can fire again on module reload; register exactly once
        }
        // Gated on the plugin's "Enable View File History" option; takes effect at startup.
        if (!PluginSettings.isViewFileHistoryEnabled()) {
            return; // no AWT listener, no polling while disabled
        }
        instance = this;
        // @OnStart runs off the EDT; the AWT listener and the timer must be created on the EDT
        SwingUtilities.invokeLater(this::register);
    }

    private void register() {
        probe = new PopupProbe("Show History", ShowHistoryCopyMessageHook::insideHistoryWindow,
                ShowHistoryCopyMessageHook::accepts, ShowHistoryCopyMessageHook::injectFromPopup);
        probe.start();
    }

    /** Adds Copy Message to the history popup (runs with the popup on screen). */
    private static void injectFromPopup(JPopupMenu popup) {
        inject(popup, selectedMessages((JList<?>) popup.getInvoker()));
    }

    /**
     * Appends the item to {@code popup}; no-op when the clicked row is not a
     * commit (a file/event row has no message). Package-private for tests.
     */
    static void inject(JPopupMenu popup, List<String> messages) {
        if (messages.isEmpty() || popup.getClientProperty(CLIENT_PROP_INJECTED) != null) {
            return;
        }
        popup.putClientProperty(CLIENT_PROP_INJECTED, Boolean.TRUE);
        popup.addSeparator();
        JMenuItem item = new JMenuItem(NbBundle.getMessage(ShowHistoryCopyMessageHook.class,
                "CTL_CopyMessage")); // NOI18N
        item.addActionListener(e -> copyToClipboard(String.join("\n\n", messages)));
        popup.add(item);
        // the menu may already be on screen; it must be repacked or the added item is cut off
        popup.pack();
        popup.repaint();
    }

    /**
     * Commit messages of the selected history rows, in list order. Rows are
     * {@code AbstractSummaryView.Item} instances whose user data is either a
     * {@code LogEntry} (a commit) or an event (a file within a commit).
     */
    static List<String> selectedMessages(JList<?> list) {
        List<String> messages = new ArrayList<>();
        for (Object value : list.getSelectedValuesList()) {
            String message = messageOf(value);
            if (message != null && !message.isEmpty() && !messages.contains(message)) {
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * @param item {@code AbstractSummaryView.Item} (or any object exposing {@code getUserData()})
     * @return the commit message when the user data is a log entry, else {@code null}
     */
    static String messageOf(Object item) {
        Object data = userData(item);
        if (data == null) {
            return null;
        }
        Method getMessage = methodOnPublicType(data.getClass(), "getMessage"); // NOI18N
        if (getMessage == null) {
            return null; // file/event row: no message to copy
        }
        try {
            Object message = getMessage.invoke(data);
            return message instanceof String text ? text : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            LOG.fine("Could not read a history message: {0}", ex); // NOI18N
            return null;
        }
    }

    /**
     * Finds {@code name()} declared by the nearest PUBLIC class in the hierarchy.
     * The git/Hg log entries are package-private classes extending the public
     * {@code AbstractSummaryView.LogEntry}; reflecting {@code getMessage()} off
     * the concrete class throws {@code IllegalAccessException} even though the
     * method is public, so the call must go through the public ancestor.
     */
    private static Method methodOnPublicType(Class<?> type, String name) {
        for (Class<?> k = type; k != null; k = k.getSuperclass()) {
            if (!Modifier.isPublic(k.getModifiers())) {
                continue;
            }
            try {
                return k.getMethod(name);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * Reflects {@code Item.getUserData()}. The method (and the {@code Item}
     * class) is package-private in the versioning module, so it needs
     * {@code setAccessible}; {@code getMessage()} is reached through the public
     * log-entry ancestor instead (see {@link #methodOnPublicType}).
     */
    static Object userData(Object item) {
        if (item == null) {
            return null;
        }
        for (Class<?> k = item.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Method getter = k.getDeclaredMethod("getUserData"); // NOI18N
                getter.setAccessible(true);
                return getter.invoke(item);
            } catch (NoSuchMethodException ex) {
                // declared on an ancestor class; keep walking
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                LOG.fine("Could not read the history item user data: {0}", ex); // NOI18N
                return null;
            }
        }
        return null;
    }

    private static void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        } catch (RuntimeException ex) {
            // Headless test environments and a clipboard owned by another app both surface here
            LOG.warn("Could not copy the commit message: {0}", ex); // NOI18N
        }
    }

    /** A popup qualifies when its invoker is the commit list of a Show History window. */
    private static boolean accepts(JPopupMenu popup) {
        if (popup.getClientProperty(CLIENT_PROP_INJECTED) != null) {
            return false;
        }
        return popup.getInvoker() instanceof JList<?> list && insideHistoryWindow(list);
    }

    /** @return whether any ancestor of the given component is a Show History window. */
    private static boolean insideHistoryWindow(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c instanceof TopComponent && ShowHistoryRefresh.isHistoryWindowClass(c.getClass())) {
                return true;
            }
        }
        return false;
    }
}
