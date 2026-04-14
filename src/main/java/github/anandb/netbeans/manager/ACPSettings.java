package github.anandb.netbeans.manager;

import org.openide.util.NbPreferences;

public final class ACPSettings {

    private static final String KEY_PREAMBLE = "preamble";

    private ACPSettings() {
    }

    public static String getPreamble() {
        return NbPreferences.forModule(github.anandb.netbeans.ui.ACPOptionsPanel.class).get(KEY_PREAMBLE, "");
    }

    public static void setPreamble(String preamble) {
        NbPreferences.forModule(github.anandb.netbeans.ui.ACPOptionsPanel.class).put(KEY_PREAMBLE, preamble == null ? "" : preamble);
    }
}
