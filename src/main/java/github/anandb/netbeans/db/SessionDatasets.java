package github.anandb.netbeans.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import github.anandb.netbeans.contract.SessionStore;
import github.anandb.netbeans.model.SessionMetadata;
import github.anandb.netbeans.support.Logger;

/**
 * Input history and per-session metadata: the two {@link SessionStore} datasets
 * that live in memory. Reads come from the EDT off volatile/snapshot caches;
 * every database access below runs on the lifecycle's executor, whose single
 * thread is therefore the only serialisation the migration needs.
 */
final class SessionDatasets {

    /** Input-history cap, matching the value {@code MessageHistory} enforced. */
    static final int HISTORY_CAP = 1024;

    /**
     * Input history, newest last. An immutable value published by a single
     * reference assignment, so a reader from the EDT always sees either the
     * previous list or the new one — never a half-replaced list.
     */
    private volatile List<String> history = List.of();

    /**
     * Metadata per harness bucket, newest session first. Each bucket is an
     * immutable snapshot replaced wholesale on write, so readers iterate without
     * locking and a write never mutates a map a reader already holds.
     */
    private final Map<String, Map<String, SessionMetadata>> metadata = new ConcurrentHashMap<>();

    /**
     * True once the metadata cache has been populated from the database. Until
     * then a caller's record is derived from nothing, so its blank fields mean
     * "unknown" rather than "clear".
     */
    private volatile boolean metadataLoaded;

    private final Logger log;

    SessionDatasets(Logger log) {
        this.log = log;
    }

    List<String> inputHistory() {
        return history;
    }

