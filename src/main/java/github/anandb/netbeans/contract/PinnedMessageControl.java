package github.anandb.netbeans.contract;

/**
 * Port for pinned-message state management.
 * UI layer depends on this interface (via Lookup) to query and toggle pin state.
 * Implementations handle persistence (NbPreferences).
 */
public interface PinnedMessageControl {

    /** Returns {@code true} if the given message is pinned within the session. */
    boolean isPinned(String sessionId, String messageId);

    /** Sets the pinned state of a message within a session and persists it. */
    void setPinned(String sessionId, String messageId, boolean pinned);

    /**
     * Primes the in-memory cache for a session from the given candidate message IDs.
     * Call once at the start of {@code ChatThreadPanel.setMessages(sessionId, messages)}
     * to avoid per-bubble NbPreferences reads.
     */
    void loadSession(String sessionId, java.util.Collection<String> messageIds);

    /** Evicts the in-memory cache for the given session. */
    void unloadSession(String sessionId);
}
