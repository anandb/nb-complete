package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.AvailableModel;
import github.anandb.netbeans.support.PreferenceKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelListStoreTest {

    private static final String HARNESS = "test-harness-" + System.nanoTime();

    private ModelListStore store;

    @BeforeEach
    void setUp() {
        store = new ModelListStore();
        removePrefs();
    }

    @AfterEach
    void tearDown() {
        removePrefs();
    }

    private static void removePrefs() {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).remove("modelList." + HARNESS);
    }

    private static List<AvailableModel> models(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new AvailableModel(id, id.toUpperCase(java.util.Locale.ROOT), "desc " + id))
                .toList();
    }

    @Test
    void getModelsEmptyForUnknownHarness() {
        assertTrue(store.getModels(HARNESS).isEmpty());
    }

    @Test
    void updateThenGetReturnsStoredList() {
        List<AvailableModel> list = models("a", "b");
        store.updateModels(HARNESS, list);
        assertEquals(list, store.getModels(HARNESS));
    }

    @Test
    void updateModelsPersistsAndReloads() {
        List<AvailableModel> list = models("a", "b");
        store.updateModels(HARNESS, list);

        ModelListStore reloaded = new ModelListStore();
        assertEquals(list, reloaded.getModels(HARNESS));
    }

    @Test
    void updateModelsIgnoresNullAndBlankHarness() {
        store.updateModels(null, models("a"));
        store.updateModels("", models("a"));
        store.updateModels("   ", models("a"));
        assertTrue(store.getModels(HARNESS).isEmpty());
    }

    @Test
    void updateModelsIgnoresNullAndEmptyList() {
        store.updateModels(HARNESS, null);
        store.updateModels(HARNESS, List.of());
        assertTrue(store.getModels(HARNESS).isEmpty());
    }

    @Test
    void updateModelsReplacesPreviousList() {
        store.updateModels(HARNESS, models("a", "b"));
        store.updateModels(HARNESS, models("c"));
        assertEquals(models("c"), store.getModels(HARNESS));
    }

    @Test
    void malformedPrefsYieldEmptyList() {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .put("modelList." + HARNESS, "NOT_JSON!!!");
        assertTrue(store.getModels(HARNESS).isEmpty());
    }

    @Test
    void longHarnessIdRoundTrips() {
        String longId = "h".repeat(200) + System.nanoTime();
        List<AvailableModel> list = models("a");
        store.updateModels(longId, list);
        assertEquals(list, new ModelListStore().getModels(longId));
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .remove("modelList." + github.anandb.netbeans.support.MessageIdGenerator.generate("models", longId));
    }
}
