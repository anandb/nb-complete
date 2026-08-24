package github.anandb.netbeans.manager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;
import org.netbeans.api.annotations.common.NullAllowed;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.contract.TaskInput;
import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TaskTxtCodec;
import github.anandb.netbeans.support.TasksMetadata;

/**
 * todo.txt-backed store for all Beanbot Tasks repositories. Single
 * Lookup-registered instance implementing {@link TaskRepositoryControl}.
 *
 * <p>All calls mutate the in-memory cache synchronously and schedule the file
 * write on a background {@link RequestProcessor}; no file I/O happens on the
 * EDT. Before writing, the file is re-statted and, if its size or timestamp
 * changed since the last snapshot (external edit), the user is prompted on the
 * EDT whether to overwrite — on refusal the change is dropped and the cache is
 * re-synchronized from disk.</p>
 */
@ServiceProvider(service = TaskRepositoryControl.class)
public final class TxtTaskRepository implements TaskRepositoryControl {

    private static final Logger LOG = Logger.from(TxtTaskRepository.class);

    /**
     * File-modification snapshot keyed by canonical path. Shared across all
     * repository states that point at the same file, so multiple repositories
     * on one file don't treat each other's writes as external changes (which
     * would otherwise trigger false overwrite-conflicts).
     */
    private static final Map<String, long[]> FILE_STATS = new ConcurrentHashMap<>();

    private final RequestProcessor io = new RequestProcessor("BeanbotTasks-Txt", 1, true);

    private final List<RepositoryListener> listeners = new CopyOnWriteArrayList<>();
    private final List<RepoState> state = new CopyOnWriteArrayList<>();

    /** @since 1.17 */
    @Override
    public List<String> repositoryIds() {
        List<String> ids = new ArrayList<>();
        for (RepoState s : state) {
            ids.add(s.repoId);
        }
        return ids;
    }

    @Override
    public String csvPathOf(String repoId) {
        RepoState s = find(repoId);
        return s == null ? "" : s.csvPath;
    }

    @Override
    public String displayNameOf(String repoId) {
        RepoState s = find(repoId);
        return s == null ? "" : s.displayName;
    }

    @Override
    public void registerRepository(String repoId, String csvPath, String displayName) {
        if (repoId == null || repoId.isEmpty()) {
            return;
        }
        RepoState s = find(repoId);
        if (s == null) {
            s = new RepoState(repoId, csvPath, displayName);
            state.add(s);
        } else {
            s.csvPath = csvPath;
            s.displayName = displayName;
        }
        TasksMetadata.put(repoId, csvPath, displayName);
        loadAsync(s);
    }

    @Override
    public void unregisterRepository(String repoId) {
        RepoState s = find(repoId);
        if (s != null) {
            state.remove(s);
            TasksMetadata.remove(repoId);
        }
    }

    @Override
    public List<TaskRecord> list(String repoId) {
        RepoState s = find(repoId);
        if (s == null) {
            return List.of();
        }
        synchronized (s) {
            return new ArrayList<>(s.cache);
        }
    }

    @Override
    public TaskRecord get(String repoId, String id) {
        RepoState s = find(repoId);
        if (s == null) {
            return null;
        }
        synchronized (s) {
            for (TaskRecord t : s.cache) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
        }
        return null;
    }

    @Override
    public String createTaskId() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public TaskRecord add(String repoId, TaskInput input) {
        RepoState s = find(repoId);
        if (s == null) {
            return null;
        }
        String now = Instant.now().toString();
        TaskRecord task = new TaskRecord(
            createTaskId(),
            orEmpty(input == null ? null : input.status()),
            orEmpty(input == null ? null : input.priority()),
            orEmpty(input == null ? null : input.summary()),
            input == null ? List.of() : input.tagsList(),
            input == null ? List.of() : input.projectsList(),
            orEmpty(input == null ? null : input.dueDate()),
            input == null ? 0 : input.estimate(),
            input == null ? 0 : input.consumed(),
            now,
            now);
        synchronized (s) {
            s.cache.add(task);
            s.gen++;
        }
        fire(s.repoId, ChangeType.ADDED, task.id());
        schedulePersist(s);
        return task;
    }

