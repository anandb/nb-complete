package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.contract.ModelListControl;
import github.anandb.netbeans.model.AvailableModel;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.MessageIdGenerator;
import github.anandb.netbeans.support.PreferenceKeys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * Persists the last known model list per harness in NbPreferences (JSON array
 * under {@code modelList.<harnessId>}). The in-memory map is a hot-path view of
 * that store — a list that differs from the cached one is written to both.
 * <p>
 * Registers via {@code @ServiceProvider} so the UI layer can discover it
 * through {@code Lookup.getDefault().lookup(ModelListControl.class)}.
 */
@ServiceProvider(service = ModelListControl.class)
public final class ModelListStore implements ModelListControl {

    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private static final TypeReference<List<AvailableModel>> LIST_TYPE = new TypeReference<>() {};
    private static final String PREF_PREFIX = "modelList.";
    private static final RequestProcessor FLUSH_RP = new RequestProcessor(ModelListStore.class);
    private static final Logger LOG = Logger.from(ModelListStore.class);

    /** Hot-path cache: harnessId → model list (mirrors prefs). */
    private final ConcurrentHashMap<String, List<AvailableModel>> cache = new ConcurrentHashMap<>();

    @Override
    public List<AvailableModel> getModels(String harnessId) {
        if (harnessId == null || harnessId.isBlank()) {
            return Collections.emptyList();
        }
        return List.copyOf(ensureLoaded(harnessId));
    }

    @Override
    public void updateModels(String harnessId, List<AvailableModel> models) {
        if (harnessId == null || harnessId.isBlank() || models == null || models.isEmpty()) {
            return;
        }
        List<AvailableModel> current = ensureLoaded(harnessId);
        if (current.equals(models)) {
            return;
        }
        List<AvailableModel> snapshot = new ArrayList<>(models);
        cache.put(harnessId, snapshot);
        persist(harnessId, snapshot);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /** Loads the harness's list from prefs into the cache; no-op once loaded. */
    private List<AvailableModel> ensureLoaded(String harnessId) {
        return cache.computeIfAbsent(harnessId, ModelListStore::loadFromPrefs);
    }

    /** Writes the harness's list to prefs and flushes the backing store off the caller's thread. */
    private void persist(String harnessId, List<AvailableModel> models) {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String key = prefKey(harnessId);
        try {
            prefs.put(key, MAPPER.writeValueAsString(models));
        } catch (IOException | IllegalArgumentException ex) {
            LOG.warn("Failed to persist model list for harness {0}: {1}",
                    harnessId, ExceptionUtils.getMessage(ex));
            return;
        }
        FLUSH_RP.post(() -> {
            try {
                prefs.flush();
            } catch (BackingStoreException ex) {
                LOG.warn("Failed to flush model list for harness {0}: {1}",
                        harnessId, ExceptionUtils.getMessage(ex));
            }
        });
    }

    private static List<AvailableModel> loadFromPrefs(String harnessId) {
        String json = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(prefKey(harnessId), null);
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<AvailableModel> models = MAPPER.readValue(json, LIST_TYPE);
            models.removeIf(Objects::isNull);
            LOG.fine("Loaded {0} cached models for harness {1}", models.size(), harnessId);
            return models;
        } catch (IOException ex) {
            LOG.warn("Failed to parse model list for harness {0}: {1}",
                    harnessId, ExceptionUtils.getMessage(ex));
            return Collections.emptyList();
        }
    }

    private static String prefKey(String harnessId) {
        String key = PREF_PREFIX + harnessId;
        if (key.length() <= Preferences.MAX_KEY_LENGTH) {
            return key;
        }
        // Java Preferences keys are capped at 80 chars; hash over-long harness ids.
        return PREF_PREFIX + MessageIdGenerator.generate("models", harnessId);
    }
}
