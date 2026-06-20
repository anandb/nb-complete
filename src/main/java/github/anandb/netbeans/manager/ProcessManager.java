package github.anandb.netbeans.manager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.LanguageResolver;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.ToolExecutor;
import github.anandb.netbeans.mcp.McpToolAdapter;
import github.anandb.netbeans.mcp.McpManager;

@ServiceProvider(service = ProcessControl.class)
public class ProcessManager implements ProcessControl {
    private static final Logger LOG = Logger.from(ProcessManager.class);
    private static volatile ProcessManager INSTANCE;
    private final SlashCommandInterceptor slashCommandInterceptor = new SlashCommandInterceptor();

    private final ObjectMapper objectMapper = MapperSupplier.get();
    private final AtomicReference<AcpProtocolClient> rpcClient = new AtomicReference<>();
    private final List<Consumer<SessionUpdate>> sseListeners = new CopyOnWriteArrayList<>();
    private final PreferenceChangeListener preferenceChangeListener;
    private final ToolExecutor toolExecutor = new McpToolAdapter(new McpManager());

    // Extracted helpers (non-final: cross-referenced in constructor lambdas)
    private ServerProcessLifecycle serverLifecycle;
    private AcpReconnectManager reconnectManager;
    private final AcpRequestRouter requestRouter;

    private volatile Consumer<String> statusListener;
    private volatile List<SessionUpdate.AvailableCommand> availableCommands = List.of();
    private volatile PermissionHandler permissionHandler;
    private volatile Runnable crashHandler;
    private volatile Runnable readyHandler;

    public ProcessManager() {
        toolExecutor.start();
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        preferenceChangeListener = this::onPreferenceChanged;
        prefs.addPreferenceChangeListener(preferenceChangeListener);

        // Wire helpers with callbacks
        requestRouter = new AcpRequestRouter(objectMapper);

        serverLifecycle = new ServerProcessLifecycle(
            rpcClient, toolExecutor, objectMapper,
            () -> {
                Runnable handler = readyHandler;
                if (handler != null) {
                    handler.run();
                }
            },
            this::notifyListeners,
            () -> reconnectManager.handleDisconnection(serverLifecycle::startServer),
            reason -> reconnectManager.setLastDisconnectReason(reason),
            requestRouter::handleReadTextFile,
            requestRouter::handleRequestPermission
        );

        reconnectManager = new AcpReconnectManager(
            serverLifecycle::isClosing,
            serverLifecycle::serverProcess,
            rpcClient,
            () -> { if (crashHandler != null) crashHandler.run(); },
            () -> statusListener,
            serverLifecycle::reconnectRP,
            serverLifecycle::setReconnectTask
        );
    }

    @Override
    public SlashCommandInterceptor getSlashCommandInterceptor() {
        return slashCommandInterceptor;
    }

    private void onPreferenceChanged(PreferenceChangeEvent evt) {
        String key = evt.getKey();
        if (!PreferenceKeys.ACP_EXECUTABLE_PATH.equals(key)
                && !PreferenceKeys.PROCESS_ARGUMENTS.equals(key)) {
            return;
        }
        LOG.fine("Preference changed: {0} — triggering server restart", key);
        if (serverLifecycle.serverStarted() && serverLifecycle.serverProcess() != null && serverLifecycle.serverProcess().isAlive()) {
            restartServer();
        }
    }

