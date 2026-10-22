package github.anandb.netbeans.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts MCP {@code tools/call} invocations per tool name for the current turn.
 * One shared counter is enough because only one session is ever mid-turn at a
 * time (one harness process, one {@code session/prompt} in flight); a steering
 * agent's interleaved second prompt still belongs to the same turn boundary.
 *
 * <p>The end-of-turn dump drains (atomically swaps) the map, so a second
 * turn-end signal for the same turn — SSE {@code responding_finished} plus the
 * RPC result both funnel into the same handler — drains an empty map and
 * writes nothing.</p>
 */
public final class ToolCallStats {
    private static volatile ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();

    private ToolCallStats() {}

    /** Counts one {@code tools/call} invocation of the named tool. */
    public static void record(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        counts.computeIfAbsent(toolName, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Atomically takes and resets the per-turn counts: a snapshot in
     * descending call order, empty when nothing was counted (or the turn was
     * already drained).
     *
     * @return tool name to this turn's call count, never null
     */
    public static Map<String, Long> drain() {
        Map<String, Long> taken = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> e : counts.entrySet()) {
            taken.put(e.getKey(), e.getValue().get());
        }
        counts = new ConcurrentHashMap<>();
        return taken;
    }
}
