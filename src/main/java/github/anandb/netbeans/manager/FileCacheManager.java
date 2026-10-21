package github.anandb.netbeans.manager;

import java.io.File;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import github.anandb.netbeans.support.Logger;

import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.Sources;
import org.openide.filesystems.FileChangeListener;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.contract.FileCacheQuery;
import github.anandb.netbeans.contract.VcsIgnoreStrategy;

import org.netbeans.api.project.SourceGroup;
import org.openide.util.Lookup;

/**
 * Builds and maintains an in-memory file cache across all open projects.
 * Uses {@link VcsIgnoreStrategy} for initial bulk filtering and filesystem
 * listeners for incremental updates.
 *
 * <p>Thread safety: writes are serialized on a single-threads RP; reads
 * use snapshot copies via {@code volatile} fields.</p>
 */
@ServiceProvider(service = FileCacheQuery.class)
public class FileCacheManager implements FileCacheQuery {

    private static final Logger LOG = Logger.from(FileCacheManager.class);
    private static final RequestProcessor RP = new RequestProcessor("GoToFile-CacheBuilder", 1);

    /** Coalescing window for rebuild triggers: a burst inside it rebuilds once. */
    static final int REBUILD_DELAY_MS = 1000;

    /** Sliding task: triggers reschedule this one task rather than posting new ones. */
    private final RequestProcessor.Task REBUILD_TASK = RP.create(this::rebuild);

    /** Files keyed by absolute path for O(1) dedup across overlapping projects. */
    private final ConcurrentHashMap<String, FileCacheQuery.CachedFile> files = new ConcurrentHashMap<>();
    private volatile boolean ready;
    private final AtomicLong cacheVersion = new AtomicLong(0);
    private final List<Runnable> readyListeners = new CopyOnWriteArrayList<>();
    private final Object readyLock = new Object();

    // Per-sourceRoot listeners for incremental updates
    private final Map<File, FileChangeListener> rootListeners =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Project open/close triggers a coalesced rebuild; removed by {@link #dispose()}. */
    private final PropertyChangeListener openProjectsListener = evt -> {
        if (OpenProjects.PROPERTY_OPEN_PROJECTS.equals(evt.getPropertyName())) {
            scheduleRebuild();
        }
    };

    // Strategy instances (one per root, cached)
    private final Map<File, VcsIgnoreStrategy> strategyCache =
            Collections.synchronizedMap(new HashMap<>());

    // Singleton via Lookup
    private static volatile FileCacheManager INSTANCE;

    public static FileCacheManager getDefault() {
        FileCacheQuery query = Lookup.getDefault().lookup(FileCacheQuery.class);
        if (query instanceof FileCacheManager manager) {
            return manager;
        }
        if (INSTANCE == null) {
            synchronized (FileCacheManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FileCacheManager();
                }
            }
        }
        return INSTANCE;
    }

    public FileCacheManager() {
        synchronized (FileCacheManager.class) {
            INSTANCE = this;
        }
        // Listen for project open/close to rebuild affected roots
        OpenProjects.getDefault().addPropertyChangeListener(openProjectsListener);

        // Initial build
        REBUILD_TASK.schedule(0);
    }

    /**
     * Reschedules the single rebuild task. Callers never post their own task, so a
     * burst of triggers (e.g. several projects opening at once) collapses into one
     * rebuild {@link #REBUILD_DELAY_MS} ms after the last trigger.
     */
    void scheduleRebuild() {
        REBUILD_TASK.schedule(REBUILD_DELAY_MS);
    }

    @Override
    public void dispose() {
        OpenProjects.getDefault().removePropertyChangeListener(openProjectsListener);
        REBUILD_TASK.cancel();
        disposeRootListeners();
        files.clear();
    }

    /** Detaches every recursive filesystem listener installed for a source root. */
    private void disposeRootListeners() {
        synchronized (rootListeners) {
            for (Map.Entry<File, FileChangeListener> e : rootListeners.entrySet()) {
                try {
                    FileUtil.removeRecursiveListener(e.getValue(), e.getKey());
                } catch (Exception ex) {
                    // already removed
                }
            }
            rootListeners.clear();
        }
    }

