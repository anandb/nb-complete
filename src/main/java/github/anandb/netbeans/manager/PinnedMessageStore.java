package github.anandb.netbeans.manager;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.MessageIdGenerator;
import github.anandb.netbeans.support.PreferenceKeys;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * Persists pinned message IDs per session in NbPreferences (JSON array under
 * {@code pinnedMessages.<sessionId>}). An in-memory cache is a hot-path view of
 * that store, not the source of truth.
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
    private static final RequestProcessor FLUSH_RP =
            new RequestProcessor(PinnedMessageStore.class);

    /** Logger. */
    private static final Logger LOG = Logger.from(PinnedMessageStore.class);

    /** Hot-path cache: sessionId → pinned message IDs (mirrors prefs). */
    private final ConcurrentHashMap<String, Set<String>> cache = new ConcurrentHashMap<>();

    // ── PinnedMessageControl ──────────────────────────────────────────────

    @Override
    public boolean isPinned(String sessionId, String messageId) {
        if (sessionId == null || messageId == null) {
            return false;
        }
        return ensureLoaded(sessionId).contains(messageId);
    }

    @Override
    public void setPinned(String sessionId, String messageId, boolean pinned) {
        if (sessionId == null || messageId == null) {
            return;
        }
        Set<String> pinnedSet = ensureLoaded(sessionId);
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
        Set<String> pinned = cache.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
        pinned.addAll(stored);
    }

    @Override
    public void retainPinned(String sessionId, Set<String> activeMessageIds) {
        if (sessionId == null || activeMessageIds == null) {
            return;
        }
        Set<String> pinned = ensureLoaded(sessionId);
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
        persistSession(sessionId);
        cache.remove(sessionId);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /** Merge persisted pins into the cache; no-op if this session is already loaded. */
    private Set<String> ensureLoaded(String sessionId) {
        Set<String> existing = cache.get(sessionId);
        if (existing != null) {
            return existing;
        }
        loadSession(sessionId);
        return cache.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
    }

    /** Writes this session's pinned set to prefs and flushes the backing store. */
    private void persistSession(String sessionId) {
        Set<String> pinnedSet = cache.get(sessionId);
        String key = prefKey(sessionId);
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        if (pinnedSet == null || pinnedSet.isEmpty()) {
            prefs.remove(key);
        } else {
            try {
                String json = MAPPER.writeValueAsString(new ArrayList<>(pinnedSet));
                prefs.put(key, json);
            } catch (IOException | IllegalArgumentException ex) {
                LOG.warn("Failed to persist pinned messages for session {0}: {1}",
                        sessionId, ExceptionUtils.getMessage(ex));
                return;
            }
        }
        FLUSH_RP.post(() -> {
            try {
                prefs.flush();
            } catch (BackingStoreException ex) {
                LOG.warn("Failed to flush pinned messages for session {0}: {1}",
                        sessionId, ExceptionUtils.getMessage(ex));
            }
        });
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
            LOG.warn("Failed to parse pinned messages for session {0}: {1}",
                            sessionId, ExceptionUtils.getMessage(ex));
            return Collections.emptyList();
        }
    }

    private static String prefKey(String sessionId) {
        String key = PREF_PREFIX + sessionId;
        if (key.length() <= Preferences.MAX_KEY_LENGTH) {
            return key;
        }
        // Java Preferences keys are capped at 80 chars; hash long session ids.
        return PREF_PREFIX + MessageIdGenerator.generate("pinned", sessionId);
    }
}
