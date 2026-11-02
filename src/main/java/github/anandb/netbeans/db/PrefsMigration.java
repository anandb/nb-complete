package github.anandb.netbeans.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.contract.SessionStore;
import github.anandb.netbeans.model.AvailableModel;
import github.anandb.netbeans.model.MigrationReport;
import github.anandb.netbeans.model.SessionMetadata;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.PreferenceKeys;

/**
 * One-shot migration of the legacy preference data into the tables. Runs once
 * per database, guarded by one marker per dataset. Containment is per entry:
 * a value that will not parse is counted in the report and its key left in
 * place, while everything else migrates. Order matters: rows first, then the
 * marker, then the preference data is deleted — and only for the entries whose
 * value reached the database, so a database that cannot be written never costs
 * the user their data. A failed write is retryable, so it leaves the marker
 * unset and the next launch retries what it kept.
 */
final class PrefsMigration {

    private final Logger log;
    private final ObjectMapper mapper;

    PrefsMigration(Logger log) {
        this.log = log;
        this.mapper = MapperSupplier.get();
    }

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() { };
    private static final TypeReference<List<AvailableModel>> MODEL_LIST = new TypeReference<>() { };

    /** Set once the legacy {@code NbPreferences} data has been copied in. */
    private static final String PREFS_MIGRATED_KEY = "prefs_migrated";

    /** Set once {@code pinnedMessages.*} keys have been copied into {@code pinned_message}. */
    private static final String PINNED_MIGRATED_KEY = "pinned_migrated";

    /** Set once {@code modelList.*} keys have been copied into {@code harness_model}. */
    private static final String MODELS_MIGRATED_KEY = "models_migrated";

    /** Input-history cap, matching the value {@code MessageHistory} enforced. */
    private static final int HISTORY_CAP = 1024;

    /** Retention cap on the skip reasons a {@code MigrationReport} carries. */
    private static final int MAX_REPORT_REASONS = 10;

    // Legacy NbPreferences layout, read once by the migration. Kept as literals
    // here because these names have no producer left in the codebase.
    /** Child node holding {@code sessionId -> SessionMetadata JSON}. */
    private static final String LEGACY_META_NODE = "sessmeta";
    /** Child node holding {@code sessionId -> position} for locally-created sessions. */
    private static final String LEGACY_IDS_NODE = "sessids";
    /** Root keys holding per-session values, folded into the metadata rows. */
    private static final String LEGACY_TITLE_PREFIX = "session_title_";
    private static final String LEGACY_HIDDEN_PREFIX = "session_hidden_";
    private static final String LEGACY_USAGE_PREFIX = "session_usage_";
    /** Root keys holding the comma-joined locally-created id list. */
    private static final String LEGACY_LOCAL_SESSIONS_KEY = "gemini_local_sessions";
    /** Root keys holding a session's pinned message ids as a JSON array. */
    private static final String LEGACY_PINNED_PREFIX = "pinnedMessages.";
    /** Root keys holding a harness's model list as a JSON array. */
    private static final String LEGACY_MODEL_PREFIX = "modelList.";
    /** Root keys superseded by the store; no code reads them. */
    private static final List<String> OBSOLETE_KEYS = List.of("caveman.enabled");

    // ---- One-shot preference migration ----

    /**
     * Copies the legacy preference data into the tables, once per database.
     *
     * <p>Containment is per entry: a value that will not parse is counted in the
     * report and its key left in place, while everything else migrates. A node
     * that cannot be listed costs only that node. Nothing here propagates an
     * exception, so a damaged legacy file can never block startup.</p>
     *
     * <p>Order matters: rows are written first, then the marker, then the
     * preference data is deleted, and only for the entries whose value reached the
     * database — so a database that cannot be written never costs the user their
     * data. A skipped value is final, so it does not stop the marker; a failed
     * write is not, so it leaves the marker unset and the next launch retries what
     * it kept.</p>
     */
    MigrationReport runPrefsMigration(Db db) {
        if (!db.ensureDatabase()) {
            // Nothing was read or written, so there is nothing to report. Saying
            // "some data could not be migrated" here would blame the user's
            // preferences for a database another process is holding open.
            return MigrationReport.none();
        }
        List<String> reasons = new ArrayList<>();
        int[] migrated = {0};
        int[] skipped = {0};
        int[] writeFailures = {0};
        Preferences root = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);

