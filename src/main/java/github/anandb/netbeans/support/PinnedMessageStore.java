package github.anandb.netbeans.support;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.contract.PinnedMessageControl;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

/**
 * In-memory + NbPreferences-persisted store for pinned messages.
 * Each session stores its pinned message IDs as a JSON array under
 * the pref key {@code "pinnedMessages." + sessionId}.
 * <p>
 * Registers via {@code @ServiceProvider} so the UI layer can discover it
 * through {@code Lookup.getDefault().lookup(PinnedMessageControl.class)}.
 */
@ServiceProvider(service = PinnedMessageControl.class)
public final class PinnedMessageStore implements PinnedMessageControl {

    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private static final TypeReference<List<String>> LIST_TYPE =
            new TypeReference<>() {};

    private static final String PREF_PREFIX = "pinnedMessages.";

    /** Logger. */
    private static final Logger LOG = Logger.from(PinnedMessageStore.class);

    /** In-memory cache: sessionId → mutable set of pinned message IDs. */
    private final ConcurrentHashMap<String, Set<String>> cache = new ConcurrentHashMap<>();

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
        persistSession(sessionId);
    }

    @Override
    public void loadSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        List<String> stored = loadFromPrefs(sessionId);
        Set<String> pinned = new HashSet<>(stored);
        cache.put(sessionId, ConcurrentHashMap.newKeySet());
        cache.get(sessionId).addAll(pinned);
    }

    @Override
    public void retainPinned(String sessionId, java.util.Set<String> activeMessageIds) {
        if (sessionId == null || activeMessageIds == null) {
            return;
        }
        Set<String> pinned = cache.get(sessionId);
        if (pinned == null) {
            return;
        }
        int before = pinned.size();
        pinned.removeIf(id -> !activeMessageIds.contains(id));
        int removed = before - pinned.size();
        if (removed > 0) {
            LOG.info("Pinned-message cleanup for session {0}: removed {1} stale entries",
                    sessionId, removed);
            persistSession(sessionId);
        }
    }

    @Override
    public void unloadSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        cache.remove(sessionId);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /** Persists one session's pinned set to its dedicated pref key. */
    private void persistSession(String sessionId) {
        Set<String> pinnedSet = cache.get(sessionId);
        String key = prefKey(sessionId);
        if (pinnedSet == null || pinnedSet.isEmpty()) {
            NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                    .remove(key);
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(new ArrayList<>(pinnedSet));
            NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                    .put(key, json);
        } catch (IOException ex) {
            Logger.from(PinnedMessageStore.class)
                    .warn("Failed to serialize pinned messages for session {0}: {1}",
                            sessionId, ExceptionUtils.getMessage(ex));
        }
    }

    /** Loads one session's pinned message IDs from its pref key. */
    private static List<String> loadFromPrefs(String sessionId) {
        String json = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .get(prefKey(sessionId), null);
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (IOException ex) {
            Logger.from(PinnedMessageStore.class)
                    .warn("Failed to parse pinned messages for session {0}: {1}",
                            sessionId, ExceptionUtils.getMessage(ex));
            return Collections.emptyList();
        }
    }

    private static String prefKey(String sessionId) {
        return PREF_PREFIX + sessionId;
    }
}
