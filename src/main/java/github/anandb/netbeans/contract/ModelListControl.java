package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.AvailableModel;
import java.util.List;

/**
 * Port for the per-harness model list cache.
 * <p>
 * The UI seeds the model dropdown from this store so it is populated before a
 * session reports its models, and pushes every received model list back into
 * it. Implementations handle persistence (NbPreferences).
 *
 * <p>Layer: contract — imports only {@code model/}.</p>
 */
public interface ModelListControl {

    /** Returns the last known model list for the harness, or an empty list. */
    List<AvailableModel> getModels(String harnessId);

    /**
     * Stores {@code models} as the harness's model list when it differs from the
     * cached one, updating both the in-memory and the persisted copy. Blank
     * harness ids and null/empty lists are ignored.
     */
    void updateModels(String harnessId, List<AvailableModel> models);
}