        if (!isMigrated(db, PREFS_MIGRATED_KEY)) {
            Set<String> bucketsToKeep = new LinkedHashSet<>();
            Set<String> historyKeys = migrateHistory(db, root, migrated, skipped, writeFailures, reasons);
            List<String> metaNodes = childNodeNames(root, LEGACY_META_NODE, reasons);
            List<String> idNodes = childNodeNames(root, LEGACY_IDS_NODE, reasons);
            Map<String, Map<String, SessionMetadata>> byHarness =
                    readLegacyMetadata(root, metaNodes, skipped, reasons, bucketsToKeep);
            Map<String, Map<String, Long>> legacyIds = readLegacyIds(root, idNodes, reasons);
            Map<String, Set<String>> foldedKeys = foldLegacyRootKeys(root, byHarness, skipped, reasons);
            Set<String> failedBuckets =
                    writeMigratedMetadata(db, byHarness, legacyIds, migrated, skipped, reasons);
            bucketsToKeep.addAll(failedBuckets);

            // A parse skip is final, a write failure is not: only a run that stored
            // everything may record the marker, so the next launch retries the rest.
            boolean stored = writeFailures[0] == 0 && failedBuckets.isEmpty();
            if (stored && !markMigrated(db, PREFS_MIGRATED_KEY)) {
                log.log(Level.WARNING,
                        "Preference migration could not record its marker; leaving preferences untouched");
            } else {
                deleteLegacyPreferences(root, metaNodes, idNodes, bucketsToKeep, historyKeys,
                        foldedKeys, failedBuckets, reasons);
                if (!stored) {
                    log.log(Level.WARNING,
                            "Preference migration could not store every entry; retrying on the next launch");
                }
            }
        }

        // Pins and model lists moved in a later release and carry their own markers:
        // an installation that ran the first migration already has PREFS_MIGRATED_KEY
        // set, so the guard above would otherwise strand them in preferences forever.
        migratePinnedMessages(db, root, migrated, skipped, reasons);
        migrateModelLists(db, root, migrated, skipped, reasons);

