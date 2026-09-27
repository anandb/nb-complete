package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.MigrationReport;
import github.anandb.netbeans.model.SessionMetadata;

import java.util.List;
import java.util.Map;

/**
 * Port over the plugin's local store for the two datasets that outgrew
 * {@code NbPreferences}: the chat input history and the per-session metadata.
 *
 * <p>Implementations keep both datasets in memory, loaded by {@link #load()}
 * on a background thread. Reads therefore never touch the database and are safe
 * to call from the event dispatch thread; writes are enqueued and return at
 * once, so an EDT caller is never blocked on I/O.</p>
 *
 * <p>Nothing in this contract throws for a data problem: a migration that meets
 * unusable legacy entries reports them rather than failing.</p>
 */
public interface SessionStore {

    /** Bucket holding sessions of a harness that is not configured. */
    String NO_HARNESS = "default";

    /** Normalises a harness id to its metadata bucket name. */
    static String bucketName(String harnessId) {
        return harnessId == null || harnessId.isBlank() ? NO_HARNESS : harnessId;
    }

    /**
     * Loads both datasets from the database into memory. Must run off the EDT.
     * A database that cannot be opened logs and leaves the datasets empty; it
     * never throws.
     */
    void load();

    /**
     * Runs the one-shot migration of the legacy preference data into this store.
     * Idempotent: a second call after a completed run does nothing. Never
     * throws — entries that cannot be recovered are counted and returned.
     *
     * @return what was recovered and what was left behind, never null
     */
    MigrationReport migrateLegacyPrefs();

    /**
     * The input history, newest entry last. Memory only, never blocks.
     *
     * @return an immutable snapshot; may be empty before {@link #load()} completes
     */
    List<String> inputHistory();

    /**
     * Appends one history entry, dropping the oldest once the cap is reached.
     * An empty or null entry, or one equal to the newest entry, is ignored.
     */
    void appendInputHistory(String text);

    /**
     * Metadata for one harness, keyed by session id, newest session first.
     * Memory only, never blocks.
     *
     * <p>The returned map is a read-only <em>snapshot</em>: a later write does not
     * appear in a map already held, so a caller that needs the new value must ask
     * again. Returning a snapshot is what lets readers iterate without locking.</p>
     *
     * @param harnessId the harness id, or null for the unconfigured-harness
     *                  bucket; never null in the returned value
     */
    Map<String, SessionMetadata> sessionMetadata(String harnessId);

    /**
     * Inserts or replaces one session's metadata. A session not yet present is
     * recorded as the newest for its harness, which preserves the session
     * ordering the dropped {@code sessids_<harness>} nodes used to carry.
     * A null session id or a null harness bucket is ignored.
     */
    void saveSessionMetadata(String harnessId, String sessionId, SessionMetadata metadata);
}