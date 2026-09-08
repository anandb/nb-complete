package github.anandb.netbeans.mcp;

import org.apache.commons.lang3.exception.ExceptionUtils;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.model.AgentCapabilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;
import github.anandb.netbeans.support.PreferenceKeys;

public class McpManager {
    private static final Logger LOG = Logger.from(McpManager.class);

    private final ExecutorService mcpStartExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "McpStart");
        t.setDaemon(true);
        return t;
    });

    private McpServer mcpServer;
    private final AtomicBoolean mcpDisabled = new AtomicBoolean(false);
    /** When true, session configs hand out a token-protected MCP URL and the
     *  servlet enforces it. Skipped only for PI-family harness binaries that
     *  cannot carry tokens. Set by the manager before the server starts. */
    private volatile boolean mcpAuthRequired;
    private CompletableFuture<Void> serverStartFuture;
    private volatile CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    /** Thread running the server start task — used for explicit interruption
     *  on stop(), since CompletableFuture.cancel(true) only completes the
     *  future exceptionally without interrupting the running task. */
    private volatile Thread startThread;

    /** Listens for the MCP server preference toggle and tears down the
     *  embedded MCP server when the user disables it. */
    private final PreferenceChangeListener prefListener = this::onPreferenceChanged;

    public McpManager() {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        prefs.addPreferenceChangeListener(prefListener);
    }

    private void onPreferenceChanged(PreferenceChangeEvent evt) {
        if (!PreferenceKeys.MCP_SERVER_ENABLED.equals(evt.getKey())) {
            return;
        }
        boolean enabled = evt.getNewValue() == null || Boolean.parseBoolean(evt.getNewValue());
        if (!enabled) {
            LOG.info("MCP server preference disabled — stopping embedded MCP server");
            stop();
        }
    }

    public void start() {
        LOG.info("McpManager.start() called, disabled={0}, preferenceEnabled={1}",
                mcpDisabled.get(), PluginSettings.isMcpServerEnabled());
        if (mcpDisabled.get() || !PluginSettings.isMcpServerEnabled()) {
            LOG.info("MCP disabled, completing ready future immediately");
            readyFuture.complete(null);
            return;
        }
        synchronized (this) {
            if (mcpServer != null || serverStartFuture != null) {
                LOG.info("MCP server already starting or started");
                return;
            }
            readyFuture = new CompletableFuture<>();
            LOG.info("Starting MCP server asynchronously...");
            serverStartFuture = CompletableFuture.runAsync(() -> {
                startThread = Thread.currentThread();
                try {
                    McpServer server = null;
                    try {
                        LOG.info("Creating new McpServer instance...");
                        server = new McpServer();
                        server.setAuthRequired(mcpAuthRequired);
                        LOG.info("Starting MCP server...");
                        server.start();
                        synchronized (McpManager.this) {
                            mcpServer = server;
                            serverStartFuture = null;
                            LOG.info("MCP Server running at {0} (auth required: {1})",
                                    server.getUrl(), mcpAuthRequired);
                            // Complete inside the synchronized block to prevent a
                            // race where a concurrent start() replaces readyFuture
                            // between our null-out and the complete, which would
                            // prematurely complete the NEW future.
                            readyFuture.complete(null);
                        }
                    } catch (IOException e) {
                        LOG.warn("Failed to start MCP server: {0}", ExceptionUtils.getMessage(e));
                        if (server != null) {
                            server.stop();
                        }
                        synchronized (McpManager.this) {
                            serverStartFuture = null;
                            // Complete inside the synchronized block to prevent a
                            // race where a concurrent start() replaces readyFuture
                            // between our null-out and the complete, which would
                            // prematurely complete the NEW future.
                            readyFuture.complete(null);
                        }
                    }
                } finally {
                    startThread = null;
                }
            }, mcpStartExecutor);
            LOG.info("MCP server start task submitted to async executor");
        }
    }

    public CompletableFuture<Void> waitForReady() {
        return readyFuture;
    }

    /** Sets whether MCP clients must present the per-instance token. The live
     *  server (if any) is updated immediately so enforcement and the URL
     *  handed out in session configs always flip together. */
    public void setMcpAuthRequired(boolean required) {
        this.mcpAuthRequired = required;
        McpServer server = this.mcpServer;
        if (server != null) {
            server.setAuthRequired(required);
        }
    }

    public void stop() {
        synchronized (this) {
            if (serverStartFuture != null) {
                // Interrupt the running start thread — CompletableFuture.cancel(true)
                // only completes the future exceptionally without interrupting.
                Thread t = startThread;
                if (t != null) {
                    t.interrupt();
                }
                serverStartFuture.cancel(true);
                serverStartFuture = null;
            }
            if (mcpServer != null) {
                try {
                    mcpServer.stop();
                } catch (Exception e) {
                    LOG.warn("Error stopping MCP server: {0}", ExceptionUtils.getMessage(e));
                } finally {
                    mcpServer = null;
                }
            }
        }
        // Do NOT shut down mcpStartExecutor — it uses daemon threads and
        // must remain alive for restartServer() to submit new tasks.
    }

    public void disable() {
        if (mcpDisabled.compareAndSet(false, true)) {
            LOG.warn("Disabling MCP servers for remainder of this process");
            stop();
            readyFuture.complete(null);
        }
    }

    public boolean isDisabled() {
        return mcpDisabled.get();
    }

    public List<Map<String, Object>> getServerConfig() {
        List<Map<String, Object>> mcpServerList = new ArrayList<>();
        if (mcpDisabled.get() || !PluginSettings.isMcpServerEnabled()) return mcpServerList;

        synchronized (this) {
            if (mcpServer == null || !mcpServer.isRunning()) {
                return mcpServerList;
            }
            // Auth-capable agents (opencode, goose, ...) get the token-protected
            // URL; the PI harness cannot carry tokens, so it gets the plain one.
            String url = mcpAuthRequired ? mcpServer.getAuthenticatedUrl() : mcpServer.getUrl();
            mcpServerList.add(Map.of(
                    "headers", List.of(),
                    "type", "http",
                    "name", "nb",
                    "url", url
            ));
        }
        return mcpServerList;
    }

    public synchronized McpTools getMcpTools() {
        if (mcpServer == null) {
            return null;
        }

        return mcpServer.getMcpTools();
    }

    public void checkServerSupport(AgentCapabilities caps) {
        if (caps == null || !caps.supportsMcpServer()) {
            return;
        }
        
        if (!mcpDisabled.get() && PluginSettings.isMcpServerEnabled() && mcpServer == null && serverStartFuture == null) {
            LOG.info("Starting MCP server");
            start();
        }
    }
}
