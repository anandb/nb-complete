package github.anandb.netbeans.support;

/**
 * Resolves the outbound links whose target can be overridden with a system
 * property, so a fork or a self-hosted deployment can serve its own update
 * manifest and user guide. Both values are read on each call — neither site is
 * on a hot path.
 */
public final class AppLinks {

    private AppLinks() {}

    /** @return the update manifest URL, overridable with {@code -Dbeanbot.update.url}. */
    public static String updateUrl() {
        return System.getProperty(PreferenceKeys.UPDATE_URL_PROP, PreferenceKeys.DEFAULT_UPDATE_URL);
    }

    /** @return the user guide URL, overridable with {@code -Dbeanbot.user.guide.url}. */
    public static String userGuideUrl() {
        return System.getProperty(PreferenceKeys.USER_GUIDE_URL_PROP, PreferenceKeys.DEFAULT_USER_GUIDE_URL);
    }
}