    // --- FileCacheQuery ---

    @Override
    public boolean isReady() {
        synchronized (readyLock) {
            return ready;
        }
    }

    @Override
    public long getCacheVersion() {
        return cacheVersion.get();
    }

    @Override
    public Collection<CachedFile> getAllFiles() {
        return Collections.unmodifiableCollection(new ArrayList<>(files.values()));
    }

    /** Registers a listener that fires once when the cache first becomes ready. */
    @Override
    public void onReady(Runnable action) {
        boolean shouldRun = false;
        synchronized (readyLock) {
            if (ready) {
                shouldRun = true;
            } else {
                readyListeners.add(action);
            }
        }
        if (shouldRun) {
            action.run();
        }
    }

    // --- Cache lifecycle ---

    private void rebuild() {
        long start = System.currentTimeMillis();

        // Dispose old listeners
        disposeRootListeners();
        strategyCache.clear();

        // Scan into a staging map and publish it at the end: clearing the live map
        // would empty GoTo File for the whole scan.
        Map<String, FileCacheQuery.CachedFile> scanned = new ConcurrentHashMap<>();
        Set<String> knownBeforeScan = Set.copyOf(files.keySet());
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        for (Project project : openProjects) {
            scanProject(project, scanned);
        }
        publish(scanned, knownBeforeScan);

        List<Runnable> listenersCopy;
        synchronized (readyLock) {
            ready = true;
            cacheVersion.incrementAndGet();
            listenersCopy = new ArrayList<>(readyListeners);
            readyListeners.clear();
        }

        long elapsed = System.currentTimeMillis() - start;
        LOG.log(Level.FINE, "GoToFile cache built: {0} files in {1}ms",
                new Object[]{files.size(), elapsed});

        for (Runnable r : listenersCopy) {
            try {
                r.run();
            } catch (Exception e) {
                LOG.log(Level.FINE, "Ready listener failed", e);
            }
        }
    }

    /**
     * Replaces the live contents with the scanned snapshot without ever exposing an
     * empty map. An entry the previous scan knew and this one does not is dropped
     * (deleted, or newly ignored); an entry that appeared only while the scan ran
     * is kept as long as its file still exists, so a file created mid-scan is not
     * lost.
     *
     * @param scanned        what the scan found
     * @param knownBeforeScan cache keys as they were when the scan started
     */
    void publish(Map<String, FileCacheQuery.CachedFile> scanned, Set<String> knownBeforeScan) {
        files.putAll(scanned);
        files.keySet().removeIf(path -> {
            if (scanned.containsKey(path)) {
                return false;
            }
            if (knownBeforeScan.contains(path)) {
                return true;
            }
            FileCacheQuery.CachedFile added = files.get(path);
            return added == null || !added.fileObject().isValid();
        });
    }

    private void scanProject(Project project, Map<String, FileCacheQuery.CachedFile> target) {
        ProjectInformation info = project.getLookup().lookup(ProjectInformation.class);
        String projectName = info != null ? info.getDisplayName() : project.getProjectDirectory().getName();
        SourceGroup[] groups =
                ProjectUtils.getSources(project).getSourceGroups(Sources.TYPE_GENERIC);
        for (SourceGroup group : groups) {
            FileObject root = group.getRootFolder();

            File rootDir = FileUtil.toFile(root);
            if (rootDir == null || !rootDir.isDirectory()) continue;

            VcsIgnoreStrategy strategy = detectStrategy(rootDir);
            strategyCache.put(rootDir, strategy);

            // Bulk scan
            Set<String> nonIgnored = strategy.listNonIgnoredFiles(rootDir);
            if (nonIgnored.isEmpty() && !(strategy instanceof BbIgnoreStrategy)) {
                // NoOp or git returned empty — fall back to full filesystem walk.
                // An empty .bbignore result is authoritative: the policy excluded
                // everything, so walking would resurrect the ignored files.
                walkFileSystem(root, projectName, rootDir, target);
            } else {
                // Add all non-ignored files from git
                for (String relPath : nonIgnored) {
                    FileObject child = root.getFileObject(relPath);
                    if (child != null && !child.isFolder()) {
                        File f = FileUtil.toFile(child);
                        if (f != null) {
                            target.put(f.getAbsolutePath(),
                                    new FileCacheQuery.CachedFile(child, projectName, relPath));
                        }
                    }
                }
            }

            // Install incremental listener
            installListener(root, rootDir, projectName, strategy);
        }
    }

