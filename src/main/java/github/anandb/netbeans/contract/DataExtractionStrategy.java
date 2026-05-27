package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.SessionUpdate;

/** Strategy for extracting data from a SessionUpdate SSE notification. */
public interface DataExtractionStrategy {

    /** @return true if this strategy handles the update. */
    boolean canHandle(SessionUpdate update, String reclassifiedType);

    /** Transforms update data into ProcessedMessage blocks and passes to handler. */
    void extract(SessionUpdate update, UIHandler handler);
}
