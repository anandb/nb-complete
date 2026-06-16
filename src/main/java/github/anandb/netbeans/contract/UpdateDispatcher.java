package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.SessionUpdate;

/**
 * Dispatches session updates to the UI via a strategy pattern.
 * Implementations process {@link SessionUpdate} events and route them
 * to the appropriate UI handlers.
 */
public interface UpdateDispatcher {

    /** Handles a session update by routing it through the strategy chain. */
    void handle(SessionUpdate update, UIHandler handler);
}
