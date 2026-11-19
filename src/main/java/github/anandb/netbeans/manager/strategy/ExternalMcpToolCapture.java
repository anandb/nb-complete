package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.ToolCallRecorder;
import github.anandb.netbeans.model.Harness;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.UsageRecords.ToolCallStatus;
import github.anandb.netbeans.support.ExtraMcpServers;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Captures MCP executions that the plugin does not own, for the harnesses whose ACP stream
 * names the server: goose ({@code _meta.goose.toolCall}), codex-acp
 * ({@code _meta.is_mcp_tool_call} + {@code rawInput.server/tool}), opencode (the tool name is
 * {@code <server>_<tool>}) and omp (xdev {@code details.xdev.inner}). The internal {@code nb}
 * server is skipped because {@code McpTools} already records it.
 */
final class ExternalMcpToolCapture {

    private static final Logger LOG = Logger.from(ExternalMcpToolCapture.class);

    private static final String INTERNAL_SERVER = ExtraMcpServers.INTERNAL_SERVER_NAME;
    private static final String OMP_PATH_PREFIX = "xd://mcp__";
    private static final Set<String> SUPPORTED_HARNESSES = Set.of("omp", "goose", "codex", "opencode");
    private static final Set<String> GOOSE_BUILTINS =
            Set.of("developer", "summon", "extensionmanager");
    /** opencode's own tools; a server-prefixed name never matches one of these. */
    private static final Set<String> OPENCODE_BUILTINS = Set.of(
            "apply_patch", "bash", "edit", "execute", "glob", "grep", "multi_edit", "patch",
            "question", "read", "shell", "skill", "subagent", "task", "todowrite", "wait",
            "webfetch", "websearch", "write");

    /** Start time per session + tool call; a lost terminal event expires instead of leaking. */
    private static final Cache<String, Long> STARTED = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /** Resolved {@code server/tool} name per session + tool call. */
    private static final Cache<String, String> PENDING_NAME = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /** Guards against a repeated terminal update for one tool call. */
    private static final Cache<String, Boolean> RECORDED = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private static volatile ToolCallRecorder recorder;
    /** Test seam: the harness that owns the in-flight update. */
    private static volatile String harnessForTest;
    private static volatile String knownServersSource;
    private static volatile List<String> knownServers = List.of(INTERNAL_SERVER);

    private ExternalMcpToolCapture() {
    }