    /** Recursively walk the source root for projects without VCS. */
    private void walkFileSystem(FileObject folder, String projectName, File rootDir,
            Map<String, FileCacheQuery.CachedFile> target) {
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                // Skip hidden/VCS directories
                String name = child.getName();
                if (name.startsWith(".") || "node_modules".equals(name)
                        || "target".equals(name) || "build".equals(name)) {
                    continue;
                }
                walkFileSystem(child, projectName, rootDir, target);
            } else {
                File f = FileUtil.toFile(child);
                if (f == null) continue;
                String relPath = relativize(rootDir, f);
                if (relPath != null) {
                    target.put(f.getAbsolutePath(),
                            new FileCacheQuery.CachedFile(child, projectName, relPath));
                }
            }
        }
    }

    private void installListener(FileObject root, File rootDir,
            String projectName, VcsIgnoreStrategy strategy) {
        FileChangeListener listener = new FileChangeListener() {
            @Override
            public void fileDataCreated(FileEvent fe) {
                FileObject fo = fe.getFile();
                File f = FileUtil.toFile(fo);
                if (f == null) return;
                if (!strategy.isIgnored(rootDir, f)) {
                    String rel = relativize(rootDir, f);
                    if (rel != null) {
                        files.put(f.getAbsolutePath(),
                                new FileCacheQuery.CachedFile(fo, projectName, rel));
                        cacheVersion.incrementAndGet();
                    }
                }
            }

            @Override
            public void fileDeleted(FileEvent fe) {
                FileObject fo = fe.getFile();
                File f = FileUtil.toFile(fo);
                if (f != null) {
                    if (files.remove(f.getAbsolutePath()) != null) {
                        cacheVersion.incrementAndGet();
                    }
                }
            }

            @Override
            public void fileRenamed(FileRenameEvent fe) {
                FileObject fo = fe.getFile();
                // Remove stale entries whose FileObject is no longer valid after rename
                files.values().removeIf(cf -> !cf.fileObject().isValid());
                // Add new path
                File f = FileUtil.toFile(fo);
                if (f != null && !strategy.isIgnored(rootDir, f)) {
                    String rel = relativize(rootDir, f);
                    if (rel != null) {
                        files.put(f.getAbsolutePath(),
                                new FileCacheQuery.CachedFile(fo, projectName, rel));
                        cacheVersion.incrementAndGet();
                    }
                }
            }

            @Override public void fileFolderCreated(FileEvent fe) { /* no-op */ }
            @Override public void fileChanged(FileEvent fe) { /* no-op — content changes don't affect listing */ }
            @Override public void fileAttributeChanged(FileAttributeEvent fe) { /* no-op */ }
        };

        FileUtil.addRecursiveListener(listener, rootDir);
        rootListeners.put(rootDir, listener);
    }

    // --- Strategy detection ---

    VcsIgnoreStrategy detectStrategy(File rootDir) {
        return strategyCache.computeIfAbsent(rootDir, dir -> {
            // A .bbignore is display policy: when present it replaces the VCS
            // ignore file entirely rather than layering on top of it.
            if (new BbIgnoreStrategy().isAvailable(dir)) {
                return new BbIgnoreStrategy();
            }
            if (new GitIgnoreStrategy().isAvailable(dir)) {
                return new GitIgnoreStrategy();
            }
            if (new HgIgnoreStrategy().isAvailable(dir)) {
                return new HgIgnoreStrategy();
            }
            return new NoVcsIgnoreStrategy();
        });
    }

    // --- Utilities ---

    private static String relativize(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(rootPath)) return null;
        String rel = filePath.substring(rootPath.length());
        if (rel.startsWith(File.separator)) {
            rel = rel.substring(1);
        }
        return rel.replace(File.separatorChar, '/');
    }
}
