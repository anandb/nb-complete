package github.anandb.netbeans.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import github.anandb.netbeans.model.AvailableModel;
import github.anandb.netbeans.support.Logger;

/**
 * Pinned messages and per-harness model lists: the two datasets that outgrew
 * preference values. Reads are memory-only (caches primed by load()); writes
 * enqueue on the lifecycle's executor. A write that races startup leaves a
 * dirty flag so the older database snapshot cannot replace it on load.
 */
final class PinModelDatasets {

    /** How long a model the harness stops reporting is kept before its row is dropped. */
    static final long MODEL_RETENTION_MS = TimeUnit.HOURS.toMillis(1);

    /**
     * Pinned message ids per session, as immutable snapshots. Reads come from the
     * EDT and that is the whole reason the database is not consulted: the cache is
     * populated by load() on the database thread.
     */
    private final Map<String, Set<String>> pins = new ConcurrentHashMap<>();

    /**
     * Sessions whose pins were written after the last cache load. A write that
     * races startup must not be replaced by the older database snapshot, so
     * loadPins() leaves these sessions alone.
     */
    private final Set<String> pinsDirty = ConcurrentHashMap.newKeySet();

    /**
     * Last known model list per harness with the time each model was last reported.
     * Immutable snapshots, same convention as the pin cache.
     */
    private final Map<String, List<TimedModel>> models = new ConcurrentHashMap<>();

    /** Harnesses whose model list was written after the last cache load. */
    private final Set<String> modelsDirty = ConcurrentHashMap.newKeySet();

    /** Serialises the read-modify-write of one session's pin set on the caller thread. */
    private final Object pinsLock = new Object();

    /** Serialises the read-modify-write of one harness's model list on the caller thread. */
    private final Object modelsLock = new Object();

    private final Logger log = Logger.from(PinModelDatasets.class);

    /** A cached model and the time the harness last reported it. */
    record TimedModel(AvailableModel model, long lastSeen) { }

    /** Wraps models with one observation time, for the legacy migration. */
    static List<TimedModel> stamp(List<AvailableModel> list, long now) {
        List<TimedModel> rows = new ArrayList<>(list.size());
        for (AvailableModel model : list) {
            if (model != null) {
                rows.add(new TimedModel(model, now));
            }
        }
        return List.copyOf(rows);
    }

