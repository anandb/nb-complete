package github.anandb.netbeans.tasks;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.netbeans.modules.bugtracking.spi.IssueController;
import org.netbeans.modules.bugtracking.spi.IssueProvider;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import org.openide.util.Lookup;

/**
 * {@link IssueProvider} for Beanbot Tasks. Task records live in a local
 * todo.txt repository (no remote), so submit/comment/attach operations are no-ops. A
 * single provider instance is shared across repositories and dispatches via
 * {@link TaskIssue#getRepositoryId()}.
 */
public final class TaskIssueProvider implements IssueProvider<TaskIssue> {

    /**
     * One controller per issue. The Dashboard editor calls
     * {@code IssueImpl.getController()} repeatedly (to obtain the component,
     * register the {@link IssueController#PROP_CHANGED} listener, and call
     * {@code isChanged()}/{@code saveChanges()}); a fresh controller per call
     * would neither report typed edits nor enable the editor's Save button.
     */
    private final Map<TaskIssue, TaskIssueController> controllers =
        new ConcurrentHashMap<>();

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private volatile boolean watchingStore;

    private TaskRepositoryControl store() {
        return Lookup.getDefault().lookup(TaskRepositoryControl.class);
    }

    @Override
    public String getDisplayName(TaskIssue i) {
        TaskRecord r = i.getRecord();
        return r == null ? "" : (blank(r.summary()) ? r.id() : r.summary());
    }

    @Override
    public String getTooltip(TaskIssue i) {
        TaskRecord r = i.getRecord();
        return r == null ? "" : blank(r.summary()) ? "" : r.summary();
    }

    @Override
    public String getID(TaskIssue i) {
        return i.getRecord() == null ? null : i.getRecord().id();
    }

    @Override
    public Collection<String> getSubtasks(TaskIssue i) {
        // Subtasks are no longer modeled; the Beanbot Tasks format has no
        // subtask concept, so this is always empty.
        return List.of();
    }

    @Override
    public String getSummary(TaskIssue i) {
        return i.getRecord() == null ? "" : i.getRecord().summary();
    }

    @Override
    public boolean isNew(TaskIssue i) {
        TaskRepositoryControl s = store();
        if (i.getRecord() == null) {
            return false;
        }
        return s == null || s.get(i.getRepositoryId(), i.getRecord().id()) == null;
    }

    @Override
    public boolean isFinished(TaskIssue i) {
        return i.getRecord() != null && i.getRecord().isFinished();
    }

    @Override
    public boolean refresh(TaskIssue i) {
        TaskRepositoryControl s = store();
        return s != null && s.reload(i.getRepositoryId());
    }

    @Override
    public void addComment(TaskIssue i, String comment, boolean close) {
        // no remote tracker; nothing to submit
    }

    @Override
    public void attachFile(TaskIssue i, java.io.File file, String description, boolean isPatch) {
        // attachments not supported (canAttachFiles == false)
    }

    @Override
    public IssueController getController(TaskIssue i) {
        return controllers.computeIfAbsent(i, key -> new TaskIssueController(store(), this, key));
    }

    @Override
    public void removePropertyChangeListener(TaskIssue i, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public void addPropertyChangeListener(TaskIssue i, PropertyChangeListener listener) {
        watchStore();
        pcs.addPropertyChangeListener(listener);
    }

    /** Informs the Dashboard that a task's data changed or a task was removed. */
    public void notifyDataChanged(String taskId, boolean deleted) {
        if (deleted) {
            pcs.firePropertyChange(EVENT_ISSUE_DELETED, null, taskId);
        } else {
            pcs.firePropertyChange(EVENT_ISSUE_DATA_CHANGED, null, taskId);
        }
    }

    private void watchStore() {
        if (watchingStore) {
            return;
        }
        synchronized (this) {
            if (watchingStore) {
                return;
            }
            watchingStore = true;
            TaskRepositoryControl s = store();
            if (s != null) {
                s.addListener((repoId, type, taskId) -> {
                    if (type == TaskRepositoryControl.ChangeType.UPDATED) {
                        pcs.firePropertyChange(EVENT_ISSUE_DATA_CHANGED, null, taskId);
                    } else if (type == TaskRepositoryControl.ChangeType.REMOVED) {
                        TaskIssueCache.invalidate(repoId, taskId);
                        pcs.firePropertyChange(EVENT_ISSUE_DELETED, null, taskId);
                    }
                });
            }
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}