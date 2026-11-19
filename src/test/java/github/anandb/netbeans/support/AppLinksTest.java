package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppLinksTest {

    @Test
    void nbmDownloadUrlPointsAtPublishedArtifact() {
        assertEquals("https://repo1.maven.org/maven2/io/github/anandb/beanbot/1.25.0/beanbot-1.25.0.nbm",
                AppLinks.nbmDownloadUrl("1.25.0"));
    }

    @Test
    void changelogUrlPointsAtMainBranch() {
        assertEquals("https://github.com/anandb/nb-complete/blob/main/CHANGELOG.md",
                AppLinks.changelogUrl());
    }

    @Test
    void projectUrlPointsAtTheGitHubRepositoryAndIsOverridable() {
        assertEquals("https://github.com/anandb/nb-complete", AppLinks.projectUrl());
        String old = System.getProperty("beanbot.project.url");
        try {
            System.setProperty("beanbot.project.url", "https://example.test/home");
            assertEquals("https://example.test/home", AppLinks.projectUrl());
        } finally {
            restore("beanbot.project.url", old);
        }
    }

    @Test
    void basesAreOverridableForForksAndTolerateTrailingSlash() {
        String oldNbm = System.getProperty("beanbot.nbm.base");
        String oldChangelog = System.getProperty("beanbot.changelog.url");
        try {
            System.setProperty("beanbot.nbm.base", "https://example.test/m2/beanbot/");
            System.setProperty("beanbot.changelog.url", "https://example.test/CHANGELOG.md");

            assertEquals("https://example.test/m2/beanbot/2.0.0/beanbot-2.0.0.nbm",
                    AppLinks.nbmDownloadUrl("2.0.0"));
            assertEquals("https://example.test/CHANGELOG.md", AppLinks.changelogUrl());
        } finally {
            restore("beanbot.nbm.base", oldNbm);
            restore("beanbot.changelog.url", oldChangelog);
        }
    }

    @Test
    void supportAndIssuesLinksHaveDefaultsAndOverrides() {
        String oldSupport = System.getProperty("beanbot.support.url");
        String oldIssues = System.getProperty("beanbot.issues.url");
        try {
            System.clearProperty("beanbot.support.url");
            System.clearProperty("beanbot.issues.url");
            assertEquals("https://buymeacoffee.com/anandb", AppLinks.supportUrl());
            assertEquals("https://github.com/anandb/nb-complete/issues/", AppLinks.issuesUrl());

            System.setProperty("beanbot.support.url", "https://example.test/support");
            System.setProperty("beanbot.issues.url", "https://example.test/issues");
            assertEquals("https://example.test/support", AppLinks.supportUrl());
            assertEquals("https://example.test/issues", AppLinks.issuesUrl());
        } finally {
            restore("beanbot.support.url", oldSupport);
            restore("beanbot.issues.url", oldIssues);
        }
    }

    @Test
    void updateUrlUsesTheConfiguredDefaultAndProperty() {
        String key = PreferenceKeys.UPDATE_URL_PROP;
        String old = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals(PreferenceKeys.DEFAULT_UPDATE_URL, AppLinks.updateUrl());
            System.setProperty(key, "https://example.test/updates.xml");
            assertEquals("https://example.test/updates.xml", AppLinks.updateUrl());
        } finally {
            restore(key, old);
        }
    }

    @Test
    void userGuideUrlIsNullUntilConfigured() {
        String key = PreferenceKeys.USER_GUIDE_URL_PROP;
        String old = System.getProperty(key);
        try {
            System.clearProperty(key);
            org.junit.jupiter.api.Assertions.assertNull(AppLinks.userGuideUrl());
            System.setProperty(key, "https://example.test/guide");
            assertEquals("https://example.test/guide", AppLinks.userGuideUrl());
        } finally {
            restore(key, old);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
