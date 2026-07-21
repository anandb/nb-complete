package github.anandb.netbeans.manager;

import java.io.File;
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

    /** Files keyed by absolute path for O(1) dedup across overlapping projects. */
    private final ConcurrentHashMap<String, FileCacheQuery.CachedFile> files = new ConcurrentHashMap<>();
    private volatile boolean ready;
    private final List<Runnable> readyListeners = new CopyOnWriteArrayList<>();
    private final Object readyLock = new Object();

    // Per-sourceRoot listeners for incremental updates
    private final Map<File, FileChangeListener> rootListeners =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Strategy instances (one per root, cached)
    private final Map<File, VcsIgnoreStrategy> strategyCache =
            Collections.synchronizedMap(new HashMap<>());

    // Singleton via Lookup
    private static volatile FileCacheManager INSTANCE;

    public static FileCacheManager getDefault() {
        FileCacheQuery query = org.openide.util.Lookup.getDefault().lookup(FileCacheQuery.class);
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
        OpenProjects.getDefault().addPropertyChangeListener(evt -> {
            if (OpenProjects.PROPERTY_OPEN_PROJECTS.equals(evt.getPropertyName())) {
                RP.post(this::rebuild);
            }
        });
        // Initial build
        RP.post(this::rebuild);
    }

    // --- FileCacheQuery ---

    @Override
    public boolean isReady() {
        synchronized (readyLock) {
            return ready;
        }
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
        synchronized (readyLock) {
            ready = false;
        }
        files.clear();

        // Dispose old listeners
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
        strategyCache.clear();

        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        for (Project project : openProjects) {
            scanProject(project);
        }

        List<Runnable> listenersCopy;
        synchronized (readyLock) {
            ready = true;
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

    private void scanProject(Project project) {
        ProjectInformation info = project.getLookup().lookup(ProjectInformation.class);
        String projectName = info != null ? info.getDisplayName() : project.getProjectDirectory().getName();
        org.netbeans.api.project.SourceGroup[] groups =
                ProjectUtils.getSources(project).getSourceGroups(Sources.TYPE_GENERIC);
        for (org.netbeans.api.project.SourceGroup group : groups) {
            FileObject root = group.getRootFolder();

            File rootDir = FileUtil.toFile(root);
            if (rootDir == null || !rootDir.isDirectory()) continue;

            VcsIgnoreStrategy strategy = detectStrategy(rootDir);
            strategyCache.put(rootDir, strategy);

            // Bulk scan
            Set<String> nonIgnored = strategy.listNonIgnoredFiles(rootDir);
            if (nonIgnored.isEmpty()) {
                // NoOp or git returned empty — fall back to full filesystem walk
                walkFileSystem(root, projectName, rootDir);
            } else {
                // Add all non-ignored files from git
                for (String relPath : nonIgnored) {
                    FileObject child = root.getFileObject(relPath);
                    if (child != null && !child.isFolder()) {
                        File f = FileUtil.toFile(child);
                        if (f != null) {
                            files.put(f.getAbsolutePath(),
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
    private void walkFileSystem(FileObject folder, String projectName, File rootDir) {
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                // Skip hidden/VCS directories
                String name = child.getName();
                if (name.startsWith(".") || "node_modules".equals(name)
                        || "target".equals(name) || "build".equals(name)) {
                    continue;
                }
                walkFileSystem(child, projectName, rootDir);
            } else {
                File f = FileUtil.toFile(child);
                if (f == null) continue;
                String relPath = relativize(rootDir, f);
                if (relPath != null) {
                    files.put(f.getAbsolutePath(),
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
                    }
                }
            }

            @Override
            public void fileDeleted(FileEvent fe) {
                FileObject fo = fe.getFile();
                File f = FileUtil.toFile(fo);
                if (f != null) {
                    files.remove(f.getAbsolutePath());
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

    private VcsIgnoreStrategy detectStrategy(File rootDir) {
        return strategyCache.computeIfAbsent(rootDir, dir -> {
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
