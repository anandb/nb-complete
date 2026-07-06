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
     * Primes the in-memory cache for a session from persisted storage.
     * Call when the session becomes active (e.g. in onSessionLoaded).
     */
    void loadSession(String sessionId);

    /** Evicts the in-memory cache for the given session. */
    void unloadSession(String sessionId);
}
