package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.SessionUpdate;

/**
 * Strategy for extracting data from a {@link SessionUpdate} SSE notification
 * and dispatching processed content to the UI via {@link UIHandler}.
 *
 * <p>Implementations handle specific update types (thought, tool, code, etc.)
 * defined by {@code reclassifiedType}. When a {@code session/update} notification
 * arrives, each registered strategy is asked {@link #canHandle} to determine if
 * it should process the update. If yes, {@link #extract} transforms the raw
 * update data into {@link github.anandb.netbeans.model.ProcessedMessage} blocks and passes them to the handler
 * for UI rendering.
 */
public interface DataExtractionStrategy {

    /**
     * Determines whether this strategy can process the given update.
     *
     * @param update           the raw session update from the SSE stream
     * @param reclassifiedType the content type (e.g. "thought", "tool", "code")
     * @return true if this strategy handles the update, false otherwise
     */
    boolean canHandle(SessionUpdate update, String reclassifiedType);

    /**
     * Extracts processed content from the update and dispatches it to the UI.
     *
     * @param update  the raw session update to process
     * @param handler the UI handler to receive processed messages and metadata
     */
    void extract(SessionUpdate update, UIHandler handler);
}
