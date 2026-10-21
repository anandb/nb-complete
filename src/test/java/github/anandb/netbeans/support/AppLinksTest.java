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

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
