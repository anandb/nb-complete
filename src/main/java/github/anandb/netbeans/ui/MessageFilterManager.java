package github.anandb.netbeans.ui;

import org.openide.util.NbPreferences;

public class MessageFilterManager {

    private static final String PREF_PREFIX = "messageFilter.";
    private static final String[] MESSAGE_TYPES = {"tool", "thought", "assistant", "user"};

    private MessageFilterManager() {
    }

    public static String[] getMessageTypes() {
        return MESSAGE_TYPES.clone();
    }

    public static boolean isTypeHidden(String type) {
        if (type == null) {
            return false;
        }
        return NbPreferences.forModule(ACPOptionsPanel.class).getBoolean(PREF_PREFIX + type, false);
    }

    public static void setTypeHidden(String type, boolean hidden) {
        if (type == null) {
            return;
        }
        NbPreferences.forModule(ACPOptionsPanel.class).putBoolean(PREF_PREFIX + type, hidden);
    }
}
