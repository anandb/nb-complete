package github.anandb.netbeans.manager;

import org.openide.util.NbPreferences;

public class SessionTitleMapper {
    private static final String PREFIX = "session_title_";

    private SessionTitleMapper() {}

    public static void setTitle(String sessionId, String title) {
        NbPreferences.forModule(SessionTitleMapper.class).put(PREFIX + sessionId, title);
    }

    public static String getTitle(String sessionId, String defaultTitle) {
        return NbPreferences.forModule(SessionTitleMapper.class).get(PREFIX + sessionId, defaultTitle);
    }
}
