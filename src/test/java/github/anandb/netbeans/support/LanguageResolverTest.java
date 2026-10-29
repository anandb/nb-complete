package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Extension → language and extension → MIME mapping in {@link LanguageResolver}. */
class LanguageResolverTest {

    @Test
    void mapsKnownExtensionsToLanguages() {
        assertEquals("java", LanguageResolver.fromPath("/src/Main.java"));
        assertEquals("python", LanguageResolver.fromPath("script.py"));
        assertEquals("javascript", LanguageResolver.fromPath("app.js"));
        assertEquals("typescript", LanguageResolver.fromPath("app.ts"));
        assertEquals("markdown", LanguageResolver.fromPath("README.md"));
        assertEquals("json", LanguageResolver.fromPath("package.json"));
    }

    @Test
    void foldsShellFamilyOntoBash() {
        assertEquals("bash", LanguageResolver.fromPath("run.sh"));
        assertEquals("bash", LanguageResolver.fromPath("run.ksh"));
        assertEquals("bash", LanguageResolver.fromPath("run.zsh"));
        assertEquals("bash", LanguageResolver.fromPath("run.bash"));
    }

    @Test
    void isCaseInsensitiveAndUsesTheLastDot() {
        assertEquals("java", LanguageResolver.fromPath("MAIN.JAVA"));
        assertEquals("java", LanguageResolver.fromPath("archive.tar.gz.java"));
        // Only the last extension counts, even when it is unmapped.
        assertEquals("gz", LanguageResolver.fromPath("archive.tar.gz"));
    }

    @Test
    void unmappedExtensionFallsBackToItself() {
        assertEquals("xyz", LanguageResolver.fromPath("weird.file.xyz"));
    }

    @Test
    void blankAndExtensionlessPathsResolveToEmpty() {
        assertEquals("", LanguageResolver.fromPath(null));
        assertEquals("", LanguageResolver.fromPath(""));
        assertEquals("", LanguageResolver.fromPath("Makefile"));
        assertEquals("", LanguageResolver.fromPath("trailing."));
        assertEquals("", LanguageResolver.fromPath("/tmp/folder.d/no-extension"));
    }

    @Test
    void mapsExtensionsToMimeTypes() {
        assertEquals("text/x-java", LanguageResolver.fromPathToMime("Main.java"));
        assertEquals("application/json", LanguageResolver.fromPathToMime("package.json"));
        assertEquals("text/x-c++", LanguageResolver.fromPathToMime("main.cpp"));
        assertEquals("text/x-python", LanguageResolver.fromPathToMime("script.py"));
        assertEquals("text/x-kotlin", LanguageResolver.fromPathToMime("App.kt"));
        assertEquals("text/html", LanguageResolver.fromPathToMime("index.htm"));
        assertEquals("text/x-diff", LanguageResolver.fromPathToMime("fix.patch"));
    }

    @Test
    void unknownOrMissingExtensionMimeIsPlainText() {
        assertEquals("text/plain", LanguageResolver.fromPathToMime(null));
        assertEquals("text/plain", LanguageResolver.fromPathToMime(""));
        assertEquals("text/plain", LanguageResolver.fromPathToMime("Makefile"));
        assertEquals("text/plain", LanguageResolver.fromPathToMime("trailing."));
        assertEquals("text/plain", LanguageResolver.fromPathToMime("weird.file.xyz"));
    }
}
