package github.anandb.netbeans.tasks;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.netbeans.modules.bugtracking.spi.IssueController;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.model.TaskStatus;

/**
 * Edit panel for a single task, used both for editing existing tasks and for
 * the "New Task" (create) flow. Saves optimistically through the store (which
 * persists off the EDT) and reports the change so the Dashboard refreshes.
 *
 * <p>Due dates are intentionally <em>not</em> edited here: they are managed via
 * the Dashboard's native Schedule submenu, which registers the task into the
 * framework's scheduling manager and populates the Schedule categories. This
 * form preserves any existing due date rather than exposing one.</p>
 */
public final class TaskIssueController implements IssueController {

    private static final String[] PRIORITIES = {"normal", "high", "low"};

    private final TaskRepositoryControl store;
    private final TaskIssueProvider provider;
    private final TaskIssue issue;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private JTextField summaryField;
    private JComboBox<TaskStatus> statusBox;
    private JComboBox<String> priorityBox;
    private TagsEditor tagsEditor;
    private JComponent component;
    private boolean changed;

    public TaskIssueController(TaskRepositoryControl store, TaskIssueProvider provider, TaskIssue issue) {
        this.store = store;
        this.provider = provider;
        this.issue = issue;
    }

    @Override
    public JComponent getComponent() {
        if (component == null) {
            component = buildComponent();
            populate();
        }
        return component;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void opened() {
    }

    @Override
    public void closed() {
    }

    @Override
    public boolean saveChanges() {
        TaskRecord cur = issue.getRecord();
        if (cur == null) {
            return false;
        }
        if (summary().isEmpty()) {
            // Summary is the only mandatory field; refuse to save a titleless task.
            DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                "A task summary is required.", NotifyDescriptor.WARNING_MESSAGE));
            return false;
        }
        String now = Instant.now().toString();
        if (provider.isNew(issue)) {
            TaskRecord saved = new TaskRecord(
                cur.id(), status(), priority(), summary(), description(), cur.filePath(),
                tags(), dueDate(), List.of(), now, now);
            if (store != null) {
                saved = store.addRecord(issue.getRepositoryId(), saved);
            }
            issue.setRecord(saved);
        } else {
            TaskRecord updated = new TaskRecord(
                cur.id(), status(), priority(), summary(), description(), cur.filePath(),
                tags(), dueDate(), cur.subtasks(), cur.createdAt(), now);
            if (store != null) {
                store.update(issue.getRepositoryId(), updated);
            }
            issue.setRecord(updated);
        }
        changed = false;
        pcs.firePropertyChange(PROP_CHANGED, true, false);
        if (provider != null) {
            provider.notifyDataChanged(issue.getRecord().id(), false);
        }
        return true;
    }

    @Override
    public boolean discardUnsavedChanges() {
        populate();
        changed = false;
        pcs.firePropertyChange(PROP_CHANGED, true, false);
        return true;
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    // ---- field accessors -------------------------------------------------

    private String summary() {
        return summaryField == null ? "" : summaryField.getText().trim();
    }

    // Description is no longer editable in this form (the field was removed).
    // Preserve the record's existing value rather than blanking it on save.
    private String description() {
        TaskRecord r = issue.getRecord();
        return r == null ? "" : (r.description() == null ? "" : r.description());
    }

    private String status() {
        TaskStatus s = (TaskStatus) (statusBox == null ? null : statusBox.getSelectedItem());
        return s == null ? "" : s.value();
    }

    private String priority() {
        return priorityBox == null ? "normal" : (String) priorityBox.getSelectedItem();
    }

    private String tags() {
        return tagsEditor == null ? "" : tagsEditor.getTags();
    }

    private String dueDate() {
        // Due dates are managed via the Dashboard's native Schedule submenu
        // (which registers the task into the framework's scheduling manager).
        // This editor intentionally does not edit them, so preserve the record's
        // current value rather than clearing it on save.
        TaskRecord r = issue.getRecord();
        return r == null ? "" : (r.dueDate() == null ? "" : r.dueDate());
    }

    private void populate() {
        TaskRecord r = issue.getRecord();
        if (r == null) {
            return;
        }
        if (summaryField != null) {
            summaryField.setText(r.summary());
        }
        if (statusBox != null) {
            statusBox.setSelectedItem(TaskStatus.fromValue(r.status()));
        }
        if (priorityBox != null) {
            priorityBox.setSelectedItem(resolvePriority(r.priority()));
        }
        if (tagsEditor != null) {
            tagsEditor.setTags(r.tags());
        }
        changed = false;
    }

    private static String resolvePriority(String stored) {
        if (stored == null) {
            return "normal";
        }
        for (String p : PRIORITIES) {
            if (p.equalsIgnoreCase(stored.trim())) {
                return p;
            }
        }
        return "normal";
    }

    private JComponent buildComponent() {
        summaryField = new JTextField(30);
        statusBox = new JComboBox<>(TaskStatus.values());
        statusBox.setRenderer(new TaskStatusRenderer());
        priorityBox = new JComboBox<>(PRIORITIES);
        tagsEditor = new TagsEditor();
        tagsEditor.setAvailableTags(Set.of(
            "bug", "documentation", "help wanted", "dependencies", "duplicate",
            "enhancement", "feedback", "invalid", "wontfix"));

        DocumentListener dl = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markChanged();
            }
        };
        summaryField.getDocument().addDocumentListener(dl);
        priorityBox.addActionListener(ev -> markChanged());
        statusBox.addActionListener(ev -> markChanged());
        tagsEditor.addChangeListener(() -> markChanged());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.anchor = GridBagConstraints.NORTHWEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0;

        addRow(panel, g, 0, "Summary*:", summaryField);
        addRow(panel, g, 1, "Status:", statusBox);
        addRow(panel, g, 2, "Priority:", priorityBox);
        addRow(panel, g, 3, "Tags:", tagsEditor);

        // No in-form Save/Cancel buttons: the bugtracking framework supplies the
        // standard Save action (enabled when isChanged() reports a change) and
        // the close-time "Task has changes. Save?" prompt via IssueSavable.
        // Keeping to the framework's defaults avoids a competing Save path and
        // duplicate warn-on-close logic.

        // Wrap in a top-aligned BorderLayout so the form sits at the top of the
        // editor area and any extra vertical space falls below (not above) the
        // form, instead of the GridBagLayout panel being vertically centered.
        JPanel wrapper = new JPanel(new java.awt.BorderLayout());
        wrapper.add(panel, java.awt.BorderLayout.NORTH);
        return wrapper;
    }

    private void addRow(JPanel panel, GridBagConstraints g, int y, String label, JComponent field) {
        g.gridy = y;
        g.gridx = 0;
        g.weightx = 0;
        panel.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 1;
        panel.add(field, g);
    }

    private void markChanged() {
        if (!changed) {
            changed = true;
            pcs.firePropertyChange(PROP_CHANGED, false, true);
        }
    }

    /** Renders a {@link TaskStatus} using its lowercase display label. */
    private static final class TaskStatusRenderer extends JLabel
            implements ListCellRenderer<TaskStatus> {

        @Override
        public Component getListCellRendererComponent(JList<? extends TaskStatus> list,
                TaskStatus value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value == null ? "" : value.display());
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
                setOpaque(true);
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                setOpaque(false);
            }
            return this;
        }
    }
}