    void appendInputHistory(Db db, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<String> current = history;
        if (!current.isEmpty() && current.get(current.size() - 1).equals(text)) {
            return;
        }
        List<String> next = new ArrayList<>(current);
        next.add(text);
        while (next.size() > HISTORY_CAP) {
            next.remove(0);
        }
        history = List.copyOf(next);
        db.runDb(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("insert_input_history"))) {
                ps.setString(1, text);
                ps.executeUpdate();
                pruneHistory(db);
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to append an input history entry", e);
            }
        });
    }

    Map<String, SessionMetadata> sessionMetadata(String harnessId) {
        Map<String, SessionMetadata> bucket = metadata.get(SessionStore.bucketName(harnessId));
        return bucket != null ? bucket : Map.of();
    }

    void saveSessionMetadata(Db db, String harnessId, String sessionId, SessionMetadata sessionMetadata) {
        if (sessionId == null || sessionId.isEmpty() || sessionMetadata == null) {
            return;
        }
        String bucket = SessionStore.bucketName(harnessId);
        putInMemory(bucket, sessionId, sessionMetadata);
        // Sampled here, not in the task: a record built before the cache loaded
        // carries blanks that mean "unknown", and the stored row must fill them in
        // rather than be overwritten with nulls. Seeing the flag later would be too
        // late, since a load completing in between would hide the race.
        boolean fillFromStored = !metadataLoaded;
        db.runDb(() -> {
            try {
                SessionMetadata effective = fillFromStored
                        ? fillBlanksFromStored(db, bucket, sessionId, sessionMetadata)
                        : sessionMetadata;
                upsertSessionMeta(db, bucket, sessionId, effective, null);
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to save session metadata", e);
            }
        });
    }

    /**
     * Bulk form of {code saveSessionMetadata}: one snapshot rebuild in memory and
     * one transaction on the database thread, instead of a round trip per session.
     */
    void saveSessionMetadataBulk(Db db, String harnessId, Map<String, SessionMetadata> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        String bucket = SessionStore.bucketName(harnessId);
        Map<String, SessionMetadata> snapshot = new LinkedHashMap<>(updates);
        putAllInMemory(bucket, snapshot);
        boolean fillFromStored = !metadataLoaded;
        db.runDb(() -> {
            try {
                upsertSessionMetaBatch(db, bucket, snapshot, fillFromStored);
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to save session metadata batch", e);
            }
        });
    }

    /**
     * Fills a record's unknown fields from the stored row. Used only for a write
     * that raced startup, where the caller could not have known the saved values;
     * the stored hidden flag wins for the same reason.
     */
    private SessionMetadata fillBlanksFromStored(Db db, String bucket, String sessionId, SessionMetadata meta)
            throws SQLException {
        SessionMetadata stored = storedMetadata(db.connection(), bucket, sessionId);
        if (stored == null) {
            return meta;
        }
        return new SessionMetadata(
                meta.title() != null ? meta.title() : stored.title(),
                meta.usage() != null ? meta.usage() : stored.usage(),
                stored.hidden(),
                meta.cwd() != null ? meta.cwd() : stored.cwd());
    }

    /** One session's stored row, or null when it has none. */
    private SessionMetadata storedMetadata(Connection conn, String bucket, String sessionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(Sql.get("select_session_meta_row"))) {
            ps.setString(1, bucket);
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String title = rs.getString(1);
                boolean hidden = rs.getBoolean(2);
                String cwd = rs.getString(3);
                String usage = usageString(rs, 4, 5);
                return new SessionMetadata(title, usage, hidden, cwd);
            }
        }
    }

    // ---- SessionStore memory ----

    /**
     * Replaces one bucket with a new immutable snapshot holding {@code meta}.
     * A session already present keeps its position; one not seen before becomes
     * the newest for its harness, which is how the dropped id lists ordered it.
     */
    private void putInMemory(String bucket, String sessionId, SessionMetadata meta) {
        this.metadata.compute(bucket, (key, current) -> {
            LinkedHashMap<String, SessionMetadata> next = new LinkedHashMap<>();
            boolean replaced = false;
            if (current != null) {
                for (Map.Entry<String, SessionMetadata> entry : current.entrySet()) {
                    if (sessionId.equals(entry.getKey())) {
                        next.put(entry.getKey(), meta);
                        replaced = true;
                    } else {
                        next.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (replaced) {
                return Collections.unmodifiableMap(next);
            }
            LinkedHashMap<String, SessionMetadata> withNew = new LinkedHashMap<>();
            withNew.put(sessionId, meta);
            withNew.putAll(next);
            return Collections.unmodifiableMap(withNew);
        });
    }

    /**
     * Bulk form of {code putInMemory}: one snapshot rebuild for the whole batch.
     * Existing sessions keep their position; new ones become the newest, in batch
     * order.
     */
    private void putAllInMemory(String bucket, Map<String, SessionMetadata> updates) {
        this.metadata.compute(bucket, (key, current) -> {
            LinkedHashMap<String, SessionMetadata> existing = new LinkedHashMap<>();
            if (current != null) {
                for (Map.Entry<String, SessionMetadata> entry : current.entrySet()) {
                    SessionMetadata replacement = updates.get(entry.getKey());
                    existing.put(entry.getKey(), replacement != null ? replacement : entry.getValue());
                }
            }
            LinkedHashMap<String, SessionMetadata> added = new LinkedHashMap<>();
            for (Map.Entry<String, SessionMetadata> entry : updates.entrySet()) {
                if (current == null || !current.containsKey(entry.getKey())) {
                    added.put(entry.getKey(), entry.getValue());
                }
            }
            if (added.isEmpty()) {
                return Collections.unmodifiableMap(existing);
            }
            LinkedHashMap<String, SessionMetadata> withNew = new LinkedHashMap<>(added);
            withNew.putAll(existing);
            return Collections.unmodifiableMap(withNew);
        });
    }

    // ---- SessionStore database ----

    void loadHistory(Db db) throws SQLException {
        List<String> loaded = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("select_input_history"));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                loaded.add(rs.getString(1));
            }
        }
        // One assignment, so a reader never observes an empty intermediate state.
        history = List.copyOf(loaded);
    }

    /** Reads every row, oldest first per harness so the exposed order is newest-first. */
    void loadMetadata(Db db) throws SQLException {
        Map<String, LinkedHashMap<String, SessionMetadata>> byHarness = new LinkedHashMap<>();
        String sql = Sql.get("select_all_session_meta");
        try (PreparedStatement ps = db.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String bucket = SessionStore.bucketName(rs.getString(1));
                String sessionId = rs.getString(2);
                String usage = usageString(rs, 6, 7);
                byHarness.computeIfAbsent(bucket, k -> new LinkedHashMap<>())
                        .put(sessionId, new SessionMetadata(
                                rs.getString(3), usage, rs.getBoolean(4), rs.getString(5)));
            }
        }
        for (Map.Entry<String, LinkedHashMap<String, SessionMetadata>> entry : byHarness.entrySet()) {
            List<Map.Entry<String, SessionMetadata>> ordered = new ArrayList<>(entry.getValue().entrySet());
            Collections.reverse(ordered);
            LinkedHashMap<String, SessionMetadata> newestFirst = new LinkedHashMap<>();
            for (Map.Entry<String, SessionMetadata> row : ordered) {
                newestFirst.put(row.getKey(), row.getValue());
            }
            metadata.put(entry.getKey(), Collections.unmodifiableMap(newestFirst));
        }
        metadataLoaded = true;
    }

    void upsertSessionMeta(Db db, String bucket, String sessionId, SessionMetadata meta,
                                   Long explicitOrdinal) throws SQLException {
        Long ordinal = explicitOrdinal != null ? explicitOrdinal : existingOrdinal(db, bucket, sessionId);
        if (ordinal == null) {
            ordinal = nextOrdinal(db, bucket);
        }
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("merge_session_meta"))) {
            bindSessionMeta(ps, bucket, sessionId, meta, ordinal);
            ps.executeUpdate();
        }
    }

    /**
     * Writes a batch of metadata rows in one transaction, resolving each session's
     * ordinal from a single read of the bucket. New sessions take the ordinals
     * after the current maximum, in batch order.
     */
    private void upsertSessionMetaBatch(Db db, String bucket, Map<String, SessionMetadata> updates,
            boolean fillFromStored) throws SQLException {
        Connection conn = db.connection();
        DbLifecycle.inTransaction(conn, () -> {
            try (PreparedStatement ps = conn.prepareStatement(Sql.get("merge_session_meta"))) {
                Map<String, Long> ordinals = storedOrdinals(db, bucket);
                long highest = 0L;
                for (long value : ordinals.values()) {
                    highest = Math.max(highest, value);
                }
                for (Map.Entry<String, SessionMetadata> entry : updates.entrySet()) {
                    SessionMetadata meta = fillFromStored
                            ? fillBlanksFromStored(db, bucket, entry.getKey(), entry.getValue())
                            : entry.getValue();
                    Long ordinal = ordinals.get(entry.getKey());
                    if (ordinal == null) {
                        ordinal = ++highest;
                    }
                    bindSessionMeta(ps, bucket, entry.getKey(), meta, ordinal);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    /** Binds one {@code merge_session_meta} row. */
    private static void bindSessionMeta(PreparedStatement ps, String bucket, String sessionId,
            SessionMetadata meta, long ordinal) throws SQLException {
        Long[] usage = parseUsage(meta.usage());
        ps.setString(1, bucket);
        ps.setString(2, sessionId);
        ps.setString(3, meta.title());
        ps.setBoolean(4, meta.hidden());
        ps.setString(5, meta.cwd());
        UsageQueries.setNullableLong(ps, 6, usage[0]);
        UsageQueries.setNullableLong(ps, 7, usage[1]);
        ps.setLong(8, ordinal);
    }

    /** Every stored ordinal in a bucket, keyed by session id. */
    private Map<String, Long> storedOrdinals(Db db, String bucket) throws SQLException {
        Map<String, Long> ordinals = new HashMap<>();
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("select_session_ordinals"))) {
            ps.setString(1, bucket);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ordinals.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return ordinals;
    }

    /** The stored ordinal for a session, or null when it has no row yet. */
    private Long existingOrdinal(Db db, String bucket, String sessionId) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("select_session_ordinal"))) {
            ps.setString(1, bucket);
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Next free ordinal in a bucket; the newest session carries the highest. */
    private long nextOrdinal(Db db, String bucket) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("select_next_ordinal"))) {
            ps.setString(1, bucket);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1L;
            }
        }
    }

    /** Rows currently in the history table; 0 also means the table cannot be read. */
    long historyRowCount(Db db) {
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("count_input_history"));
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            log.log(Level.FINE, "Could not count input history rows; treating as empty", e);
            return 0;
        }
    }

    /** Drops all but the newest {@code HISTORY_CAP} history rows. */
    private void pruneHistory(Db db) throws SQLException {
        Long oldestKept = null;
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("prune_history_seq"))) {
            ps.setInt(1, HISTORY_CAP - 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    oldestKept = rs.getLong(1);
                }
            }
        }
        if (oldestKept == null) {
            return;
        }
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("delete_input_history_before"))) {
            ps.setLong(1, oldestKept);
            ps.executeUpdate();
        }
    }

    /**
     * Rebuilds the "{@code used,size}" string the UI parses from the two stored
     * columns. Both absent reads back as null, which is what an unset usage was.
     */
    static String usageString(ResultSet rs, int usedIndex, int sizeIndex) throws SQLException {
        long used = rs.getLong(usedIndex);
        boolean usedAbsent = rs.wasNull();
        long size = rs.getLong(sizeIndex);
        boolean sizeAbsent = rs.wasNull();
        if (usedAbsent && sizeAbsent) {
            return null;
        }
        return (usedAbsent ? 0 : used) + "," + (sizeAbsent ? 0 : size);
    }

    /** Splits the "{@code used,size}" string into the two columns; nulls when unusable. */
    static Long[] parseUsage(String usage) {
        if (usage == null || usage.isBlank()) {
            return new Long[]{null, null};
        }
        String[] parts = usage.split(",", -1);
        return new Long[]{
            parseLongOrNull(parts.length > 0 ? parts[0] : null),
            parseLongOrNull(parts.length > 1 ? parts[1] : null)};
    }

    static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    /** Minimal seam over the facade: executor, connection, and log. */
    interface Db {
        Connection connection() throws SQLException;

        void runDb(Runnable task);

        <T> T call(Callable<T> task, T fallback);

        Logger log();
    }

}
