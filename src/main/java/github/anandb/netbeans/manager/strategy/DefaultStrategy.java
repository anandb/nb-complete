package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

/**
 * Fallback strategy for unknown ACP message types.
 * Logs the unknown type to help with debugging and protocol evolution.
 */
public class DefaultStrategy implements DataExtractionStrategy {
    private static final Logger LOG = new Logger(DefaultStrategy.class);

    @Override
    public boolean canHandle(SessionUpdate update, String reclassifiedType) {
        return true; // Catch-all
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        LOG.warn("Received unknown ACP session update type: {0}", update.type());
        LOG.fine("Unknown update payload: {0}", update);

        // We don't display anything to the UI for unknown types by default
        // to avoid cluttering the chat with garbage, but we log it for developers.
    }
}
