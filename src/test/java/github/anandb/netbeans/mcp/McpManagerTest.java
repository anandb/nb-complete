package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.HarnessCatalog.Harness;
import github.anandb.netbeans.support.PluginSettings;

/**
 * Tests {@link McpManager}: server-config gating (disabled manager, disabled
 * preference, no running server), the auth-URL flip that must keep the token
 * out of PI-harness configs, and {@code checkServerSupport} start decisions.
 * A real embedded server is started on a random loopback port for the config
 * tests — fully headless.
 */
class McpManagerTest {

    private McpManager manager;
    private MockedStatic<PluginSettings> settings;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
            manager = null;
        }
        if (settings != null) {
            settings.close();
            settings = null;
        }
    }

    /** Mocks the MCP preference knobs: enabled, random port, 30s idle. */
    private void preferencesEnabled() {
        settings = mockStatic(PluginSettings.class);
        settings.when(PluginSettings::isMcpServerEnabled).thenReturn(true);
        settings.when(PluginSettings::getMcpServerPort).thenReturn(0);
        settings.when(PluginSettings::getSessionIdleTimeout).thenReturn(30);
    }

    private static Harness harness(boolean mcpSupported) {
        return new Harness(
                "test-harness", "Test Harness", "test",
                List.of("test-harness"), "",
                false, false, false, false, mcpSupported,
                false, false, false, false, false, false,
                "", "", "", "", "", "", "", List.of());
    }

    // ---- config gating ------------------------------------------------------

    @Test
    void serverConfigEmptyWhenNoServerStarted() {
        manager = new McpManager();

        assertTrue(manager.getServerConfig().isEmpty());
        assertNull(manager.getMcpTools());
    }

    @Test
    void disabledManagerNeverStartsAndReturnsEmptyConfig() throws Exception {
        manager = new McpManager();
        manager.disable();

        assertTrue(manager.isDisabled());
        manager.start(); // must be a no-op
        manager.waitForReady().get(5, TimeUnit.SECONDS); // completes immediately
        assertTrue(manager.getServerConfig().isEmpty());
    }

    @Test
    void startSkippedWhenServerPreferenceDisabled() throws Exception {
        settings = mockStatic(PluginSettings.class);
        settings.when(PluginSettings::isMcpServerEnabled).thenReturn(false);
        manager = new McpManager();

        manager.start();
        manager.waitForReady().get(5, TimeUnit.SECONDS);

        assertTrue(manager.getServerConfig().isEmpty(),
                "no config entry when the preference disables the server");
    }

    @Test
    void checkServerSupportIgnoresNullAndNonMcpHarness() {
        settings = mockStatic(PluginSettings.class);
        settings.when(PluginSettings::isMcpServerEnabled).thenReturn(true);
        manager = new McpManager();

        manager.checkServerSupport(null);
        manager.checkServerSupport(harness(false));

        assertTrue(manager.getServerConfig().isEmpty(),
                "a harness without MCP support must not start the server");
    }

    // ---- live server config -------------------------------------------------

    @Test
    void serverConfigReflectsAuthToggle() throws Exception {
        preferencesEnabled();
        manager = new McpManager();
        manager.setMcpAuthRequired(true);

        manager.start();
        manager.waitForReady().get(15, TimeUnit.SECONDS);

        List<Map<String, Object>> config = manager.getServerConfig();
        assertEquals(1, config.size(), "running server should produce one config entry");
        Map<String, Object> entry = config.get(0);
        assertEquals("http", entry.get("type"));
        assertEquals("nb", entry.get("name"));
        assertEquals(List.of(), entry.get("headers"));
        String authedUrl = (String) entry.get("url");
        assertTrue(authedUrl.contains("?token="),
                "auth-required config must carry the token: " + authedUrl);

        // Flipping auth off must hand out the plain URL — the same switch the
        // servlet enforces, so URL and enforcement never disagree.
        manager.setMcpAuthRequired(false);
        String plainUrl = (String) manager.getServerConfig().get(0).get("url");
        assertFalse(plainUrl.contains("?token="),
                "plain config must not carry the token: " + plainUrl);

        // And back on again.
        manager.setMcpAuthRequired(true);
        assertNotNull(manager.getMcpTools(), "running server should expose its tool registry");
    }

    @Test
    void checkServerSupportStartsServerForMcpHarness() throws Exception {
        preferencesEnabled();
        manager = new McpManager();

        manager.checkServerSupport(harness(true));
        manager.waitForReady().get(15, TimeUnit.SECONDS);

        List<Map<String, Object>> config = manager.getServerConfig();
        assertEquals(1, config.size(), "MCP-capable harness should start the server");
        assertFalse(((String) config.get(0).get("url")).contains("?token="),
                "default (auth off) config uses the plain URL");
    }

    @Test
    void catalogBaseHarnessAdvertisesMcpSupport() {
        // OpenCode is the canonical auth-capable harness; PI-family is not.
        // Guard the catalog flag the manager's callers rely on.
        assertTrue(HarnessCatalog.OPENCODE.supportsMcpServer());
    }
}