    void loadPins(Db db) throws SQLException {
        Map<String, LinkedHashSet<String>> loaded = new HashMap<>();
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("select_pins"));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                loaded.computeIfAbsent(rs.getString(1), k -> new LinkedHashSet<>())
                        .add(rs.getString(2));
            }
        }
        loaded.forEach((sessionId, ids) -> {
            if (!pinsDirty.contains(sessionId)) {
                pins.put(sessionId, Collections.unmodifiableSet(ids));
            }
        });
    }

    void loadModels(Db db) throws SQLException {
        Map<String, List<TimedModel>> loaded = new LinkedHashMap<>();
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("select_models"));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                loaded.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(new TimedModel(new AvailableModel(rs.getString(2), rs.getString(3), rs.getString(4)),
                                rs.getLong(5)));
            }
        }
        // No retention pass here: the window is measured from a harness's most
        // recent report, so rows loaded after a long break must survive until
        // that harness reports again.
        loaded.forEach((harnessId, list) -> {
            if (!modelsDirty.contains(harnessId)) {
                models.put(harnessId, List.copyOf(list));
            }
        });
    }

    boolean isPinned(String sessionId, String messageId) {
        if (sessionId == null || messageId == null) {
            return false;
        }
        Set<String> pinned = pins.get(sessionId);
        return pinned != null && pinned.contains(messageId);
    }

    void setPinned(Db db, String sessionId, String messageId, boolean pinned) {
        if (sessionId == null || messageId == null) {
            return;
        }
        synchronized (pinsLock) {
            Set<String> current = pins.getOrDefault(sessionId, Set.of());
            if (current.contains(messageId) == pinned) {
                return;
            }
            LinkedHashSet<String> updated = new LinkedHashSet<>(current);
            if (pinned) {
                updated.add(messageId);
            } else {
                updated.remove(messageId);
            }
            pins.put(sessionId, Collections.unmodifiableSet(updated));
            pinsDirty.add(sessionId);
        }
        long now = System.currentTimeMillis();
        db.runDb(() -> {
            try {
                // Row-level delta, never a snapshot replace: a write that races the
                // startup load must not resurrect rows the load has not seen yet.
                if (pinned) {
                    try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("merge_pinned_message"))) {
                        ps.setString(1, sessionId);
                        ps.setString(2, messageId);
                        ps.setLong(3, now);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("delete_pin"))) {
                        ps.setString(1, sessionId);
                        ps.setString(2, messageId);
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to persist the pin state of a message", e);
            }
        });
    }

    void loadSession(String sessionId) {
        // load() primed every session's pins from the database at startup, so
        // there is nothing per-session left to read. Kept for the interface.
    }

    void retainPinned(Db db, String sessionId, Set<String> activeMessageIds) {
        if (sessionId == null || activeMessageIds == null) {
            return;
        }
        Set<String> kept;
        synchronized (pinsLock) {
            Set<String> current = pins.get(sessionId);
            if (current == null || activeMessageIds.containsAll(current)) {
                return;
            }
            LinkedHashSet<String> remaining = new LinkedHashSet<>(current);
            remaining.retainAll(activeMessageIds);
            kept = Collections.unmodifiableSet(remaining);
            pins.put(sessionId, kept);
            pinsDirty.add(sessionId);
        }
        log.info("Pinned-message cleanup for session {0}: kept {1}",
                sessionId, kept.size());
        Set<String> snapshot = kept;
        db.runDb(() -> {
            try {
                replacePins(db, sessionId, snapshot);
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to prune stale pinned messages", e);
            }
        });
    }

    void unloadSession(String sessionId) {
        // Intentionally no eviction: the cache is small (ids only) and dropping it
        // would make the next read a blocking database call from the EDT.
    }

    List<AvailableModel> getModels(String harnessId) {
        if (harnessId == null || harnessId.isBlank()) {
            return List.of();
        }
        List<TimedModel> cached = models.get(harnessId);
        if (cached == null) {
            return List.of();
        }
        return cached.stream().map(TimedModel::model).toList();
    }

    void updateModels(Db db, String harnessId, List<AvailableModel> list) {
        updateModels(db, harnessId, list, System.currentTimeMillis());
    }

    /**
     * Records {@code list} as the harness's current models. Included models are
     * stamped with {@code now}; models the harness stopped reporting are kept
     * until {@code MODEL_RETENTION_MS} has elapsed since they were last seen,
     * measured from this newest report. Package-private so tests can supply the
     * clock.
     */
    void updateModels(Db db, String harnessId, List<AvailableModel> list, long now) {
        if (harnessId == null || harnessId.isBlank() || list == null || list.isEmpty()) {
            return;
        }
        List<TimedModel> merged;
        synchronized (modelsLock) {
            merged = mergeModels(harnessId, list, now);
            models.put(harnessId, merged);
        }
        modelsDirty.add(harnessId);
        db.runDb(() -> {
            try {
                replaceModels(db, harnessId, merged);
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to persist the model list for harness " + harnessId, e);
            }
        });
    }

    /** Incoming models first, then the harness's previously known models that are still
     *  inside the retention window; expired models are dropped. Caller holds {@code modelsLock}. */
    private List<TimedModel> mergeModels(String harnessId, List<AvailableModel> list, long now) {
        long cutoff = now - MODEL_RETENTION_MS;
        List<TimedModel> merged = new ArrayList<>(list.size() + 4);
        Set<String> seen = new HashSet<>();
        for (AvailableModel model : list) {
            if (model == null || model.modelId() == null || !seen.add(model.modelId())) {
                continue;
            }
            merged.add(new TimedModel(model, now));
        }
        for (TimedModel previous : models.getOrDefault(harnessId, List.of())) {
            String modelId = previous.model().modelId();
            if (modelId != null && !seen.contains(modelId) && previous.lastSeen() >= cutoff) {
                seen.add(modelId);
                merged.add(previous);
            }
        }
        return List.copyOf(merged);
    }




    /** Rewrites one session's pin rows in a transaction; used by the stale-pin cleanup. */
    void replacePins(Db db, String sessionId, Set<String> pinned) throws SQLException {
        Connection conn = db.connection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement delete = conn.prepareStatement(Sql.get("delete_session_pins"))) {
                delete.setString(1, sessionId);
                delete.executeUpdate();
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement insert = conn.prepareStatement(Sql.get("merge_pinned_message"))) {
                for (String messageId : pinned) {
                    insert.setString(1, sessionId);
                    insert.setString(2, messageId);
                    insert.setLong(3, now);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    /** Rewrites one harness's model rows in a transaction, preserving list order. */
    void replaceModels(Db db, String harnessId, List<TimedModel> list) throws SQLException {
        Connection conn = db.connection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement delete = conn.prepareStatement(Sql.get("delete_harness_models"))) {
                delete.setString(1, harnessId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = conn.prepareStatement(Sql.get("insert_harness_model"))) {
                int position = 0;
                for (TimedModel row : list) {
                    AvailableModel model = row.model();
                    insert.setString(1, harnessId);
                    insert.setInt(2, position++);
                    insert.setString(3, model.modelId());
                    insert.setString(4, model.name());
                    insert.setString(5, model.description());
                    insert.setLong(6, row.lastSeen());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }
    /** Minimal seam over the facade: executor connection plus the lifecycle runner. */
    interface Db {
        Connection connection() throws SQLException;

        void runDb(Runnable task);
    }

}