    /** Feeds one {@code tool_call}/{@code tool_call_update}; non-MCP updates are ignored. */
    static void capture(SessionUpdate update) {
        SessionUpdate.UpdateData data = update != null ? update.update() : null;
        if (data == null) {
            return;
        }
        String sessionId = update.params() != null ? update.params().sessionId() : null;
        String toolCallId = data.toolCallId();
        if (isBlank(sessionId) || isBlank(toolCallId)) {
            return;
        }
        String harness = harness();
        if (!SUPPORTED_HARNESSES.contains(harness)) {
            return;
        }
        String key = sessionId + '\0' + toolCallId;
        Descriptor descriptor = descriptor(harness, data);
        if (descriptor != null && INTERNAL_SERVER.equals(descriptor.server())) {
            forget(key);
            return;
        }

        String status = update.status();
        if (data.type() == MessageType.tool_call && !isTerminal(status)) {
            STARTED.put(key, System.currentTimeMillis());
        }
        if (descriptor != null) {
            PENDING_NAME.asMap().putIfAbsent(key, descriptor.name());
        }
        if (!isTerminal(status)) {
            return;
        }

        String name = PENDING_NAME.getIfPresent(key);
        if (name == null && descriptor != null) {
            name = descriptor.name();
        }
        if (name == null || RECORDED.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long startedAt = STARTED.asMap().remove(key);
        PENDING_NAME.invalidate(key);
        long duration = startedAt != null ? Math.max(0L, now - startedAt) : 0L;
        boolean error = isError(status) || (descriptor != null && descriptor.error());
        record(name, now, duration, error ? ToolCallStatus.ERROR : ToolCallStatus.OK);
    }

    /** The server + tool this update names, or null when the harness does not name one. */
    private static Descriptor descriptor(String harness, SessionUpdate.UpdateData data) {
        if (harness == null) {
            return null;
        }
        return switch (harness) {
            case "omp" -> ompDescriptor(data);
            case "goose" -> gooseDescriptor(data);
            case "codex" -> codexDescriptor(data);
            case "opencode" -> openCodeDescriptor(data);
            default -> null;
        };
    }

    /** omp reports an MCP execution only in the result's xdev envelope. */
    private static Descriptor ompDescriptor(SessionUpdate.UpdateData data) {
        JsonNode inner = inner(data.rawOutput());
        if (inner == null) {
            return null;
        }
        String server = text(inner, "serverName");
        String tool = text(inner, "mcpToolName");
        if (isBlank(server) || isBlank(tool)) {
            return null;
        }
        return new Descriptor(server, tool, inner.path("isError").asBoolean(false));
    }

    /** goose names the extension and its fully prefixed tool ({@code nb__run_command}). */
    private static Descriptor gooseDescriptor(SessionUpdate.UpdateData data) {
        JsonNode call = meta(data).path("goose").path("toolCall");
        String extension = text(call, "extensionName");
        String tool = text(call, "toolName");
        if (isBlank(extension) || isBlank(tool) || GOOSE_BUILTINS.contains(extension)) {
            return null;
        }
        String prefix = extension + "__";
        String bare = tool.startsWith(prefix) ? tool.substring(prefix.length()) : tool;
        return new Descriptor(extension, bare, false);
    }

    /** codex-acp marks MCP calls and carries the server and bare tool name. */
    private static Descriptor codexDescriptor(SessionUpdate.UpdateData data) {
        if (!meta(data).path("is_mcp_tool_call").asBoolean(false)) {
            return null;
        }
        String server = text(data.rawInput(), "server");
        String tool = text(data.rawInput(), "tool");
        if (isBlank(server) || isBlank(tool)) {
            return null;
        }
        return new Descriptor(server, tool, false);
    }

    /** opencode names an MCP tool as {@code <server>_<tool>} (legacy: {@code tools_<...>}). */
    private static Descriptor openCodeDescriptor(SessionUpdate.UpdateData data) {
        String raw = data.name();
        if (isBlank(raw) || OPENCODE_BUILTINS.contains(raw)) {
            return null;
        }
        String name = raw.startsWith("tools_") ? raw.substring("tools_".length()) : raw;
        String server = null;
        for (String known : knownServers()) {
            if (name.startsWith(known + "_") && name.length() > known.length() + 1) {
                server = known;
                break;
            }
        }
        if (server != null) {
            return new Descriptor(server, name.substring(server.length() + 1), false);
        }
        int separator = name.indexOf('_');
        if (separator <= 0 || separator == name.length() - 1) {
            return null;
        }
        return new Descriptor(name.substring(0, separator), name.substring(separator + 1), false);
    }

    /** Known MCP server names, longest first, refreshed when the stored extra servers change. */
    private static List<String> knownServers() {
        String json = PluginSettings.getExtraMcpServers();
        if (json == null ? knownServersSource != null : !json.equals(knownServersSource)) {
            List<String> names = new ArrayList<>();
            names.add(INTERNAL_SERVER);
            for (Map<String, Object> server : ExtraMcpServers.parseStored(json)) {
                Object name = server.get("name");
                if (name instanceof String text && !text.isBlank() && !names.contains(text)) {
                    names.add(text);
                }
            }
            names.sort((a, b) -> Integer.compare(b.length(), a.length()));
            knownServers = List.copyOf(names);
            knownServersSource = json;
        }
        return knownServers;
    }

    /** The {@code details.xdev.inner} object of a completed xdev execution, or null. */
    private static JsonNode inner(JsonNode rawOutput) {
        if (rawOutput == null) {
            return null;
        }
        JsonNode inner = rawOutput.path("details").path("xdev").path("inner");
        return inner.isObject() ? inner : null;
    }

    private static JsonNode meta(SessionUpdate.UpdateData data) {
        return data.meta() != null ? data.meta() : MissingNode.getInstance();
    }

    /** Tool calls still running; everything else is a terminal status. */
    private static boolean isTerminal(String status) {
        return status != null
                && !"pending".equals(status)
                && !"in_progress".equals(status)
                && !"running".equals(status);
    }

    private static boolean isError(String status) {
        return "failed".equals(status) || "error".equals(status) || "cancelled".equals(status);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static void forget(String key) {
        STARTED.invalidate(key);
        PENDING_NAME.invalidate(key);
    }

    private static void record(String toolName, long capturedAt, long durationMs, ToolCallStatus status) {
        ToolCallRecorder target = recorder();
        if (target == null) {
            return;
        }
        try {
            target.record(toolName, capturedAt, durationMs, status);
        } catch (RuntimeException ex) {
            LOG.fine("MCP tool capture failed for {0}: {1}", toolName, ExceptionUtils.getMessage(ex));
        }
    }

    /** The recorder seam, resolved from Lookup once and then cached. */
    private static ToolCallRecorder recorder() {
        ToolCallRecorder current = recorder;
        if (current == null) {
            current = Lookup.getDefault().lookup(ToolCallRecorder.class);
            if (current != null) {
                recorder = current;
            }
        }
        return current;
    }

    private static String harness() {
        String forced = harnessForTest;
        if (forced != null) {
            return forced;
        }
        ProcessControl control = Lookup.getDefault().lookup(ProcessControl.class);
        Harness caps = control != null ? control.getCapabilities() : null;
        return caps != null ? caps.id() : null;
    }

    /** Replaces the Lookup-resolved recorder; tests only. */
    static void setRecorderForTest(ToolCallRecorder target) {
        recorder = target;
    }

    /** Forces the harness id; tests only. */
    static void setHarnessForTest(String harnessId) {
        harnessForTest = harnessId;
    }

    /** Clears the timing and dedupe state; tests only. */
    static void clearCachesForTest() {
        STARTED.invalidateAll();
        PENDING_NAME.invalidateAll();
        RECORDED.invalidateAll();
    }

    /** One external MCP execution's resolved identity. */
    private record Descriptor(String server, String tool, boolean error) {
        String name() {
            return server + '/' + tool;
        }
    }
}