    @Override
    public TaskRecord addRecord(String repoId, TaskRecord task) {
        if (task == null) {
            return null;
        }
        RepoState s = find(repoId);
        if (s == null) {
            return null;
        }
        synchronized (s) {
            if (s.cache.stream().noneMatch(t -> t.id().equals(task.id()))) {
                s.cache.add(task);
                s.gen++;
            } else {
                return get(repoId, task.id());
            }
        }
        fire(s.repoId, ChangeType.ADDED, task.id());
        schedulePersist(s);
        return task;
    }

    @Override
    public boolean update(String repoId, TaskRecord task) {
        if (task == null) {
            return false;
        }
        RepoState s = find(repoId);
        if (s == null) {
            return false;
        }
        boolean existed;
        synchronized (s) {
            existed = false;
            for (int i = 0; i < s.cache.size(); i++) {
                if (s.cache.get(i).id().equals(task.id())) {
                    TaskRecord updated = new TaskRecord(task.id(), task.status(), task.priority(),
                        task.summary(), task.tags(), task.projects(), task.dueDate(),
                        task.estimate(), task.consumed(), task.createdAt(), Instant.now().toString());
                    s.cache.set(i, updated);
                    s.gen++;
                    existed = true;
                    break;
                }
            }
        }
        if (existed) {
            fire(s.repoId, ChangeType.UPDATED, task.id());
            schedulePersist(s);
        }
        return existed;
    }

    @Override
    public boolean delete(String repoId, String id) {
        RepoState s = find(repoId);
        if (s == null) {
            return false;
        }
        boolean removed;
        synchronized (s) {
            removed = s.cache.removeIf(t -> t.id().equals(id));
            if (removed) {
                s.gen++;
            }
        }
        if (removed) {
            fire(s.repoId, ChangeType.REMOVED, id);
            schedulePersist(s);
        }
        return removed;
    }

    @Override
    public boolean reload(String repoId) {
        RepoState s = find(repoId);
        if (s == null) {
            return false;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            // Never perform file I/O on the EDT; read asynchronously instead.
            io.post(() -> loadSync(s));
            return true;
        }
        return loadSync(s);
    }

