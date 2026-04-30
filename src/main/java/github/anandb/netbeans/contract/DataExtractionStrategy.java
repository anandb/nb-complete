package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;

public interface DataExtractionStrategy {
    boolean canHandle(SessionUpdate update);
    void extract(SessionUpdate update, ProcessedMessage target, UIHandler handler);
}
