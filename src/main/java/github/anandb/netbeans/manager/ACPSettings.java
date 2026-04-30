package github.anandb.netbeans.manager;

import github.anandb.netbeans.ui.ACPOptionsPanel;
import org.openide.util.NbPreferences;

public final class ACPSettings {

    private static final String KEY_PREAMBLE = "preamble";
    private static final String KEY_CUSTOM_USER_ICON = "customUserIcon";

    private ACPSettings() {
    }

    public static String getPreamble() {
        return NbPreferences.forModule(ACPOptionsPanel.class).get(KEY_PREAMBLE, "");
    }

    public static void setPreamble(String preamble) {
        NbPreferences.forModule(ACPOptionsPanel.class).put(KEY_PREAMBLE, preamble == null ? "" : preamble);
    }

    public static String getCustomUserIcon() {
        return NbPreferences.forModule(ACPOptionsPanel.class).get(KEY_CUSTOM_USER_ICON, "");
    }

    public static void setCustomUserIcon(String path) {
        NbPreferences.forModule(ACPOptionsPanel.class).put(KEY_CUSTOM_USER_ICON, path == null ? "" : path);
    }
}
