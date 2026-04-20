package github.anandb.netbeans.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.nio.file.Path;

import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ACPManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetSingleton() throws Exception {
        Field instanceField = ACPManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        ACPManager manager = ACPManager.getInstance();
        Field activeProjectDirField = ACPManager.class.getDeclaredField("activeProjectDir");
        activeProjectDirField.setAccessible(true);
        activeProjectDirField.set(manager, null);
    }

    @Test
    void testParseCommandReturnsValidCommandLine() {
        ACPManager manager = ACPManager.getInstance();
        CommandLine cmd = manager.parseCommand();
        assertNotNull(cmd);
        assertFalse(cmd.getExecutable().isEmpty());
    }

    @Test
    void testServerStatus() {
        ACPManager manager = ACPManager.getInstance();
        assertFalse(manager.isInitialized());
    }

    @Test
    void testSseListenerRegistration() {
        ACPManager manager = ACPManager.getInstance();
        manager.addSseListener(update -> {});
        // Just verify no exception
    }
}
