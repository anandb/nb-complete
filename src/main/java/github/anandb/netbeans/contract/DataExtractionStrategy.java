package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.SessionUpdate;

public interface DataExtractionStrategy {
    boolean canHandle(SessionUpdate update, String reclassifiedType);
    void extract(SessionUpdate update, UIHandler handler);
}