        MigrationReport report = new MigrationReport(migrated[0], skipped[0], reasons);
        if (report.hasSkips()) {
            log.log(Level.WARNING, "Preference migration moved {0} entries and skipped {1}",
                    new Object[]{report.migrated(), report.skipped()});
        } else if (report.migrated() > 0) {
            log.log(Level.INFO, "Preference migration moved {0} entries into the database",
                    report.migrated());
        }
        return report;
    }

    private boolean isMigrated(Db db, String marker) {
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("select_meta_value"))) {
            ps.setString(1, marker);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "true".equalsIgnoreCase(rs.getString(1));
            }
        } catch (SQLException e) {
            log.log(Level.FINE, "Could not read the migration marker; treating as not migrated", e);
            return false;
        }
    }

    /** @return true when the database accepted the marker, so the sources may be deleted. */
    private boolean markMigrated(Db db, String marker) {
        try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("merge_meta_value"))) {
            ps.setString(1, marker);
            ps.setString(2, "true");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.log(Level.WARNING, "Could not record the preference migration marker", e);
            return false;
        }
    }

    /**
     * Copies {@code pinnedMessages.<sessionId>} JSON arrays into {@code pinned_message}.
     * Rows first, then the marker, then the keys — and only for sessions whose rows
     * were written, so an unwritable database never costs the user their pins.
     */
    private void migratePinnedMessages(Db db, Preferences root, int[] migrated, int[] skipped,
                                       List<String> reasons) {
        if (isMigrated(db, PINNED_MIGRATED_KEY)) {
            return;
        }
        Map<String, List<String>> parsed = new LinkedHashMap<>();
        for (String key : rootKeys(root, reasons)) {
            if (!key.startsWith(LEGACY_PINNED_PREFIX)) {
                continue;
            }
            String sessionId = key.substring(LEGACY_PINNED_PREFIX.length());
            try {
                List<String> ids = mapper.readValue(root.get(key, "[]"), LIST_OF_STRINGS);
                parsed.put(sessionId, ids == null ? List.of() : ids);
            } catch (IOException | RuntimeException e) {
                skipped[0]++;
                addReason(reasons, "pinned messages: " + sessionId + ": " + e.getMessage());
            }
        }
        List<String> written = new ArrayList<>();
        int failures = 0;
        for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
            try {
                Set<String> ids = new LinkedHashSet<>(entry.getValue());
                ids.remove(null);
                db.pins().replacePins(db.pinDb(), entry.getKey(), ids);
                written.add(entry.getKey());
                migrated[0]++;
            } catch (SQLException e) {
                failures++;
                skipped[0]++;
                addReason(reasons, "pinned messages: " + entry.getKey() + ": could not be stored");
            }
        }
        if (failures > 0) {
            log.log(Level.WARNING,
                    "Could not store every pinned-message entry; retrying on the next launch");
        } else if (!markMigrated(db, PINNED_MIGRATED_KEY)) {
            log.log(Level.WARNING, "Could not record the pinned-message migration marker");
            return;
        }
        for (String sessionId : written) {
            root.remove(LEGACY_PINNED_PREFIX + sessionId);
        }
        flushPreferences(root, reasons);
    }

    /**
     * Copies {@code modelList.<harnessId>} JSON arrays into {@code harness_model}.
     * Same order and containment as the pinned migration.
     */
    private void migrateModelLists(Db db, Preferences root, int[] migrated, int[] skipped,
                                   List<String> reasons) {
        if (isMigrated(db, MODELS_MIGRATED_KEY)) {
            return;
        }
        Map<String, List<AvailableModel>> parsed = new LinkedHashMap<>();
        for (String key : rootKeys(root, reasons)) {
            if (!key.startsWith(LEGACY_MODEL_PREFIX)) {
                continue;
            }
            String harnessId = key.substring(LEGACY_MODEL_PREFIX.length());
            try {
                List<AvailableModel> models = mapper.readValue(root.get(key, "[]"), MODEL_LIST);
                if (models == null) {
                    models = List.of();
                }
                models.removeIf(Objects::isNull);
                parsed.put(harnessId, models);
            } catch (IOException | RuntimeException e) {
                skipped[0]++;
                addReason(reasons, "model list: " + harnessId + ": " + e.getMessage());
            }
        }
        long now = System.currentTimeMillis();
        List<String> written = new ArrayList<>();
        int failures = 0;
        for (Map.Entry<String, List<AvailableModel>> entry : parsed.entrySet()) {
            try {
                db.pins().replaceModels(db.pinDb(), entry.getKey(), PinModelDatasets.stamp(entry.getValue(), now));
                written.add(entry.getKey());
                migrated[0]++;
            } catch (SQLException e) {
                failures++;
                skipped[0]++;
                addReason(reasons, "model list: " + entry.getKey() + ": could not be stored");
            }
        }
        if (failures > 0) {
            log.log(Level.WARNING, "Could not store every model list entry; retrying on the next launch");
        } else if (!markMigrated(db, MODELS_MIGRATED_KEY)) {
            log.log(Level.WARNING, "Could not record the model-list migration marker");
            return;
        }
        for (String harnessId : written) {
            root.remove(LEGACY_MODEL_PREFIX + harnessId);
        }
        flushPreferences(root, reasons);
    }


    private static List<String> rootKeys(Preferences root, List<String> reasons) {
        try {
            return List.of(root.keys());
        } catch (BackingStoreException e) {
            addReason(reasons, "preference keys: could not be listed: " + e.getMessage());
            return List.of();
        }
    }

    private static void flushPreferences(Preferences root, List<String> reasons) {
        try {
            root.flush();
        } catch (BackingStoreException e) {
            addReason(reasons, "preference keys: could not be flushed: " + e.getMessage());
        }
    }

    /**
     * Copies the {@code inputHistory.*} keys into {@code input_history}.
     *
     * @return the keys whose value reached the database, plus the ones the history cap
     *         drops. Every other key stays in preferences: a failed insert (a partial
     *         import cannot be resumed without duplicating entries), an unreadable
     *         position, and a table that already holds rows are all reasons to keep it.
     */
    private Set<String> migrateHistory(Db db, Preferences root, int[] migrated, int[] skipped,
                                      int[] writeFailures, List<String> reasons) {
        Set<String> consumed = new LinkedHashSet<>();
        List<String> historyKeys = new ArrayList<>();
        try {
            for (String key : root.keys()) {
                if (key.startsWith(PreferenceKeys.INPUT_HISTORY_PREFIX)
                        && !key.equals(PreferenceKeys.INPUT_HISTORY_COUNT)) {
                    historyKeys.add(key);
                }
            }
        } catch (BackingStoreException e) {
            skipped[0]++;
            addReason(reasons, "input history: could not be listed: " + e.getMessage());
            return consumed;
        }
        if (db.sessions().historyRowCount(db.sessionDb()) > 0) {
            // Rows are already there, so a previous run inserted them — the absent
            // marker only means that run was interrupted before it finished. Plain
            // INSERTs are not absorbed by an upsert, so importing again would
            // duplicate every entry. Nothing was imported now, so no key may go.
            return consumed;
        }
        Map<Integer, String> byPosition = new TreeMap<>();
        Map<Integer, String> keyByPosition = new TreeMap<>();
        for (String key : historyKeys) {
            String value = root.get(key, null);
            if (value == null) {
                continue;
            }
            Integer position = SessionDatasets.parseIntOrNull(key.substring(PreferenceKeys.INPUT_HISTORY_PREFIX.length()));
            if (position == null) {
                skipped[0]++;
                addReason(reasons, "history entry " + key + ": unreadable position");
                continue;
            }
            byPosition.put(position, value);
            keyByPosition.put(position, key);
        }
        // Position 0 is the oldest, so writing in position order keeps the
        // auto-increment sequence newest-last, which is the exposed order.
        while (byPosition.size() > HISTORY_CAP) {
            Integer dropped = byPosition.keySet().iterator().next();
            byPosition.remove(dropped);
            consumed.add(keyByPosition.remove(dropped));
        }
        for (Map.Entry<Integer, String> entry : byPosition.entrySet()) {
            try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("insert_input_history"))) {
                ps.setString(1, entry.getValue());
                ps.executeUpdate();
                consumed.add(keyByPosition.get(entry.getKey()));
                migrated[0]++;
            } catch (SQLException e) {
                writeFailures[0]++;
                skipped[0]++;
                addReason(reasons, "history entry: " + e.getMessage());
            }
        }
        return consumed;
    }

    /** Child node names belonging to one legacy family, e.g. {@code sessmeta} and {@code sessmeta_x}. */
    private static List<String> childNodeNames(Preferences root, String family, List<String> reasons) {
        List<String> names = new ArrayList<>();
        try {
            for (String name : root.childrenNames()) {
                if (name.equals(family) || name.startsWith(family + "_")) {
                    names.add(name);
                }
            }
        } catch (BackingStoreException e) {
            addReason(reasons, family + " nodes: could not be listed: " + e.getMessage());
        }
        return names;
    }

    /** Maps a legacy node name back to its harness bucket. */
    private static String bucketFromNodeName(String nodeName, String family) {
        if (nodeName.equals(family) || nodeName.equals(family + "_")) {
            return SessionStore.NO_HARNESS;
        }
        return nodeName.substring(family.length() + 1);
    }

    /** Parses every legacy metadata entry, skipping only the entries that will not parse. */
    private Map<String, Map<String, SessionMetadata>> readLegacyMetadata(
            Preferences root, List<String> metaNodes, int[] skipped, List<String> reasons,
            Set<String> bucketsToKeep) {
        Map<String, Map<String, SessionMetadata>> byHarness = new LinkedHashMap<>();
        for (String nodeName : metaNodes) {
            Preferences node = root.node(nodeName);
            String bucket = bucketFromNodeName(nodeName, LEGACY_META_NODE);
            Map<String, SessionMetadata> sessions =
                    byHarness.computeIfAbsent(bucket, k -> new LinkedHashMap<>());
            try {
                for (String sessionId : node.keys()) {
                    String json = node.get(sessionId, null);
                    if (json == null) {
                        continue;
                    }
                    try {
                        sessions.put(sessionId, mapper.readValue(json, SessionMetadata.class));
                    } catch (Exception e) {
                        skipped[0]++;
                        bucketsToKeep.add(bucket);
                        addReason(reasons, nodeName + "/" + sessionId + ": " + e.getMessage());
                    }
                }
            } catch (BackingStoreException e) {
                skipped[0]++;
                bucketsToKeep.add(bucket);
                addReason(reasons, nodeName + ": could not be listed: " + e.getMessage());
            }
        }
        return byHarness;
    }

    /** Old per-harness session ordering: session id to position, 0 being the newest. */
    private Map<String, Map<String, Long>> readLegacyIds(Preferences root, List<String> idNodes,
                                                         List<String> reasons) {
        Map<String, Map<String, Long>> byHarness = new LinkedHashMap<>();
        for (String nodeName : idNodes) {
            Preferences node = root.node(nodeName);
            String bucket = bucketFromNodeName(nodeName, LEGACY_IDS_NODE);
            Map<String, Long> ids = byHarness.computeIfAbsent(bucket, k -> new LinkedHashMap<>());
            try {
                for (String sessionId : node.keys()) {
                    ids.put(sessionId, SessionDatasets.parseLongOrNull(node.get(sessionId, null)));
                }
            } catch (BackingStoreException e) {
                addReason(reasons, nodeName + ": ids could not be listed: " + e.getMessage());
            }
        }
        return byHarness;
    }

    /**
     * Folds the root-level per-session keys into the metadata they describe.
     * They predate the harness id, so a key is applied to every bucket that
     * already holds that session; one held by no bucket lands in the bucket for
     * an unconfigured harness, which is where the old reader would have put it.
     *
     * @return each folded key's harness buckets, so a bucket whose row never reached
     *         the database can keep the keys only it still holds
     */
    private static Map<String, Set<String>> foldLegacyRootKeys(
            Preferences root, Map<String, Map<String, SessionMetadata>> byHarness,
            int[] skipped, List<String> reasons) {
        Map<String, Set<String>> folded = new LinkedHashMap<>();
        try {
            for (String key : root.keys()) {
                String prefix = legacyFoldPrefix(key);
                if (prefix == null) {
                    continue;
                }
                String value = root.get(key, null);
                if (value == null) {
                    continue;
                }
                folded.put(key, applyLegacyValue(byHarness, key.substring(prefix.length()), prefix, value));
            }
        } catch (BackingStoreException e) {
            skipped[0]++;
            addReason(reasons, "per-session keys: could not be listed: " + e.getMessage());
        }
        return folded;
    }

    /** The folded root-key family {@code key} belongs to, or null when it belongs to none. */
    private static String legacyFoldPrefix(String key) {
        if (key.startsWith(LEGACY_TITLE_PREFIX)) {
            return LEGACY_TITLE_PREFIX;
        }
        if (key.startsWith(LEGACY_HIDDEN_PREFIX)) {
            return LEGACY_HIDDEN_PREFIX;
        }
        return key.startsWith(LEGACY_USAGE_PREFIX) ? LEGACY_USAGE_PREFIX : null;
    }

    /** @return the buckets the value was folded into. */
    private static Set<String> applyLegacyValue(Map<String, Map<String, SessionMetadata>> byHarness,
                                                String sessionId, String prefix, String value) {
        Set<String> buckets = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, SessionMetadata>> entry : byHarness.entrySet()) {
            SessionMetadata current = entry.getValue().get(sessionId);
            if (current != null) {
                entry.getValue().put(sessionId, withLegacyValue(current, prefix, value));
                buckets.add(entry.getKey());
            }
        }
        if (buckets.isEmpty()) {
            Map<String, SessionMetadata> fallback =
                    byHarness.computeIfAbsent(SessionStore.NO_HARNESS, k -> new LinkedHashMap<>());
            SessionMetadata current = fallback.get(sessionId);
            if (current == null) {
                current = new SessionMetadata(null, null, false, null);
            }
            fallback.put(sessionId, withLegacyValue(current, prefix, value));
            buckets.add(SessionStore.NO_HARNESS);
        }
        return buckets;
    }

    private static SessionMetadata withLegacyValue(SessionMetadata current, String prefix, String value) {
        if (LEGACY_TITLE_PREFIX.equals(prefix)) {
            return new SessionMetadata(value, current.usage(), current.hidden(), current.cwd());
        }
        if (LEGACY_USAGE_PREFIX.equals(prefix)) {
            return new SessionMetadata(current.title(), value, current.hidden(), current.cwd());
        }
        return new SessionMetadata(current.title(), current.usage(), Boolean.parseBoolean(value), current.cwd());
    }

    /**
     * Writes every legacy session, numbering rows so the newest ends up highest.
     *
     * @return the buckets in which at least one row failed to write, so their
     *         preference data is not deleted
     */
    private Set<String> writeMigratedMetadata(Db db,Map<String, Map<String, SessionMetadata>> byHarness,
                                              Map<String, Map<String, Long>> legacyIds,
                                              int[] migrated, int[] skipped, List<String> reasons) {
        Set<String> failedBuckets = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, SessionMetadata>> entry : byHarness.entrySet()) {
            String bucket = entry.getKey();
            long ordinal = 0;
            for (String sessionId : orderLegacySessions(entry.getValue(), legacyIds.get(bucket))) {
                ordinal++;
                SessionMetadata meta = entry.getValue().get(sessionId);
                try {
                    db.sessions().upsertSessionMeta(db.sessionDb(), bucket, sessionId,
                            meta != null ? meta : new SessionMetadata(null, null, false, null), ordinal);
                    migrated[0]++;
                } catch (SQLException e) {
                    skipped[0]++;
                    // The row is not in the database, so its preference entry must
                    // survive: deleting it would destroy the only copy.
                    failedBuckets.add(bucket);
                    addReason(reasons, bucket + "/" + sessionId + ": " + e.getMessage());
                }
            }
        }
        return failedBuckets;
    }

    /**
     * Ordered oldest first, matching ascending ordinals. The dropped id lists
     * stored 0 for the newest session, so ordering by descending position puts
     * the oldest first and the newest on the highest ordinal. Sessions the list
     * never mentioned sort to the newest end.
     */
    private static List<String> orderLegacySessions(Map<String, SessionMetadata> sessions,
                                                    Map<String, Long> legacyIds) {
        List<String> ordered = new ArrayList<>(sessions.keySet());
        ordered.sort(Comparator.comparingLong((String id) -> {
            Long position = legacyIds != null ? legacyIds.get(id) : null;
            return position != null ? position : -1L;
        }).reversed());
        return ordered;
    }

    /**
     * Removes the migrated and superseded preference data. A bucket is kept, in
     * both node families, when anything in it was not written: the metadata node
     * still holds what could not be recovered, and the id node still holds the
     * ordering that metadata node needs for a later attempt. A root key goes only
     * once nothing it still holds lacks a copy in the database.
     */
    private void deleteLegacyPreferences(Preferences root, List<String> metaNodes,
                                         List<String> idNodes, Set<String> bucketsToKeep,
                                         Set<String> historyKeys, Map<String, Set<String>> foldedKeys,
                                         Set<String> failedBuckets, List<String> reasons) {
        for (String nodeName : idNodes) {
            if (!bucketsToKeep.contains(bucketFromNodeName(nodeName, LEGACY_IDS_NODE))) {
                removeNode(root, nodeName, reasons);
            }
        }
        for (String nodeName : metaNodes) {
            if (!bucketsToKeep.contains(bucketFromNodeName(nodeName, LEGACY_META_NODE))) {
                removeNode(root, nodeName, reasons);
            }
        }
        try {
            List<String> keys = List.of(root.keys());
            boolean historyDropped = keys.stream()
                    .filter(PrefsMigration::isHistoryKey)
                    .allMatch(historyKeys::contains);
            for (String key : keys) {
                if (!isLegacyRootKey(key)
                        || retainLegacyRootKey(key, historyKeys, foldedKeys, failedBuckets, historyDropped)) {
                    continue;
                }
                root.remove(key);
            }
            for (String key : OBSOLETE_KEYS) {
                root.remove(key);
            }
            root.flush();
        } catch (BackingStoreException e) {
            addReason(reasons, "preference keys: could not be cleaned up: " + e.getMessage());
        }
    }

    private static boolean isHistoryKey(String key) {
        return key.startsWith(PreferenceKeys.INPUT_HISTORY_PREFIX)
                && !key.equals(PreferenceKeys.INPUT_HISTORY_COUNT);
    }

    /** Root keys superseded by the store. */
    private static boolean isLegacyRootKey(String key) {
        return isHistoryKey(key)
                || key.equals(PreferenceKeys.INPUT_HISTORY_COUNT)
                || key.startsWith(LEGACY_TITLE_PREFIX)
                || key.startsWith(LEGACY_HIDDEN_PREFIX)
                || key.startsWith(LEGACY_USAGE_PREFIX)
                || key.equals(LEGACY_LOCAL_SESSIONS_KEY)
                || key.startsWith(LEGACY_LOCAL_SESSIONS_KEY + "_");
    }

    /**
     * Keeps a root key only while it is the sole copy of something: a history entry
     * whose insert failed, or a per-session value folded into a bucket that did not
     * reach the database. Everything else is migrated or superseded and goes.
     */
    private static boolean retainLegacyRootKey(String key, Set<String> historyKeys,
                                               Map<String, Set<String>> foldedKeys,
                                               Set<String> failedBuckets, boolean historyDropped) {
        if (key.equals(PreferenceKeys.INPUT_HISTORY_COUNT)) {
            return !historyDropped;
        }
        if (isHistoryKey(key)) {
            return !historyKeys.contains(key);
        }
        Set<String> buckets = foldedKeys.get(key);
        return buckets != null && buckets.stream().anyMatch(failedBuckets::contains);
    }

    private static void removeNode(Preferences root, String nodeName, List<String> reasons) {
        try {
            root.node(nodeName).removeNode();
        } catch (BackingStoreException e) {
            addReason(reasons, nodeName + ": could not be removed: " + e.getMessage());
        }
    }

    /** Retains at most {@link #MAX_REPORT_REASONS} reasons, so a mass failure cannot flood memory. */
    private static void addReason(List<String> reasons, String reason) {
        if (reasons.size() < MAX_REPORT_REASONS) {
            reasons.add(reason);
        }
    }
    /** Minimal seam over the facade: executor gate, datasets, and log. */
    interface Db {
        boolean ensureDatabase();

        Connection connection() throws SQLException;

        SessionDatasets sessions();

        SessionDatasets.Db sessionDb();

        PinModelDatasets pins();

        PinModelDatasets.Db pinDb();
    }

}
