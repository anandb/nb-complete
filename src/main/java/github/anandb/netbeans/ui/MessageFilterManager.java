package github.anandb.netbeans.ui;

import org.openide.util.NbPreferences;

/**
 * Manages message type visibility filters via NbPreferences.
 * Extracted from ChatThreadPanel.
 */
final class MessageFilterManager {
    private static final String PREF_PREFIX = "messageFilter.";
    private static final String[] MESSAGE_TYPES = {"tool", "thought", "assistant", "user"};

    private MessageFilterManager() {}

    static String[] getMessageTypes() { return MESSAGE_TYPES.clone(); }

    /** Return filter types shown in the UI menu. When combine is on, "activity"
     *  replaces separate "tool" and "thought" entries. */
    static String[] getEffectiveMessageTypes() {
        if (NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("combineToolThought", true)) {
            return new String[]{"activity", "assistant", "user"};
        }
        return MESSAGE_TYPES.clone();
    }

    static boolean isTypeHidden(String type) {
        if (type == null) return false;
        // "activity" is a virtual type — hidden if either tool or thought is hidden
        if ("activity".equals(type)) {
            return NbPreferences.forModule(ACPOptionsPanel.class)
                    .getBoolean(PREF_PREFIX + "tool", false)
                || NbPreferences.forModule(ACPOptionsPanel.class)
                    .getBoolean(PREF_PREFIX + "thought", false);
        }
        return NbPreferences.forModule(ACPOptionsPanel.class).getBoolean(PREF_PREFIX + type, false);
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
}
