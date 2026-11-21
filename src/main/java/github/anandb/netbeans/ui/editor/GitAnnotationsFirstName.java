package github.anandb.netbeans.ui.editor;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.UIBundle;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

import org.openide.awt.NotificationDisplayer;
import org.openide.util.NbBundle;

/**
 * Shows the commit author's first name in git blame annotations.
 *
 * <p>NetBeans derives the author column from the commit email address before
 * the {@code @}, which for GitHub commits is the generated noreply address
 * ({@code 1234+user@users.noreply.github.com}). With the option enabled, the
 * annotation bar's Author field shows the commit author's first name instead
 * (or, when the name is missing, the part of the email after {@code +}).
 * The Author field must be one of the bar's displayed fields: the bar renders a
 * fixed set of display fields, and this reuses the author slot — the git
 * module has no formatter or extra-field SPI. With the Author field hidden the
 * bar renders nothing for this option.</p>
 *
 * <p>Applied from the plugin's "Toggle Annotations" editor action after the git
 * action has been invoked. The blame command runs asynchronously, so a bounded
 * EDT poll waits for the bar to hold its lines and then rewrites their private
 * author abbreviation. Re-fetches started elsewhere (the IDE's own versioning
 * menus, "Show Annotations for revision") keep the NetBeans formatting until
 * Toggle Annotations is used again; a keyboard shortcut for the feature should
 * be bound to that action, not to the IDE's "Show Annotations" action, which has
 * no listener seam.</p>
 */
public final class GitAnnotationsFirstName {

    private static final Logger LOG = Logger.from(GitAnnotationsFirstName.class);

    /** Package-private git class that builds the annotation bar. */
    private static final String ANNOTATION_BAR_CLASS // NOI18N
            = "org.netbeans.modules.git.ui.blame.AnnotationBar"; // NOI18N
    /** Bar field holding the per-line annotation data. */
    private static final String BAR_MAP_FIELD = "elementAnnotations"; // NOI18N
    /** Annotation line field holding the commit author ({@code GitUser}). */
    private static final String LINE_AUTHOR_FIELD = "author"; // NOI18N
    /** Annotation line field holding the abbreviated author shown in the bar. */
    private static final String LINE_SHORT_FIELD = "authorShort"; // NOI18N

    /** Blame usually lands within a few ticks; the cap stops a lost fetch. */
    private static final int APPLY_DELAY_MS = 200;
    private static final int APPLY_MAX_TICKS = 25;
    /** Client property holding the map already decorated on a bar. */
    private static final String CLIENT_PROP_APPLIED_MAP = "beanbot.firstNameAppliedMap"; // NOI18N

    /** Editors with an active poll; also stops duplicate pollers for one editor. */
    private static final Set<JTextComponent> POLLING
            = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Annotation bars, so a shortcut costs a map lookup instead of a tree walk. */
    private static final Map<JTextComponent, Component> BARS
            = Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile Field barMapField;
    private static volatile Field lineAuthorField;
    private static volatile Field lineShortField;
    private static volatile Method textComponentMethod;
    private static volatile Method annotatedMethod;
    private static volatile Method lineNumberMethod;
    private static volatile Method authorNameMethod;
    private static volatile Method authorEmailMethod;
    private static volatile boolean reflectionFailureReported;

    private GitAnnotationsFirstName() {
    }

