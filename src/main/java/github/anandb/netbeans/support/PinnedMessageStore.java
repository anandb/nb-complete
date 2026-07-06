package github.anandb.netbeans.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.contract.PinnedMessageControl;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

/**
 * In-memory + NbPreferences-persisted store for pinned messages.
 * Keyed by {@code sessionId → Set<messageId>}.
 * <p>
 * Registers via {@code @ServiceProvider} so the UI layer can discover it
 * through {@code Lookup.getDefault().lookup(PinnedMessageControl.class)}.
 */
@ServiceProvider(service = PinnedMessageControl.class)
public final class PinnedMessageStore implements PinnedMessageControl {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, List<String>>> MAP_TYPE =
            new TypeReference<>() {};

    /** In-memory cache: sessionId → mutable set of pinned message IDs. */
    private final ConcurrentHashMap<String, Set<String>> cache = new ConcurrentHashMap<>();

    /** Lock guarding NbPreferences reads/writes (JSON parse + serialize). */
    private static final Object PREF_LOCK = new Object();

    // ── PinnedMessageControl ──────────────────────────────────────────────

    @Override
    public boolean isPinned(String sessionId, String messageId) {
        if (sessionId == null || messageId == null) {
            return false;
        }
        Set<String> pinned = cache.get(sessionId);
        return pinned != null && pinned.contains(messageId);
    }

    @Override
    public void setPinned(String sessionId, String messageId, boolean pinned) {
        if (sessionId == null || messageId == null) {
            return;
        }
        Set<String> pinnedSet = cache.computeIfAbsent(sessionId,
                k -> ConcurrentHashMap.newKeySet());
        if (pinned) {
            pinnedSet.add(messageId);
        } else {
            pinnedSet.remove(messageId);
        }
        persist();
    }

    @Override
    public void loadSession(String sessionId, Collection<String> messageIds) {
        if (sessionId == null) {
            return;
        }
        // Load the persisted JSON, then intersect with the candidate set so we
        // only keep pins that still have matching messages in the current session.
        Map<String, List<String>> all = loadFromPrefs();
        List<String> stored = all.getOrDefault(sessionId, Collections.emptyList());
        Set<String> valid = new HashSet<>(stored);
        valid.retainAll(messageIds);
        cache.put(sessionId, ConcurrentHashMap.newKeySet());
        cache.get(sessionId).addAll(valid);
    }

    @Override
    public void unloadSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        cache.remove(sessionId);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private void persist() {
        Map<String, List<String>> all = loadFromPrefs();
        // Merge current cache into the persisted map (overwrite each session).
        for (Map.Entry<String, Set<String>> e : cache.entrySet()) {
            if (e.getValue().isEmpty()) {
                all.remove(e.getKey());
            } else {
                all.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        // Remove sessions that were unloaded (not in cache).
        all.keySet().removeIf(k -> !cache.containsKey(k));
        saveToPrefs(all);
    }

    private static Map<String, List<String>> loadFromPrefs() {
        synchronized (PREF_LOCK) {
            String json = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                    .get(PreferenceKeys.PINNED_MESSAGES, null);
            if (json == null || json.isEmpty()) {
                return new HashMap<>();
            }
            try {
                return MAPPER.readValue(json, MAP_TYPE);
            } catch (IOException ex) {
                Logger.from(PinnedMessageStore.class)
                        .warn("Failed to parse pinned messages JSON: {0}", ex.getMessage());
                return new HashMap<>();
            }
        }
    }

    private static void saveToPrefs(Map<String, List<String>> data) {
        synchronized (PREF_LOCK) {
            try {
                String json = MAPPER.writeValueAsString(data);
                NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                        .put(PreferenceKeys.PINNED_MESSAGES, json);
            } catch (IOException ex) {
                Logger.from(PinnedMessageStore.class)
                        .warn("Failed to serialize pinned messages: {0}", ex.getMessage());
            }
        }
    }
}
