package github.anandb.netbeans.support;

/**
 * Resolves the outbound links whose target can be overridden with a system
 * property, so a fork or a self-hosted deployment can serve its own update
 * manifest and user guide. Both values are read on each call — neither site is
 * on a hot path.
 */
public final class AppLinks {

    /** Maven Central directory for the published artifact. */
    private static final String DEFAULT_NBM_BASE =
            "https://repo1.maven.org/maven2/io/github/anandb/beanbot";

    /** Changelog on the main branch — kept up to date without cutting a release. */
    private static final String DEFAULT_CHANGELOG_URL =
            "https://github.com/anandb/nb-complete/blob/main/CHANGELOG.md";

    private static final String NBM_BASE_PROP = "beanbot.nbm.base";
    private static final String CHANGELOG_URL_PROP = "beanbot.changelog.url";

    private AppLinks() {}

    /** @return the update metadata URL, overridable with {@code -Dbeanbot.update.url}. */
    public static String updateUrl() {
        return System.getProperty(PreferenceKeys.UPDATE_URL_PROP, PreferenceKeys.DEFAULT_UPDATE_URL);
    }

    /**
     * @param version published version, e.g. {@code 1.25.0}
     * @return the published {@code .nbm} for that version. Overridable with
     *         {@code -Dbeanbot.nbm.base} when a fork publishes under another coordinate.
     */
    public static String nbmDownloadUrl(String version) {
        String base = System.getProperty(NBM_BASE_PROP, DEFAULT_NBM_BASE);
        return trimSlash(base) + "/" + version + "/beanbot-" + version + ".nbm";
    }

    /**
     * @return the changelog on the main branch, so the notes link works for a version that
     *         has not been tagged or released yet. Overridable with
     *         {@code -Dbeanbot.changelog.url}.
     */
    public static String changelogUrl() {
        return System.getProperty(CHANGELOG_URL_PROP, DEFAULT_CHANGELOG_URL);
    }

    private static String trimSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** @return the user guide URL, overridable with {@code -Dbeanbot.user.guide.url}. */
    public static String userGuideUrl() {
        return System.getProperty(PreferenceKeys.USER_GUIDE_URL_PROP, PreferenceKeys.DEFAULT_USER_GUIDE_URL);
    }
}