    /**
     * Applies first names once the shown annotation bar holds its lines.
     * Called after the git "Show Annotations" action; does nothing when the
     * option is off or annotations are hidden.
     *
     * @param editor the editor whose annotations were just shown
     */
    public static void applyWhenShown(JTextComponent editor) {
        if (editor == null || !PluginSettings.isGitAnnotationsFirstName()
                || !POLLING.add(editor)) {
            return;
        }
        Timer timer = new Timer(APPLY_DELAY_MS, null);
        int[] ticks = {0};
        timer.addActionListener(e -> {
            if (!PluginSettings.isGitAnnotationsFirstName() || ++ticks[0] > APPLY_MAX_TICKS) {
                stopPolling(timer, editor);
                return;
            }
            Component bar = findBar(editor);
            if (bar == null) {
                if (ticks[0] == 1) {
                    stopPolling(timer, editor); // no annotation bar: not an annotations show
                }
                return;
            }
            if (!isAnnotated(bar)) {
                stopPolling(timer, editor); // annotations were hidden, not shown
                return;
            }
            if (bar instanceof JComponent component && mapOf(bar) instanceof Map<?, ?>) {
                apply(component);
                stopPolling(timer, editor);
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    private static void stopPolling(Timer timer, JTextComponent editor) {
        timer.stop();
        POLLING.remove(editor);
    }

    /** Replaces the author abbreviation of every line the bar currently holds. */
    static void apply(JComponent bar) {
        try {
            Object value = barMapField(bar.getClass()).get(bar);
            if (!(value instanceof Map<?, ?> map)
                    || map == bar.getClientProperty(CLIENT_PROP_APPLIED_MAP)) {
                return; // nothing new: an unrelated shortcut or a repaint
            }
            synchronized (map) {
                Object previousAuthor = null;
                boolean titleCase = true;
                for (Map.Entry<?, ?> entry : orderedEntries(map)) {
                    Object line = entry.getValue();
                    if (line == null) {
                        continue;
                    }
                    Object author = authorField(line.getClass()).get(line);
                    if (author == null) {
                        continue;
                    }
                    if (previousAuthor != null && !previousAuthor.equals(author)) {
                        titleCase = !titleCase;
                    }
                    previousAuthor = author;
                    String first = firstAuthorName(author);
                    Field shortField = shortField(line.getClass());
                    if (first == null || first.isEmpty()
                            || !(shortField.get(line) instanceof String)) {
                        continue;
                    }
                    // Case alternates per author block as a visual hint.
                    shortField.set(line, stylize(first, titleCase));
                }
            }
            bar.putClientProperty(CLIENT_PROP_APPLIED_MAP, map);
            bar.revalidate();
            bar.repaint();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            reportOnce(ex);
        }
    }

    /** Annotation entries in document order; the bar's map is unordered. */
    private static List<? extends Map.Entry<?, ?>> orderedEntries(Map<?, ?> map) {
        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparingInt(GitAnnotationsFirstName::linePosition));
        return entries;
    }

    private static int linePosition(Map.Entry<?, ?> entry) {
        if (entry.getKey() instanceof Element element) {
            return element.getStartOffset();
        }
        Object line = entry.getValue();
        if (line == null) {
            return 0;
        }
        try {
            Object number = lineNumberMethod(line.getClass()).invoke(line);
            return number instanceof Integer value ? value : 0;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            reportOnce(ex);
            return 0;
        }
    }

    /** @return the annotation bar belonging to the editor, or null when absent. */
    private static Component findBar(JTextComponent editor) {
        Component cached = BARS.get(editor);
        if (cached != null) {
            return cached;
        }
        Component root = editor;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        Component found = root instanceof Container container ? findBarIn(container, editor) : null;
        if (found != null) {
            BARS.put(editor, found);
        }
        return found;
    }

    private static Component findBarIn(Container container, JTextComponent editor) {
        for (Component child : container.getComponents()) {
            if (ANNOTATION_BAR_CLASS.equals(child.getClass().getName()) && editor.equals(textComponentOf(child))) {
                return child;
            }
            if (child instanceof Container nested) {
                Component found = findBarIn(nested, editor);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Object mapOf(Component bar) {
        try {
            return barMapField(bar.getClass()).get(bar);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            reportOnce(ex);
            return null;
        }
    }

    private static boolean isAnnotated(Component bar) {
        try {
            Object annotated = annotatedMethod(bar.getClass()).invoke(bar);
            return Boolean.TRUE.equals(annotated);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            reportOnce(ex);
            return false;
        }
    }

    private static JTextComponent textComponentOf(Component bar) {
        try {
            Object component = textComponentMethod(bar.getClass()).invoke(bar);
            return component instanceof JTextComponent text ? text : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            reportOnce(ex);
            return null;
        }
    }

    private static Field barMapField(Class<?> barClass) throws NoSuchFieldException {
        Field field = barMapField;
        if (field == null || field.getDeclaringClass() != barClass) {
            field = accessibleField(barClass, BAR_MAP_FIELD);
            barMapField = field;
        }
        return field;
    }

    private static Field authorField(Class<?> lineClass) throws NoSuchFieldException {
        Field field = lineAuthorField;
        if (field == null || field.getDeclaringClass() != lineClass) {
            field = accessibleField(lineClass, LINE_AUTHOR_FIELD);
            lineAuthorField = field;
        }
        return field;
    }

    private static Field shortField(Class<?> lineClass) throws NoSuchFieldException {
        Field field = lineShortField;
        if (field == null || field.getDeclaringClass() != lineClass) {
            field = accessibleField(lineClass, LINE_SHORT_FIELD);
            lineShortField = field;
        }
        return field;
    }

    private static Method textComponentMethod(Class<?> barClass) throws NoSuchMethodException {
        Method method = textComponentMethod;
        if (method == null || method.getDeclaringClass() != barClass) {
            method = accessibleMethod(barClass, "getTextComponent"); // NOI18N
            textComponentMethod = method;
        }
        return method;
    }

    private static Method annotatedMethod(Class<?> barClass) throws NoSuchMethodException {
        Method method = annotatedMethod;
        if (method == null || method.getDeclaringClass() != barClass) {
            method = accessibleMethod(barClass, "isAnnotated"); // NOI18N
            annotatedMethod = method;
        }
        return method;
    }

    private static Method lineNumberMethod(Class<?> lineClass) throws NoSuchMethodException {
        Method method = lineNumberMethod;
        if (method == null || method.getDeclaringClass() != lineClass) {
            method = accessibleMethod(lineClass, "getLineNum"); // NOI18N
            lineNumberMethod = method;
        }
        return method;
    }

    private static Field accessibleField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method accessibleMethod(Class<?> type, String name) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    /** Title case for the first block, all lower case for the next, alternating. */
    private static String stylize(String value, boolean titleCase) {
        if (!titleCase) {
            return value.toLowerCase(Locale.ROOT);
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    /** @return the author's first name, or the email user name when the name is unusable. */
    private static String firstAuthorName(Object author) throws ReflectiveOperationException {
        if (author == null) {
            return null;
        }
        String name = (String) authorMethod(author.getClass(), "getName", true).invoke(author);
        String email = (String) authorMethod(author.getClass(), "getEmailAddress", false).invoke(author);
        return preferredAuthor(name, email);
    }

    private static Method authorMethod(Class<?> authorClass, String name, boolean nameGetter)
            throws NoSuchMethodException {
        Method method = nameGetter ? authorNameMethod : authorEmailMethod;
        if (method == null || method.getDeclaringClass() != authorClass) {
            method = authorClass.getMethod(name);
            if (nameGetter) {
                authorNameMethod = method;
            } else {
                authorEmailMethod = method;
            }
        }
        return method;
    }

    /**
     * Derives the displayed first name. A real author name contributes its first
     * token; an email-like name and a missing name fall back to the email user
     * name, taking the part after {@code +} for GitHub noreply addresses.
     *
     * @param name  commit author name (may be null/blank/an email address)
     * @param email commit author email address (may be null)
     * @return the preferred display value, or {@code null} when nothing usable exists
     */
    static String preferredAuthor(String name, String email) {
        if (name != null) {
            String trimmed = name.trim();
            int at = trimmed.indexOf('@');
            if (at >= 0) {
                return emailUserName(trimmed);
            }
            if (!trimmed.isEmpty()) {
                for (int i = 0; i < trimmed.length(); i++) {
                    if (Character.isWhitespace(trimmed.charAt(i))) {
                        return trimmed.substring(0, i);
                    }
                }
                return trimmed;
            }
        }
        return emailUserName(email);
    }

    private static String emailUserName(String email) {
        if (email == null) {
            return null;
        }
        String value = email.trim();
        int at = value.indexOf('@');
        String local = at >= 0 ? value.substring(0, at) : value;
        if (local.isEmpty()) {
            return null;
        }
        int plus = local.indexOf('+');
        if (plus >= 0 && plus + 1 < local.length()) {
            return local.substring(plus + 1);
        }
        return local;
    }

    private static void reportOnce(Exception ex) {
        if (reflectionFailureReported) {
            return;
        }
        reflectionFailureReported = true;
        LOG.warn("Cannot show first names in git annotations: the git module's blame bar "
                + "no longer has the expected fields or methods ({0}). Feature off this session.",
                ex.getMessage(), ex);
        // A log line alone is invisible; tell the user the option silently stopped working.
        SwingUtilities.invokeLater(() -> {
            try {
                NotificationDisplayer.getDefault().notify(
                        NbBundle.getMessage(UIBundle.class, "MSG_GitFirstNameUnavailableTitle"),
                        NotificationDisplayer.Priority.LOW.getIcon(),
                        NbBundle.getMessage(UIBundle.class, "MSG_GitFirstNameUnavailableText"),
                        null, NotificationDisplayer.Priority.LOW);
            } catch (RuntimeException ignored) {
                // Notification is best-effort; the warning above records the cause.
            }
        });
    }
}
