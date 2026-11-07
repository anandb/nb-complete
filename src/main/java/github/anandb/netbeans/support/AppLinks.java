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

    /** Funding link — shown in Help and the empty-state screen, never as a popup. */
    private static final String DEFAULT_SUPPORT_URL = "https://buymeacoffee.com/anandb";

    /** Issue tracker behind Help ▸ Report an Issue. */
    private static final String DEFAULT_ISSUES_URL = "https://github.com/anandb/nb-complete/issues/";

    /** Project home behind Help ▸ Star on GitHub. */
    private static final String DEFAULT_PROJECT_URL = "https://github.com/anandb/nb-complete";

    private static final String NBM_BASE_PROP = "beanbot.nbm.base";
    private static final String CHANGELOG_URL_PROP = "beanbot.changelog.url";
    private static final String SUPPORT_URL_PROP = "beanbot.support.url";
    private static final String ISSUES_URL_PROP = "beanbot.issues.url";
    private static final String PROJECT_URL_PROP = "beanbot.project.url";

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

    /**
     * @return the project's funding link, overridable with {@code -Dbeanbot.support.url} so a fork
     *         can point at its own page.
     */
    public static String supportUrl() {
        return System.getProperty(SUPPORT_URL_PROP, DEFAULT_SUPPORT_URL);
    }

    /** @return the issue tracker, overridable with {@code -Dbeanbot.issues.url}. */
    public static String issuesUrl() {
        return System.getProperty(ISSUES_URL_PROP, DEFAULT_ISSUES_URL);
    }

    /** @return the project home page (GitHub repository), overridable with
     *  {@code -Dbeanbot.project.url} so a fork can point at its own home. */
    public static String projectUrl() {
        return System.getProperty(PROJECT_URL_PROP, DEFAULT_PROJECT_URL);
    }

    /** @return the custom user-guide URL, or {@code null} when none is configured. */
    public static String userGuideUrl() {
        return System.getProperty(PreferenceKeys.USER_GUIDE_URL_PROP);
    }
}