    @Override
    public void addListener(RepositoryListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(RepositoryListener listener) {
        listeners.remove(listener);
    }

    // ---- internals -------------------------------------------------------

    private RepoState find(String repoId) {
        for (RepoState s : state) {
            if (s.repoId.equals(repoId)) {
                return s;
            }
        }
        return null;
    }

    private void loadAsync(RepoState s) {
        io.post(() -> loadSync(s));
    }

    /** Loads the repository todo.txt from disk into the cache and updates the snapshot. */
    private boolean loadSync(RepoState s) {
        File f = new File(s.csvPath);
        List<TaskRecord> loaded = new ArrayList<>();
        synchronized (s) {
            s.genAtLoadStart = s.gen;
        }
        if (!f.exists()) {
            synchronized (s) {
                s.cache = loaded;
                s.lastModified = 0;
                s.size = 0;
            }
            markFileStat(s.csvPath, 0, 0);
            fire(s.repoId, ChangeType.RELOADED, null);
            return true;
        }
        // File (re)stat + read happen here, on the background processor.
        try {
            long lm = f.lastModified();
            long sz = f.length();
            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            loaded = new ArrayList<>(TaskTxtCodec.parse(content));
            synchronized (s) {
                // Do not clobber tasks mutated while this load was in flight.
                if (s.gen == s.genAtLoadStart) {
                    s.cache = loaded;
                    s.lastModified = lm;
                    s.size = sz;
                }
            }
            markFileStat(s.csvPath, lm, sz);
            LOG.fine("Loaded {0} tasks from {1}", loaded.size(), s.csvPath);
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Failed to load tasks file {0}: {1}", s.csvPath, ex.getMessage());
            return false;
        }
        fire(s.repoId, ChangeType.RELOADED, null);
        return true;
    }

    private void schedulePersist(RepoState s) {
        if (s.persistScheduled.compareAndSet(false, true)) {
            io.post(() -> {
                try {
                    persist(s);
                } finally {
                    s.persistScheduled.set(false);
                }
            });
        }
    }

    /**
     * Writes the cache to disk, guarding against silently overwriting a file
     * modified outside the IDE. Must run off the EDT.
     */
    private void persist(RepoState s) {
        File f = new File(s.csvPath);
        boolean conflict = false;
        if (f.exists()) {
            long lm = f.lastModified();
            long sz = f.length();
            conflict = !matchesSharedStat(s.csvPath, lm, sz);
        }
        if (conflict && !confirmOverwrite(f)) {
            // User declined — drop the optimistic change and resync from disk.
            LOG.warn("File {0} changed externally; discard local change", f.getPath());
            loadSync(s);
            DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                "The local change to " + f.getName() + " was discarded because the file was modified outside the IDE.",
                NotifyDescriptor.WARNING_MESSAGE));
            return;
        }
        try {
            String content;
            synchronized (s) {
                content = TaskTxtCodec.serialize(s.cache);
            }
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }
            Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
            synchronized (s) {
                s.lastModified = f.lastModified();
                s.size = f.length();
            }
            markFileStat(s.csvPath, f.lastModified(), f.length());
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Failed to write tasks file {0}: {1}", f.getPath(), ex.getMessage());
        }
    }

    /** Prompts (on the EDT, from the background thread) whether to overwrite. */
    private boolean confirmOverwrite(File f) {
        NotifyDescriptor.Confirmation d = new NotifyDescriptor.Confirmation(
            "The file " + f.getName() + " was modified outside the IDE.\nOverwrite it with the current changes?",
            "Beanbot Tasks — overwrite?",
            NotifyDescriptor.YES_NO_OPTION,
            NotifyDescriptor.WARNING_MESSAGE);
        Object result = DialogDisplayer.getDefault().notify(d);
        return NotifyDescriptor.YES_OPTION.equals(result);
    }

    private void fire(String repoId, ChangeType type, @NullAllowed String taskId) {
        for (RepositoryListener l : listeners) {
            try {
                l.repositoryChanged(repoId, type, taskId);
            } catch (RuntimeException ex) {
                LOG.warn("Repository listener error: {0}", ex.getMessage());
            }
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String fileKey(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException ex) {
            return path;
        }
    }

    /** Records the file's current (lastModified, size) as the shared snapshot. */
    private static void markFileStat(String path, long lastModified, long size) {
        long[] stat = FILE_STATS.computeIfAbsent(fileKey(path), k -> new long[2]);
        synchronized (stat) {
            stat[0] = lastModified;
            stat[1] = size;
        }
    }

    /** True if the file's stat matches the shared snapshot (i.e. not externally changed). */
    private static boolean matchesSharedStat(String path, long lastModified, long size) {
        long[] stat = FILE_STATS.get(fileKey(path));
        if (stat == null) {
            return false;
        }
        synchronized (stat) {
            return stat[0] == lastModified && stat[1] == size;
        }
    }

    /** Per-repository mutable state; guarded via synchronized(this). */
    private static final class RepoState {
        final String repoId;
        volatile String csvPath;
        volatile String displayName;
        List<TaskRecord> cache = new ArrayList<>();
        long lastModified;
        long size;
        long gen;
        long genAtLoadStart;
        final AtomicBoolean persistScheduled = new AtomicBoolean();

        RepoState(String repoId, String csvPath, String displayName) {
            this.repoId = repoId;
            this.csvPath = csvPath;
            this.displayName = displayName;
        }
    }
}
