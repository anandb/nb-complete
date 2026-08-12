package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferencesMigratorTest {

    @TempDir
    Path temp;

    private static void touch(File f) throws IOException {
        Files.createDirectories(f.getParentFile().toPath());
        Files.writeString(f.toPath(), "key=value\n");
    }

    private static void setProp(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void preferencesFilePointsUnderConfigPreferences() {
        File userDir = temp.resolve("31").toFile();
        File prefs = PreferencesMigrator.preferencesFile(userDir);
        File expected = new File(new File(new File(userDir, "config"), "Preferences"), "io/github/anandb/beanbot");
        assertEquals(expected, prefs);
    }

    @Test
    void copiesFileAndCreatesParentDirs() throws IOException {
        File source = temp.resolve("src/io/github/anandb/beanbot.properties").toFile();
        touch(source);
        File target = temp.resolve("dst/io/github/anandb/beanbot.properties").toFile();

        PreferencesMigrator.copyRecursively(source, target);

        assertTrue(target.exists());
        assertEquals("key=value\n", Files.readString(target.toPath()));
    }

    @Test
    void versionRankOrdersStableAboveRcAndMajorAboveMinor() {
        assertTrue(PreferencesMigrator.versionRank("31") > PreferencesMigrator.versionRank("31-rc2"));
        assertTrue(PreferencesMigrator.versionRank("31-rc2") > PreferencesMigrator.versionRank("31-rc1"));
        assertTrue(PreferencesMigrator.versionRank("31-rc1") > PreferencesMigrator.versionRank("30"));
        assertEquals(Long.MIN_VALUE, PreferencesMigrator.versionRank("custom-userdir"));
    }

    @Test
    void findSourcePrefersHighestPreviousVersionOverNewerMtime() throws IOException {
        File root = temp.resolve("root").toFile();
        File current = new File(root, "31");
        File rcPrefs = PreferencesMigrator.preferencesFile(new File(root, "31-rc1"));
        File olderVersionPrefs = PreferencesMigrator.preferencesFile(new File(root, "30"));
        touch(rcPrefs);
        touch(olderVersionPrefs);
        // Make the lower version (30) the more recently modified to prove version beats mtime.
        Files.setLastModifiedTime(olderVersionPrefs.toPath(), java.nio.file.attribute.FileTime.fromMillis(2_000_000));
        Files.setLastModifiedTime(rcPrefs.toPath(), java.nio.file.attribute.FileTime.fromMillis(1_000_000));

        String oldRoot = System.getProperty("netbeans.default_userdir_root");
        setProp("netbeans.default_userdir_root", root.getAbsolutePath());
        try {
            assertEquals(rcPrefs, PreferencesMigrator.findSourcePreferences(current));
        } finally {
            setProp("netbeans.default_userdir_root", oldRoot);
        }
    }

    @Test
    void findSourcePicksHighestRcAmongSameMajor() throws IOException {
        File root = temp.resolve("root").toFile();
        File current = new File(root, "31");
        File rc1 = PreferencesMigrator.preferencesFile(new File(root, "31-rc1"));
        File rc2 = PreferencesMigrator.preferencesFile(new File(root, "31-rc2"));
        touch(rc1);
        touch(rc2);

        String oldRoot = System.getProperty("netbeans.default_userdir_root");
        setProp("netbeans.default_userdir_root", root.getAbsolutePath());
        try {
            assertEquals(rc2, PreferencesMigrator.findSourcePreferences(current));
        } finally {
            setProp("netbeans.default_userdir_root", oldRoot);
        }
    }

    @Test
    void findSourceExcludesUserDirNewerThanCurrent() throws IOException {
        File root = temp.resolve("root").toFile();
        File current = new File(root, "31");
        File rcPrefs = PreferencesMigrator.preferencesFile(new File(root, "31-rc2"));
        File newerPrefs = PreferencesMigrator.preferencesFile(new File(root, "32"));
        touch(rcPrefs);
        touch(newerPrefs);
        Files.setLastModifiedTime(newerPrefs.toPath(), java.nio.file.attribute.FileTime.fromMillis(2_000_000));

        String oldRoot = System.getProperty("netbeans.default_userdir_root");
        setProp("netbeans.default_userdir_root", root.getAbsolutePath());
        try {
            assertEquals(rcPrefs, PreferencesMigrator.findSourcePreferences(current));
        } finally {
            setProp("netbeans.default_userdir_root", oldRoot);
        }
    }

    @Test
    void findSourceReturnsNullWhenNoPreviousData() {
        File root = temp.resolve("root").toFile();
        File current = new File(root, "31");
        String oldRoot = System.getProperty("netbeans.default_userdir_root");
        setProp("netbeans.default_userdir_root", root.getAbsolutePath());
        try {
            assertNull(PreferencesMigrator.findSourcePreferences(current));
        } finally {
            setProp("netbeans.default_userdir_root", oldRoot);
        }
    }

    @Test
    void userDirRootFallsBackToParent() {
        File current = temp.resolve("parent/31").toFile();
        String oldRoot = System.getProperty("netbeans.default_userdir_root");
        setProp("netbeans.default_userdir_root", null);
        try {
            assertEquals(current.getParentFile(), PreferencesMigrator.userDirRoot(current));
        } finally {
            setProp("netbeans.default_userdir_root", oldRoot);
        }
    }

    @Test
    void migrateIfNeededCopiesFromPreviousUserDir() throws IOException {
        Path root = temp.resolve("root");
        Path current = root.resolve("31");
        Path old = root.resolve("30");
        touch(PreferencesMigrator.preferencesFile(old.toFile()));

        String oldUser = System.getProperty("netbeans.user");
        String oldRoot = System.getProperty("netbeans.default_userdir_root");
        setProp("netbeans.user", current.toString());
        setProp("netbeans.default_userdir_root", root.toString());
        try {
            PreferencesMigrator.migrateIfNeeded();
            assertTrue(PreferencesMigrator.preferencesFile(current.toFile()).exists());
        } finally {
            setProp("netbeans.user", oldUser);
            setProp("netbeans.default_userdir_root", oldRoot);
        }
    }

    @Test
    void migrateIfNeededIsNoOpWhenCurrentDataExists() throws IOException {
        Path root = temp.resolve("root");
        Path current = root.resolve("31");
        Path old = root.resolve("30");
        touch(PreferencesMigrator.preferencesFile(old.toFile()));
        File currentPrefs = PreferencesMigrator.preferencesFile(current.toFile());
        touch(currentPrefs); // current user dir already has plugin data

        String oldUser = System.getProperty("netbeans.user");
        String oldRoot = System.getProperty("netbeans.default_userdir_root");
        setProp("netbeans.user", current.toString());
        setProp("netbeans.default_userdir_root", root.toString());
        try {
            PreferencesMigrator.migrateIfNeeded();
            assertEquals("key=value\n", Files.readString(currentPrefs.toPath()));
        } finally {
            setProp("netbeans.user", oldUser);
            setProp("netbeans.default_userdir_root", oldRoot);
        }
    }
}
