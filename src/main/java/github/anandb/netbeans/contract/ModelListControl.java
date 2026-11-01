package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.AvailableModel;
import java.util.List;

/**
 * Port for the per-harness model list cache.
 * <p>
 * The UI seeds the model dropdown from this store so it is populated before a
 * session reports its models, and pushes every received model list back into
 * it. A list is merged, not replaced wholesale: models the harness stops
 * reporting stay available until a retention window has elapsed since they
 * were last seen, so a transient subset (e.g. {@code config_options_update})
 * cannot drop models that are still valid. Implementations own that window.
 *
 * <p>Layer: contract — imports only {@code model/}.</p>
 */
public interface ModelListControl {

    /** Returns the last known model list for the harness, or an empty list. */
    List<AvailableModel> getModels(String harnessId);

    /**
     * Records {@code models} as the harness's latest model list: every included
     * model is stamped as seen now, previously known models that are absent are
     * retained until their own retention window expires, and expired ones are
     * removed. Both the in-memory and the persisted copy are updated. Blank
     * harness ids and null/empty lists are ignored.
     */
    void updateModels(String harnessId, List<AvailableModel> models);

    /**
     * Writes any pending model list to disk immediately. Called on module
     * shutdown, since the coalesced flush may still be waiting out its window.
     */
    default void flush() { }
}
