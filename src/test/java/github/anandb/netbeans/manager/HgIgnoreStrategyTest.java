package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HgIgnoreStrategyTest {

    private final HgIgnoreStrategy strategy = new HgIgnoreStrategy();

    @Test
    void findHgRootReturnsNullForDirWithoutHg() throws IOException {
        File dir = Files.createTempDirectory("no-hg").toFile();
        try {
            assertNull(HgIgnoreStrategy.findHgRoot(dir));
        } finally {
            dir.delete();
        }
    }

    @Test
    void findHgRootReturnsDirWithHgSubdir() throws IOException {
        File dir = Files.createTempDirectory("has-hg").toFile();
        File hgDir = new File(dir, ".hg");
        hgDir.mkdirs();
        try {
            assertEquals(dir, HgIgnoreStrategy.findHgRoot(dir));
        } finally {
            hgDir.delete();
            dir.delete();
        }
    }

    @Test
    void findHgRootWalksUp(@TempDir Path temp) throws IOException {
        File parent = temp.resolve("parent").toFile();
        parent.mkdirs();
        new File(parent, ".hg").mkdirs();
        File child = temp.resolve("parent/child").toFile();
        child.mkdirs();

        File result = HgIgnoreStrategy.findHgRoot(child);
        assertNotNull(result);
        assertEquals(parent, result);

        new File(parent, ".hg").delete();
        child.delete();
        parent.delete();
    }

    @Test
    void isAvailableReturnsTrueWhenHgDirExists() throws IOException {
        File dir = Files.createTempDirectory("hg-avail").toFile();
        new File(dir, ".hg").mkdirs();
        try {
            assertTrue(strategy.isAvailable(dir));
        } finally {
            new File(dir, ".hg").delete();
            dir.delete();
        }
    }

    @Test
    void isAvailableReturnsFalseWhenNoHgDir() throws IOException {
        File dir = Files.createTempDirectory("no-hg-avail").toFile();
        try {
            assertFalse(strategy.isAvailable(dir));
        } finally {
            dir.delete();
        }
    }

    @Test
    void listNonIgnoredFilesReturnsEmptyForNonHgDir() throws IOException {
        File dir = Files.createTempDirectory("no-hg-list").toFile();
        try {
            assertTrue(strategy.listNonIgnoredFiles(dir).isEmpty());
        } finally {
            dir.delete();
        }
    }

    @Test
    void isIgnoredReturnsFalseForNonHgDir() throws IOException {
        File dir = Files.createTempDirectory("no-hg-check").toFile();
        File file = new File(dir, "test.txt");
        file.createNewFile();
        try {
            assertFalse(strategy.isIgnored(dir, file));
        } finally {
            file.delete();
            dir.delete();
        }
    }
}
