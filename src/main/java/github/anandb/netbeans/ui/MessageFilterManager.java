package github.anandb.netbeans.ui;

import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.openide.util.NbPreferences;

/**
 * Manages message type visibility filters via NbPreferences.
 * Extracted from ChatThreadPanel.
 *
 * <p>Preference values are cached in {@code static volatile} fields and kept
 * in sync via a {@link PreferenceChangeListener} so that hot-path reads
 * never touch disk.</p>
 */
// DSL-CONTROLLER: not a view — preferences-backed filter state (show/hide tool
// blocks, keep bubbles). Stays imperative; the toggle button it drives is
// bound by ChatToolbarSpec.
final class MessageFilterManager {
    private static final String PREF_PREFIX = "messageFilter.";
    private static final String[] MESSAGE_TYPES = {"tool", "thought", "assistant", "user"};

    /** Cached preference values — volatile for cross-thread visibility. */
    private static volatile boolean combineToolThought = true;
    private static volatile boolean filterTool = false;
    private static volatile boolean filterThought = false;
    private static volatile boolean filterAssistant = false;
    private static volatile boolean filterUser = false;

    /** Listener that keeps the cached values in sync with NbPreferences. */
    private static final PreferenceChangeListener listener = MessageFilterManager::onPreferenceChanged;

    static {
        Preferences prefs = NbPreferences.forModule(ACPOptionsPanel.class);
        // Seed initial values
        combineToolThought = prefs.getBoolean("combineToolThought", true);
        filterTool        = prefs.getBoolean(PREF_PREFIX + "tool", false);
        filterThought     = prefs.getBoolean(PREF_PREFIX + "thought", false);
        filterAssistant   = prefs.getBoolean(PREF_PREFIX + "assistant", false);
        filterUser        = prefs.getBoolean(PREF_PREFIX + "user", false);
        prefs.addPreferenceChangeListener(listener);
    }

    private MessageFilterManager() {}

    static String[] getMessageTypes() { return MESSAGE_TYPES.clone(); }

    /** Return filter types shown in the UI menu. When combine is on, "activity"
     *  replaces separate "tool" and "thought" entries. */
    static String[] getEffectiveMessageTypes() {
        if (combineToolThought) {
            return new String[]{"activity", "assistant", "user"};
        }
        return MESSAGE_TYPES.clone();
    }

    static boolean isTypeHidden(String type) {
        if (type == null) return false;
        // "activity" is a virtual type — hidden if either tool or thought is hidden
        if ("activity".equals(type)) {
            return filterTool || filterThought;
        }
        return switch (type) {
            case "tool" -> filterTool;
            case "thought" -> filterThought;
            case "assistant" -> filterAssistant;
            case "user" -> filterUser;
            default -> false;
        };
    }

    static void setTypeHidden(String type, boolean hidden) {
        if (type == null) return;
        // "activity" toggles both tool and thought together
        if ("activity".equals(type)) {
            NbPreferences.forModule(ACPOptionsPanel.class).putBoolean(PREF_PREFIX + "tool", hidden);
            NbPreferences.forModule(ACPOptionsPanel.class).putBoolean(PREF_PREFIX + "thought", hidden);
            return;
        }
        NbPreferences.forModule(ACPOptionsPanel.class).putBoolean(PREF_PREFIX + type, hidden);
    }

    /** Refreshes cached values when any preference changes. */
    private static void onPreferenceChanged(PreferenceChangeEvent evt) {
        String key = evt.getKey();
        if (key == null) return;
        switch (key) {
            case "combineToolThought" -> combineToolThought = Boolean.parseBoolean(evt.getNewValue());
            case PREF_PREFIX + "tool" -> filterTool = Boolean.parseBoolean(evt.getNewValue());
            case PREF_PREFIX + "thought" -> filterThought = Boolean.parseBoolean(evt.getNewValue());
            case PREF_PREFIX + "assistant" -> filterAssistant = Boolean.parseBoolean(evt.getNewValue());
            case PREF_PREFIX + "user" -> filterUser = Boolean.parseBoolean(evt.getNewValue());
            default -> {}
        }
    }
}
