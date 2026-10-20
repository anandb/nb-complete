package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbIgnoreStrategyTest {

    private final BbIgnoreStrategy strategy = new BbIgnoreStrategy();

    @Test
    void isAvailableRequiresBbignoreFile(@TempDir Path temp) throws IOException {
        assertFalse(strategy.isAvailable(temp.toFile()));
        Files.writeString(temp.resolve(BbIgnoreStrategy.FILE_NAME), "");
        assertTrue(strategy.isAvailable(temp.toFile()));
    }

    @Test
    void filtersGlobsNegationDirectoryAndAnchoredPatterns(@TempDir Path temp) throws IOException {
        File root = temp.toFile();
        Files.writeString(temp.resolve(BbIgnoreStrategy.FILE_NAME), """
                # keeps the ignore file out of the listing
                .bbignore
                *.log
                !keep.log
                build/
                /secret.txt
                """);
        write(temp, "a.log");
        write(temp, "keep.log");
        write(temp, "secret.txt");
        write(temp, "nested/secret.txt");
        write(temp, "build/out.txt");
        write(temp, "src/Main.java");

        assertEquals(Set.of("keep.log", "nested/secret.txt", "src/Main.java"),
                strategy.listNonIgnoredFiles(root));

        assertTrue(strategy.isIgnored(root, new File(root, "a.log")));
        assertTrue(strategy.isIgnored(root, new File(root, "build/out.txt")),
                "a directory pattern must ignore its descendants");
        assertFalse(strategy.isIgnored(root, new File(root, "nested/secret.txt")),
                "the leading slash anchors the pattern to the root");
        assertFalse(strategy.isIgnored(root, new File(root, "keep.log")),
                "negation re-includes a later match");
    }

    @Test
    void doubleStarAndAnchoredDirectoryGlobs(@TempDir Path temp) throws IOException {
        File root = temp.toFile();
        Files.writeString(temp.resolve(BbIgnoreStrategy.FILE_NAME), """
                **/temp/
                docs/*.md
                """);
        write(temp, "x/temp/a.txt");
        write(temp, "docs/a.md");
        write(temp, "docs/sub/b.md");
        write(temp, "y/docs/a.md");

        assertEquals(Set.of(".bbignore", "docs/sub/b.md", "y/docs/a.md"),
                strategy.listNonIgnoredFiles(root));
    }

    @Test
    void filesOutsideRootAreNeverIgnored(@TempDir Path temp) throws IOException {
        File root = temp.resolve("project").toFile();
        assertTrue(root.mkdirs());
        Files.writeString(temp.resolve("project").resolve(BbIgnoreStrategy.FILE_NAME), "*.txt");
        File outside = temp.resolve("outside.txt").toFile();
        Files.writeString(outside.toPath(), "x");

        assertFalse(strategy.isIgnored(root, outside));
    }

    @Test
    void siblingWithSharedPathPrefixIsOutsideRoot(@TempDir Path temp) throws IOException {
        File root = temp.resolve("project").toFile();
        assertTrue(root.mkdirs());
        Files.writeString(temp.resolve("project").resolve(BbIgnoreStrategy.FILE_NAME), "*.txt");
        File sibling = temp.resolve("project-other/x.txt").toFile();
        assertTrue(sibling.getParentFile().mkdirs());
        Files.writeString(sibling.toPath(), "x");

        assertFalse(strategy.isIgnored(root, sibling));
    }

    @Test
    void invalidPatternIsSkippedAndScanContinues(@TempDir Path temp) throws IOException {
        File root = temp.toFile();
        Files.writeString(temp.resolve(BbIgnoreStrategy.FILE_NAME), "[]\n[z-a]\n*.log\n");
        write(temp, "a.log");
        write(temp, "keep.txt");

        assertEquals(Set.of(BbIgnoreStrategy.FILE_NAME, "keep.txt"),
                strategy.listNonIgnoredFiles(root));
    }

    @Test
    void excludeEverythingYieldsEmptyListing(@TempDir Path temp) throws IOException {
        File root = temp.toFile();
        Files.writeString(temp.resolve(BbIgnoreStrategy.FILE_NAME), "*\n");
        write(temp, "a.txt");
        write(temp, "src/Main.java");

        assertTrue(strategy.listNonIgnoredFiles(root).isEmpty());
    }

    @Test
    void bbignoreWinsOverGitAndHg(@TempDir Path temp) throws IOException {
        File root = temp.toFile();
        Files.createDirectories(temp.resolve(".git/refs"));
        Files.createDirectories(temp.resolve(".hg"));
        Files.writeString(temp.resolve(BbIgnoreStrategy.FILE_NAME), "");

        assertTrue(new FileCacheManager().detectStrategy(root) instanceof BbIgnoreStrategy);
    }

    @Test
    void fallsBackToGitWhenNoBbignore(@TempDir Path temp) throws IOException {
        File root = temp.toFile();
        Files.createDirectories(temp.resolve(".git/refs"));

        assertTrue(new FileCacheManager().detectStrategy(root) instanceof GitIgnoreStrategy);
    }

    private static void write(Path root, String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }
}
