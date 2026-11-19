package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessTest {

    @Test
    void emptyOptionalBundleKeysResolveToEmptyStrings() {
        Harness harness = Harness.builder("test", "Test", "test").build();

        assertEquals("", harness.limitations());
        assertEquals("", harness.unsupportedModelSelectionMessage());
        assertEquals("", harness.unsupportedModelSelectionPlaceholder());
        assertFalse(harness.hasLimitedSupport());
    }

    @Test
    void limitedHarnessResolvesItsBundleKey() {
        Harness harness = Harness.builder("test", "Test", "test")
                .limited("Harness.Limitations.Jcode")
                .build();

        assertTrue(harness.hasLimitedSupport());
        assertEquals(NbBundle.getMessage(Harness.class, "Harness.Limitations.Jcode"),
                harness.limitations());
    }

    @Test
    void matchesBinaryAcceptsWindowsExtensionAndRejectsUnknownNames() {
        Harness harness = Harness.builder("omp", "Oh My Pi", "omp")
                .binaryNames("omp", "oh-my-pi")
                .build();

        assertTrue(harness.matchesBinary("omp"));
        assertTrue(harness.matchesBinary("omp.exe"));
        assertTrue(harness.matchesBinary("OH-MY-PI.EXE"));
        assertFalse(harness.matchesBinary("omp-helper"));
        assertFalse(harness.matchesBinary(null));
    }

    @Test
    void withRequiresMessageQueueOnlyChangesThatFlag() {
        Harness base = Harness.builder("opencode", "OpenCode", "opencode")
                .binaryNames("opencode")
                .launchArgs("acp")
                .acceptsMcpAuthToken(false)
                .build();

        Harness queued = base.withRequiresMessageQueue(true);

        assertTrue(queued.requiresMessageQueue());
        assertFalse(base.requiresMessageQueue());
        assertEquals(base.id(), queued.id());
        assertEquals(base.binaryNames(), queued.binaryNames());
        assertEquals(base.launchArgs(), queued.launchArgs());
        assertEquals(base.acceptsMcpAuthToken(), queued.acceptsMcpAuthToken());
    }

    @Test
    void triStateIdsDriveExtensionSupport() {
        assertTrue(Harness.builder("goose", "goose", "goose").build().supportsSessionSteer());
        assertTrue(Harness.builder("goose", "goose", "goose").build().supportsSessionRename());
        assertTrue(Harness.builder("goose", "goose", "goose").build().supportsWorkingDirUpdate());
        assertTrue(Harness.builder("goose", "goose", "goose").build().supportsContextReport());
        assertTrue(Harness.builder("hermes", "Hermes", "hermes").build().supportsSlashSteer());
        assertTrue(Harness.builder("grok", "Grok", "grok").build().supportsInterject());
        assertFalse(Harness.builder("other", "Other", "other").build().supportsSessionSteer());
        assertFalse(Harness.builder("other", "Other", "other").build().supportsSlashSteer());
        assertFalse(Harness.builder("other", "Other", "other").build().supportsInterject());
        assertEquals("", Harness.builder("other", "Other", "other").build().limitationsKey());
    }
}
