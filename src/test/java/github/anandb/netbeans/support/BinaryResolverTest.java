package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import github.anandb.netbeans.model.HarnessCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BinaryResolver} binary-name resolution used by the MCP
 * token-auth policy: PI-family harness binaries (pi, pi-acp, pi-agent) skip
 * token auth; everything else (opencode, goose, ...) gets token-protected
 * URLs. Windows executables carry a {@code .exe} suffix that must be stripped
 * before matching.
 */
class BinaryResolverTest {

    @Test
    void piHarnessBinariesMatchWithAndWithoutExe() {
        assertTrue(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/pi")));
        assertTrue(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("C:\\tools\\pi-acp.exe")));
        assertTrue(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/local/bin/pi-agent")));
        assertTrue(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("C:\\bin\\PI-AGENT.EXE")));
    }

    @Test
    void nonPiBinariesDoNotMatch() {
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/opencode")));
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("C:\\tools\\opencode.exe")));
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/goose")));
        // Substring look-alikes must not match — exact-name policy only.
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/piano")));
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/opi")));
    }

    @Test
    void knownHarnessesIncludeCursorAgentAndOthers() {
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("opencode"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("pi-acp"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("goose"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("agent"));
        // Detection is by ACP entry point only — bare pi / pi-agent are not probed.
        assertFalse(BinaryResolver.KNOWN_HARNESSES.contains("pi"));
        assertFalse(BinaryResolver.KNOWN_HARNESSES.contains("pi-agent"));
        assertFalse(BinaryResolver.PI_HARNESS.contains("agent"));
    }

    @Test
    void knownHarnessesDerivedFromCatalog() {
        // Every catalog launch binary must be part of the detection set...
        for (HarnessCatalog.Harness harness : HarnessCatalog.ALL) {
            for (String name : harness.binaryNames()) {
                assertTrue(BinaryResolver.KNOWN_HARNESSES.contains(name),
                        harness.id() + "/" + name + " missing from KNOWN_HARNESSES");
            }
        }
        // ...and the set must not contain duplicates or non-catalog binaries.
        long catalogCount = HarnessCatalog.ALL.stream()
                .flatMap(h -> h.binaryNames().stream()).distinct().count();
        assertEquals(catalogCount, BinaryResolver.KNOWN_HARNESSES.size());
    }

    @Test
    void findAllKnownOnPathReturnsCatalogEntries() {
        // Exact detection results depend on the machine, but every returned
        // entry must reference a known catalog harness and a non-blank path.
        for (BinaryResolver.FoundBinary fb : BinaryResolver.findAllKnownOnPath()) {
            assertNotNull(HarnessCatalog.byId(fb.harnessId()), fb.harnessId());
            assertNotNull(HarnessCatalog.byBinaryName(
                    fb.path().substring(fb.path().replace('\\', '/').lastIndexOf('/') + 1)
                            .toLowerCase(java.util.Locale.ROOT)));
            assertFalse(fb.path().isBlank());
        }
    }

    @Test
    void nativeHarnessNamesMatchOs() {
        String[] names = BinaryResolver.nativeHarnessNames();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean sawAgent = false;
        for (String name : names) {
            if (isWindows) {
                assertTrue(name.endsWith(".exe") || name.endsWith(".cmd"), name);
            } else {
                assertFalse(name.endsWith(".exe"), name);
                assertFalse(name.endsWith(".cmd"), name);
            }
            if (name.startsWith("agent") || "cursor-agent.cmd".equals(name)) {
                sawAgent = true;
            }
        }
        assertTrue(sawAgent);
    }

    @Test
    void missingPathFallsBackToNull() {
        // No configured binary = null (generic pre-selection default), which
        // maps to the generic agent icon/display name, not opencode.
        assertNull(BinaryResolver.binaryNameFromPath(null));
        assertNull(BinaryResolver.binaryNameFromPath(""));
        assertNull(BinaryResolver.binaryNameFromPath("  "));
    }

    @Test
    void windowsNativeNamesIncludeCursorAgentCmd() {
        String[] win = BinaryResolver.nativeHarnessNames(true);
        boolean sawCmd = false;
        boolean sawAgentExe = false;
        for (String name : win) {
            if ("cursor-agent.cmd".equals(name)) {
                sawCmd = true;
            }
            if ("agent.exe".equals(name)) {
                sawAgentExe = true;
            }
        }
        assertTrue(sawCmd);
        assertTrue(sawAgentExe);

        for (String name : BinaryResolver.nativeHarnessNames(false)) {
            assertFalse(name.endsWith(".cmd"), name);
        }
    }

    @Test
    void cmdSuffixIsStrippedFromBinaryName() {
        assertEquals("cursor-agent",
                BinaryResolver.binaryNameFromPath("C:\\Users\\me\\AppData\\Local\\cursor-agent.cmd"));
        assertFalse(BinaryResolver.PI_HARNESS.contains("cursor-agent"));
    }

    @Test
    void tokenizeArgsSplitsSafely() {
        assertEquals(0, BinaryResolver.tokenizeArgs(null).length);
        assertEquals(0, BinaryResolver.tokenizeArgs("  ").length);
        assertEquals(java.util.List.of("acp"), java.util.List.of(BinaryResolver.tokenizeArgs("acp")));
        assertEquals(java.util.List.of("--model", "gpt-4"), java.util.List.of(BinaryResolver.tokenizeArgs("--model gpt-4")));
        assertEquals(2, BinaryResolver.tokenizeArgs("\"a b\" c").length);
    }

    @Test
    void toWslPathMapsDrives() {
        assertEquals("/mnt/c/Users/foo/opencode.exe", BinaryResolver.toWslPath("C:\\Users\\foo\\opencode.exe"));
        assertEquals("/mnt/d/tool.exe", BinaryResolver.toWslPath("D:/tool.exe"));
        assertEquals("/usr/local/bin/goose", BinaryResolver.toWslPath("/usr/local/bin/goose"));
        assertNull(BinaryResolver.toWslPath(null));
    }

    @Test
    void buildWslArgsNeverInterpolates() {
        String[] cmd = BinaryResolver.buildWslArgs("acp", "; rm -rf /");
        assertEquals("wsl.exe", cmd[0]);
        assertEquals("exec \"$0\" \"$@\"", cmd[4]);
        assertEquals("; rm -rf /", cmd[cmd.length - 1]);
    }

    @Test
    void isInPathHandlesBlank() {
        assertFalse(BinaryResolver.isInPath("definitely-not-a-real-binary-xyz123"));
    }

    /**
     * Regression: Windows native names carry a {@code .exe}/{@code .cmd}
     * suffix while catalog names are extension-less. Every name produced here
     * must still resolve to a catalog harness, otherwise harness-specific
     * install directories (windowsInstallSubdirs) are silently skipped on the
     * ACPOptionsPanel detection path.
     */
    @Test
    void windowsNativeNamesResolveToCatalogHarnesses() {
        for (String name : BinaryResolver.nativeHarnessNames(true)) {
            assertNotSame(HarnessCatalog.UNKNOWN, HarnessCatalog.byBinaryName(name),
                    "Windows binary name does not resolve to a catalog harness: " + name);
        }
    }

    /**
     * A Linux distro command lives in a system package directory. Such a path is
     * still classified (and ranked last), but only a candidate the IDE user owns
     * or a global node-package install is accepted — Debian {@code kilo} and a
     * generic {@code agent} stay excluded.
     */
    @Test
    void systemPackageDirCandidatesAreExcluded() {
        assertTrue(BinaryResolver.isSystemPackagePath("/usr/bin/kilo"));
        assertTrue(BinaryResolver.isSystemPackagePath("/bin/agent"));
        assertTrue(BinaryResolver.isSystemPackagePath("/usr/sbin/gemini"));
        assertFalse(BinaryResolver.isSystemPackagePath("/home/me/.kilo/bin/kilo"));
        assertFalse(BinaryResolver.isSystemPackagePath("/usr/local/bin/agent"));
        assertFalse(BinaryResolver.isSystemPackagePath("/home/me/.local/bin/opencode"));
        assertTrue(BinaryResolver.isSystemPackageDir(new java.io.File("/usr/bin")));
        assertFalse(BinaryResolver.isSystemPackageDir(new java.io.File("/usr/local/bin")));
    }

    /**
     * A system-directory candidate survives only when it can be vouched for: a
     * file the IDE's user owns, or the symlink a root {@code npm i -g} leaves
     * behind. A missing path and a symlink into a distro package do not.
     */
    @Test
    void systemCandidatesNeedOwnershipOrNodeModulesLink(@TempDir Path dir) throws IOException {
        Path owned = dir.resolve("goose");
        Files.writeString(owned, "#!/bin/sh\n");
        assertTrue(BinaryResolver.isTrustedSystemCandidate(owned.toString()),
                "a user-owned file is trusted");

        Path pkg = Files.createDirectories(dir.resolve("pkg").resolve("node_modules")
                .resolve("goose").resolve("bin"));
        Path real = pkg.resolve("goose");
        Files.writeString(real, "#!/bin/sh\n");
        Path link = dir.resolve("goose-link");
        Files.createSymbolicLink(link, real);
        assertTrue(BinaryResolver.isTrustedSystemCandidate(link.toString()),
                "a symlink into node_modules is a global package install");

        Path distroTarget = dir.resolve("kilo-editor");
        Files.writeString(distroTarget, "#!/bin/sh\n");
        Path distroLink = dir.resolve("kilo-link");
        Files.createSymbolicLink(distroLink, distroTarget);
        assertTrue(BinaryResolver.isTrustedSystemCandidate(distroLink.toString()),
                "the test owns both ends, so ownership vouches for it");
        assertFalse(BinaryResolver.isTrustedSystemCandidate(dir.resolve("absent").toString()),
                "a path that does not exist cannot be trusted");
    }

    /**
     * {@code which -a} semantics: every qualifying candidate is returned, in
     * trust order (harness-owned dir first), and a generic name installed next
     * to a distro command is still a candidate.
     */
    @Test
    void findAllOnPathReturnsEveryCandidateInTrustOrder(@TempDir Path home) throws IOException {
        Path bin = Files.createDirectories(home.resolve(".kilo").resolve("bin"));
        Path kilo = bin.resolve("kilo");
        Files.writeString(kilo, "#!/bin/sh\n");
        assertTrue(kilo.toFile().setExecutable(true), "could not mark test binary executable");

        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            List<String> candidates = BinaryResolver.findAllOnPath("kilo");
            assertFalse(candidates.isEmpty());
            assertEquals(kilo.toAbsolutePath().toString(), candidates.get(0),
                    "harness-owned install dir must outrank any PATH hit");
            for (String candidate : candidates) {
                assertFalse(BinaryResolver.isSystemPackagePath(candidate)
                                && !BinaryResolver.isTrustedSystemCandidate(candidate),
                        "unvouched system package candidate leaked: " + candidate);
            }
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    /**
     * Cross-name ranking: harness-owned dir first, then same-user files, then
     * distinctive names over generic ones a distro command also owns.
     */
    @Test
    void candidateRankOrdersOwnedThenOwnerThenDistinctive() {
        var owned = new BinaryResolver.Candidate("/home/me/.kilo/bin/kilo", true, true);
        var userFile = new BinaryResolver.Candidate("/home/me/.local/bin/kilo", false, true);
        var rootFile = new BinaryResolver.Candidate("/usr/local/bin/kilo", false, false);
        assertEquals(0, BinaryResolver.candidateRank(owned, false));
        assertEquals(2, BinaryResolver.candidateRank(userFile, false));
        assertEquals(3, BinaryResolver.candidateRank(userFile, true));
        assertEquals(4, BinaryResolver.candidateRank(rootFile, false));
        assertEquals(5, BinaryResolver.candidateRank(rootFile, true));
    }

    /** A file created by this test belongs to the IDE's user. */
    @Test
    void userOwnedFilesAreRecognised(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("agent");
        Files.writeString(file, "#!/bin/sh\n");
        assertTrue(BinaryResolver.isUserOwned(file.toString()));
        assertTrue(BinaryResolver.isUserOwned(dir.toString()));
    }

    /**
     * A harness-owned install dir beats everything on PATH, including a generic
     * name that also exists system-wide.
     */
    @Test
    void findHarnessBinaryPrefersOwnedInstallDir(@TempDir Path home) throws IOException {
        Path owned = Files.createDirectories(home.resolve(".kilo").resolve("bin"));
        Path ownedKilo = owned.resolve("kilo");
        Files.writeString(ownedKilo, "#!/bin/sh\n");
        assertTrue(ownedKilo.toFile().setExecutable(true), "could not mark test binary executable");

        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            assertEquals(ownedKilo.toAbsolutePath().toString(),
                    BinaryResolver.findHarnessBinary(HarnessCatalog.KILO));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }
}
