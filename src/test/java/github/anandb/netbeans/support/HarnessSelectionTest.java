package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.HarnessCatalog;

/** Pure rules behind the Options-panel harness selector. */
class HarnessSelectionTest {

    @Test
    void containerLaunchersAreClassifiedByName() {
        for (String name : new String[]{"docker", "podman", "nerdctl", "finch",
                "Docker", "docker.exe", "/usr/bin/podman", "C:\\tools\\podman.CMD"}) {
            assertEquals(HarnessSelection.Launcher.CONTAINER, HarnessSelection.launcherKind(name), name);
        }
        assertEquals(HarnessSelection.Launcher.DIRECT, HarnessSelection.launcherKind("copilot"));
        assertEquals(HarnessSelection.Launcher.DIRECT, HarnessSelection.launcherKind("/usr/bin/agent"));
        assertEquals(HarnessSelection.Launcher.DIRECT, HarnessSelection.launcherKind(null));
    }

    @Test
    void storedIdThePathCannotExplainStaysExplicit() {
        HarnessSelection.Selection selection = HarnessSelection.resolve("goose", "docker");

        assertFalse(selection.auto());
        assertEquals("goose", selection.harness().id());
        assertEquals("goose", HarnessSelection.harnessId(selection, "docker"));
        assertEquals(HarnessCatalog.byId("goose").displayName(),
                HarnessSelection.displayName(selection, "docker"));
    }

    @Test
    void catalogPathWithoutStoredIdIsAuto() {
        HarnessSelection.Selection selection = HarnessSelection.resolve("", "copilot");

        assertTrue(selection.auto());
        assertEquals("copilot", selection.harness().id());
        assertEquals("copilot", HarnessSelection.harnessId(selection, "copilot"));
        assertEquals(HarnessCatalog.byId("copilot").displayName(),
                HarnessSelection.displayName(selection, "copilot"));
    }

    @Test
    void storedIdMatchingTheDetectionStaysAuto() {
        HarnessSelection.Selection selection = HarnessSelection.resolve("opencode", "opencode");

        assertTrue(selection.auto(), "an onboarding choice that the path explains is not a manual decision");
        assertEquals("opencode", HarnessSelection.harnessId(selection, "opencode"));
    }

    @Test
    void containerWithoutStoredIdIsAutoAndUnknown() {
        HarnessSelection.Selection selection = HarnessSelection.resolve(null, "docker");

        assertTrue(selection.auto());
        assertEquals(HarnessCatalog.UNKNOWN, selection.harness());
        assertEquals("", HarnessSelection.harnessId(selection, "docker"));
        assertEquals("", HarnessSelection.displayName(selection, "docker"));
    }

    @Test
    void unknownBinaryWithoutStoredIdStoresEmpty() {
        HarnessSelection.Selection selection = HarnessSelection.resolve("", "my-wrapper");

        assertTrue(selection.auto());
        assertEquals(HarnessCatalog.UNKNOWN, selection.harness());
        assertEquals("", HarnessSelection.harnessId(selection, "my-wrapper"));
    }

    @Test
    void nullSelectionFallsBackToAuto() {
        assertEquals("copilot", HarnessSelection.harnessId(null, "copilot"));
        assertEquals("", HarnessSelection.harnessId(null, "docker"));
        assertEquals(HarnessCatalog.byId("copilot").displayName(),
                HarnessSelection.displayName(null, "copilot"));
    }
}