    @Override
    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    public static ProcessManager getInstance() {
        ProcessManager pm = INSTANCE;
        if (pm == null) {
            synchronized (ProcessManager.class) {
                pm = INSTANCE;
                if (pm == null) {
                    pm = Lookup.getDefault().lookup(ProcessManager.class);
                    if (pm == null) {
                        throw new IllegalStateException(
                                "ProcessManager not found in Lookup — @ServiceProvider registration is broken");
                    }
                    INSTANCE = pm;
                }
            }
        }
        return pm;
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }
        return client.sendRequest(method, params);
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params, long timeout, TimeUnit unit) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }
        return client.sendRequest(method, params, unit.toSeconds(timeout));
    }

    public void sendNotification(String method, Object params) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            LOG.warn("sendNotification called with null rpcClient");
            return;
        }
        client.sendNotification(method, params);
    }

    @Override
    public synchronized void ensureStarted() {
        serverLifecycle.ensureStarted();
    }

    private synchronized void startServer() {
        serverLifecycle.startServer();
    }

    public void shutdown() {
        if (serverLifecycle.isClosing()) {
            return;
        }
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        prefs.removePreferenceChangeListener(preferenceChangeListener);
        serverLifecycle.stopServer();
    }

    @Override
    public synchronized void restartServer() {
        serverLifecycle.restartServer();
        reconnectManager.resetThrottle();
    }

    private synchronized void stopServer() {
        serverLifecycle.stopServer();
    }

    public void addSseListener(Consumer<SessionUpdate> listener) {
        sseListeners.add(listener);
    }

    public void removeSseListener(Consumer<SessionUpdate> listener) {
        sseListeners.remove(listener);
    }

    private void notifyListeners(SessionUpdate update) {
        // Update available commands if present
        if (update.update() != null && "available_commands_update".equals(update.type())) {
            if (update.update().availableCommands() != null) {
                availableCommands = List.copyOf(update.update().availableCommands());
            }
        }
        for (Consumer<SessionUpdate> listener : sseListeners) {
            listener.accept(update);
        }
    }

    @Override
    public void touchConnection() {
        AcpProtocolClient client = rpcClient.get();
        if (client != null) {
            client.touch();
        }
    }

    @Override
    public CompletableFuture<JsonNode> sendMessage(String sessionId, String text, Map<String, Object> context) {
        return sendMessage(sessionId, text, context, null);
    }

    @Override
    public CompletableFuture<JsonNode> sendMessage(String sessionId, String text, Map<String, Object> context,
                                                   List<Map<String, Object>> additionalBlocks) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }

        List<Map<String, Object>> promptBlocks = new ArrayList<>();

        if (context != null) {
            String filePath = (String) context.get("filePath");
            if (filePath != null) {
                File file = new File(filePath);
                String lang = LanguageResolver.fromPath(filePath);
                String fileName = file.getName();

                StringBuilder xml = new StringBuilder();
                xml.append("<metadata>\n");
                xml.append("  <purpose>reference</purpose>\n");
                xml.append("  <note>The file path, cursor, and selection below are reference-only")
                   .append(" context about the user's editor state. The user's text message")
                   .append(" that follows is the primary instruction.</note>\n");
                xml.append("  <language>").append(lang).append("</language>\n");
                xml.append("  <file_path>").append(filePath).append("</file_path>\n");

                Object cursorObj = context.get("cursor");
                if (cursorObj != null) {
                    xml.append("  <cursor>").append(cursorObj.toString()).append("</cursor>\n");
                }

                Object selObj = context.get("selection");
                if (selObj != null) {
                    xml.append("  <selection>").append(selObj.toString()).append("</selection>\n");
                }
                xml.append("</metadata>");

                Map<String, Object> metadataPart = new HashMap<>();
                metadataPart.put("type", "text");
                metadataPart.put("text", xml.toString());
                metadataPart.put("annotations", Map.of("audience", List.of("assistant")));

                promptBlocks.add(metadataPart);

                String selectionContent = (String) context.get("selectionContent");
                if (selectionContent != null && !selectionContent.isEmpty()) {
                    Map<String, Object> selectionPart = new HashMap<>();
                    selectionPart.put("type", "text");
                    selectionPart.put("text", "\nSelection from `" + fileName + "`:\n```" + lang + "\n" + selectionContent + "\n```\n");
                    selectionPart.put("annotations", Map.of("audience", List.of("assistant")));
                    promptBlocks.add(selectionPart);
                }
            }
        }

        if (additionalBlocks != null) {
            promptBlocks.addAll(additionalBlocks);
        }

        Map<String, Object> userTextPart = new HashMap<>();
        userTextPart.put("type", "text");
        userTextPart.put("text", text);
        promptBlocks.add(userTextPart);

        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", promptBlocks);
        params.put("mcpServers", toolExecutor.getServerConfig());

        return client.sendRequest("session/prompt", params);
    }

    @Override
    public CompletableFuture<Void> stopMessage(String sessionId) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }
        client.sendNotification("session/cancel", Map.of("sessionId", sessionId));
        return CompletableFuture.completedFuture(null);
    }

    public String getActiveProjectDir() {
        return System.getProperty("user.dir");
    }

    @Override
    public List<SessionUpdate.AvailableCommand> getAvailableCommands() {
        return new ArrayList<>(availableCommands);
    }

    @Override
    public CompletableFuture<Void> whenReady() {
        return serverLifecycle.readyFuture();
    }

    @Override
    public void setPermissionHandler(PermissionHandler handler) {
        this.permissionHandler = handler;
        requestRouter.setPermissionHandler(handler);
    }

    @Override
    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener;
    }

    @Override
    public void setCrashHandler(Runnable handler) {
        this.crashHandler = handler;
    }

    public void setReadyHandler(Runnable handler) {
        this.readyHandler = handler;
    }

    private String operationalError() {
        return NbBundle.getMessage(ProcessManager.class, "ERR_NotStarted");
    }
}
