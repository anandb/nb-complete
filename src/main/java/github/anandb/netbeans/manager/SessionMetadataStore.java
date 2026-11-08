package github.anandb.netbeans.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.netbeans.api.project.Project;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.contract.SessionStore;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionMetadata;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ProjectPaths;

import static org.apache.commons.text.StringEscapeUtils.unescapeHtml4;

/**
 * Persists and reads per-session metadata (title, hidden flag, context usage, cwd).
 * Extracted from SessionManager so persistence and its lock are no longer mixed with
 * session orchestration.
 */
final class SessionMetadataStore {

    private static final Logger LOG = Logger.from(SessionMetadataStore.class);

    /** Metadata for a session that has none yet. */
    private static final SessionMetadata EMPTY_METADATA = new SessionMetadata(null, null, false, null);

    private final Supplier<String> harnessId;

    /**
     * The local store. Resolved from the service registry on first use and then
     * kept, so the per-session metadata reads that the UI makes in a loop do not
     * each pay for a lookup. Never cached when absent, so a store registered
     * later is still picked up.
     */
    private volatile SessionStore sessionStore;

    SessionMetadataStore(Supplier<String> harnessId) {
        this.harnessId = harnessId;
    }

    /** The local store holding the per-session metadata, or null when none is registered. */
    private SessionStore store() {
        SessionStore local = sessionStore;
        if (local != null) {
            return local;
        }
        local = Lookup.getDefault().lookup(SessionStore.class);
        if (local != null) {
            sessionStore = local;
        }
        return local;
    }

    /**
     * Overrides the store, bypassing the service registry. Tests use this so a
     * unit test never opens the real database under the user directory.
     */
    void setStoreForTest(SessionStore store) {
        this.sessionStore = store;
    }

    /** Metadata for one session, or {@link #EMPTY_METADATA} when it has none. */
    private SessionMetadata metadataOf(String sessionId) {
        if (sessionId == null) {
            return EMPTY_METADATA;
        }
        SessionStore store = store();
        if (store == null) {
            return EMPTY_METADATA;
        }
        String harness = harnessId.get();
        SessionMetadata meta = store.sessionMetadata(harness).get(sessionId);
        if (meta == null && harness != null) {
            // Metadata written before a harness was configured carries no harness
            // id. The preference-backed reader fell back to the unqualified node,
            // so keep those titles, hidden flags and usage visible.
            meta = store.sessionMetadata(null).get(sessionId);
        }
        return meta != null ? meta : EMPTY_METADATA;
    }

    /**
     * Applies {@code updater} to a session's metadata and saves the result. The
     * read-modify-write is not atomic against other writers, which is acceptable:
     * every caller runs on the EDT or an RPC thread and changes a single field,
     * exactly as the preference-backed version this replaced did.
     */
    synchronized void update(String sessionId, Function<SessionMetadata, SessionMetadata> updater) {
        if (sessionId == null || sessionId.isEmpty() || updater == null) {
            return;
        }
        SessionStore store = store();
        if (store == null) {
            return;
        }
        store.saveSessionMetadata(harnessId.get(), sessionId, updater.apply(metadataOf(sessionId)));
    }

    /** Local title for a session, HTML-decoded, or {@code defaultTitle}. */
    String customTitle(String sessionId, String defaultTitle) {
        if (sessionId == null) return defaultTitle;
        String val = metadataOf(sessionId).title();
        return decodeHtmlEntities(val != null ? val : defaultTitle);
    }

    private static String decodeHtmlEntities(String input) {
        if (input == null) return null;
        return unescapeHtml4(input);
    }

    void setCustomTitle(String sessionId, String title) {
        update(sessionId, m -> new SessionMetadata(title, m.usage(), m.hidden(), m.cwd()));
    }

    boolean hidden(String sessionId) {
        return metadataOf(sessionId).hidden();
    }

    void setHidden(String sessionId, boolean hidden) {
        update(sessionId, m -> new SessionMetadata(m.title(), m.usage(), hidden, m.cwd()));
    }

    /**
     * Bulk form of {@link #setHidden}: one lock acquisition and one store call for
     * the whole set, so archiving N sessions costs one write instead of N.
     */
    synchronized void setHiddenBulk(Collection<String> sessionIds, boolean hidden) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        SessionStore store = store();
        if (store == null) {
            return;
        }
        Map<String, SessionMetadata> updates = new LinkedHashMap<>();
        for (String sessionId : sessionIds) {
            if (sessionId == null || sessionId.isEmpty()) {
                continue;
            }
            SessionMetadata m = metadataOf(sessionId);
            updates.put(sessionId, new SessionMetadata(m.title(), m.usage(), hidden, m.cwd()));
        }
        store.saveSessionMetadataBulk(harnessId.get(), updates);
    }

    String usage(String sessionId) {
        return metadataOf(sessionId).usage();
    }

    void setUsage(String sessionId, long used, long size) {
        update(sessionId, m -> new SessionMetadata(m.title(), used + "," + size, m.hidden(), m.cwd()));
    }

    /**
     * Restores the locally-created sessions into the cache manager, newest first.
     * Only meaningful for a harness without {@code session/list}: every session it
     * has is one created locally.
     */
    void loadLocallyCreatedSessions(SessionCacheManager cacheManager) {
        String agent = harnessId.get();
        SessionStore store = store();
        if (store == null) {
            return;
        }
        List<String> ids = new ArrayList<>(store.sessionMetadata(agent).keySet());
        if (ids.isEmpty()) {
            return;
        }
        // Data repair: sessions saved before the cwd field existed carry no project
        // directory. With exactly one project open they can be attributed safely —
        // backfill and persist the repair once. With several projects open the
        // ambiguity is left to the load-time fallback instead of guessing.
        ProjectQuery projectQuery = Lookup.getDefault().lookup(ProjectQuery.class);
        Project[] openProjects = projectQuery == null
                ? new Project[0] : projectQuery.getAllOpenProjects();
        String repairCwd = openProjects.length == 1 && openProjects[0] != null
                ? ProjectPaths.nativePath(openProjects[0]) : null;
        // Load in reverse order since cacheManager.addLocallyCreated prepends
        for (int i = ids.size() - 1; i >= 0; i--) {
            String sessionId = ids.get(i);
            String title = customTitle(sessionId, sessionId);
            String cwd = metadataOf(sessionId).cwd();
            if (cwd == null && repairCwd != null) {
                LOG.info("Repairing missing cwd for session {0}: {1}", sessionId, repairCwd);
                update(sessionId, m -> new SessionMetadata(
                        m.title(), m.usage(), m.hidden(), m.cwd() != null ? m.cwd() : repairCwd));
                cwd = repairCwd;
            }
            Session s = new Session(sessionId, title, cwd, cwd, null, null, null, null, null, null);
            cacheManager.addLocallyCreated(s, agent);
        }
    }
}
