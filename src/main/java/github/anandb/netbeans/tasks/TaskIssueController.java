package github.anandb.netbeans.tasks;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.netbeans.modules.bugtracking.spi.IssueController;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

import org.netbeans.api.project.Project;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.model.TaskStatus;
import github.anandb.netbeans.ui.platform.PlatformBridge;

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

    /** NATO phonetic words, indexed by letter (A=Alpha ... Z=Zulu). */
    private static final String[] NATO = {
        "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel",
        "India", "Juliet", "Kilo", "Lima", "Mike", "November", "Oscar", "Papa",
        "Quebec", "Romeo", "Sierra", "Tango", "Uniform", "Victor", "Whiskey",
        "Xray", "Yankee", "Zulu"
    };

    private static final List<String> PRIORITIES = buildPriorities();

    private final TaskRepositoryControl store;
    private final TaskIssueProvider provider;
    private final TaskIssue issue;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private JTextField summaryField;
    private JComboBox<TaskStatus> statusBox;
    private JComboBox<String> priorityBox;
    private TagsEditor tagsEditor;
    private ChipEditor projectsEditor;
    private JTextField estimateField;
    private JTextField consumedField;
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
        String now = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString();
        String statusVal = status();
        if (provider.isNew(issue)) {
            String completedAt = TaskRecord.isFinishedStatus(statusVal) ? now : "";
            TaskRecord saved = new TaskRecord(
                cur.id(), statusVal, priority(), summary(),
                List.copyOf(tags()), List.copyOf(projects()), dueDate(),
                estimate(), consumed(), now, completedAt, now);
            if (store != null) {
                saved = store.addRecord(issue.getRepositoryId(), saved);
            }
            issue.setRecord(saved);
        } else {
            String completedAt = cur.completedAt();
            boolean wasFinished = cur.isFinished();
            boolean nowFinished = TaskRecord.isFinishedStatus(statusVal);
            if (nowFinished && !wasFinished) {
                completedAt = now;
            } else if (!nowFinished) {
                completedAt = "";
            }
            TaskRecord updated = new TaskRecord(
                cur.id(), statusVal, priority(), summary(),
                List.copyOf(tags()), List.copyOf(projects()), dueDate(),
                estimate(), consumed(), cur.createdAt(), completedAt, now);
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

    private String status() {
        TaskStatus s = (TaskStatus) (statusBox == null ? null : statusBox.getSelectedItem());
        return s == null ? "" : s.value();
    }

    private String priority() {
        if (priorityBox == null) {
            return "N";
        }
        return natoToLetter((String) priorityBox.getSelectedItem());
    }

    private Set<String> tags() {
        return tagsEditor == null ? Set.of() : tagsEditor.getSelected();
    }

    private Set<String> projects() {
        return projectsEditor == null ? Set.of() : projectsEditor.getSelected();
    }

    private int estimate() {
        return parsePositive(estimateField);
    }

    private int consumed() {
        return parsePositive(consumedField);
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
            String s = r.summary() == null ? "" : r.summary();
            if (s.isEmpty()) {
                summaryField.setForeground(Color.GRAY);
                summaryField.setText(SUMMARY_PLACEHOLDER);
            } else {
                summaryField.setForeground(Color.BLACK);
                summaryField.setText(s);
            }
        }
        if (statusBox != null) {
            statusBox.setSelectedItem(TaskStatus.fromValue(r.status()));
        }
        if (priorityBox != null) {
            priorityBox.setSelectedItem(letterToNato(r.priority()));
        }
        if (tagsEditor != null) {
            tagsEditor.setTags(String.join(", ", r.tags()));
        }
        if (projectsEditor != null) {
            projectsEditor.setSelected(Set.copyOf(r.projects()));
        }
        if (estimateField != null) {
            estimateField.setText(Integer.toString(r.estimate()));
        }
        if (consumedField != null) {
            consumedField.setText(Integer.toString(r.consumed()));
        }
        changed = false;
    }

    private static String resolvePriority(String stored) {
        if (stored == null) {
            return "N";
        }
        String p = stored.trim().toUpperCase(java.util.Locale.ROOT);
        return p.matches("[A-Z]") ? p : "N";
    }

    /** Maps a NATO word to its uppercase letter (default N). The letter is just
     *  the word's first character, so no lookup is needed. */
    private static String natoToLetter(String nato) {
        if (nato == null || nato.isBlank()) {
            return "N";
        }
        char c = Character.toUpperCase(nato.trim().charAt(0));
        return (c >= 'A' && c <= 'Z') ? String.valueOf(c) : "N";
    }

    /** Maps an uppercase letter to its NATO word (default November). */
    private static String letterToNato(String letter) {
        if (letter == null) {
            return NATO[13]; // N = November
        }
        String p = letter.trim().toUpperCase(java.util.Locale.ROOT);
        if (p.matches("[A-Z]")) {
            return NATO[p.charAt(0) - 'A'];
        }
        return NATO[13];
    }

    private static int parsePositive(JTextField f) {
        if (f == null) {
            return 0;
        }
        String v = f.getText().trim();
        if (v.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(v));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static final String SUMMARY_PLACEHOLDER = "Enter summary";

    private JComponent buildComponent() {
        summaryField = new JTextField(30);
        summaryField.setForeground(Color.GRAY);
        summaryField.setText(SUMMARY_PLACEHOLDER);
        summaryField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (summaryField.getText().equals(SUMMARY_PLACEHOLDER)) {
                    summaryField.setText("");
                    summaryField.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (summaryField.getText().isEmpty()) {
                    summaryField.setForeground(Color.GRAY);
                    summaryField.setText(SUMMARY_PLACEHOLDER);
                }
            }
        });
        statusBox = new JComboBox<>(TaskStatus.values());
        statusBox.setRenderer(new TaskStatusRenderer());
        priorityBox = new JComboBox<>(PRIORITIES.toArray(new String[0]));
        tagsEditor = new TagsEditor();
        tagsEditor.setAvailableTags(Set.of(
            "bug", "documentation", "duplicate",
            "enhancement", "feedback", "home", "invalid", "wontfix", "work"));
        projectsEditor = new ChipEditor("+", "Project", collectOpenProjectNames());
        estimateField = new JTextField(8);
        consumedField = new JTextField(8);

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
        estimateField.getDocument().addDocumentListener(dl);
        consumedField.getDocument().addDocumentListener(dl);
        priorityBox.addActionListener(ev -> markChanged());
        statusBox.addActionListener(ev -> markChanged());
        tagsEditor.addChangeListener(() -> markChanged());
        projectsEditor.addChangeListener(() -> markChanged());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.anchor = GridBagConstraints.WEST;

        // ── ChipEditor section ─────────────────────────────────────────
        // Summary, Tags, Projects: label in col 0, field spans cols 1–3
        // so the ChipEditor's internal dropdown sits at the right edge of
        // col 3 — aligned with Priority / Consumed below.
        addCell(panel, g, 0, 0, "Summary*:", summaryField, true, 3);
        addCell(panel, g, 1, 0, "Tags:", tagsEditor, true, 3);
        addCell(panel, g, 2, 0, "Projects:", projectsEditor, true, 3);

        // ── Separator ─────────────────────────────────────────────────
        g.gridy = 3;
        g.gridx = 0;
        g.gridwidth = 4;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(6, 4, 2, 4);
        panel.add(new JSeparator(), g);
        g.insets = new Insets(3, 4, 3, 4);
        g.gridwidth = 1;

        // ── Controls section ──────────────────────────────────────────
        // Status + Priority on one row; Estimate + Consumed on the next.
        // This sub-panel uses weightx=0 on all columns so controls keep
        // their natural width; fill=HORIZONTAL makes each pair match
        // within its column.
        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 4, 3, 4);
        gc.anchor = GridBagConstraints.WEST;
        addCell(controls, gc, 0, 0, "Status:", statusBox, true, 1);
        addCell(controls, gc, 0, 2, "Priority:", priorityBox, true, 1);
        addCell(controls, gc, 1, 0, "Estimate:", estimateField, true, 1);
        addCell(controls, gc, 1, 2, "Consumed:", consumedField, true, 1);

        g.gridy = 4;
        g.gridx = 0;
        g.gridwidth = 4;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;
        panel.add(controls, g);
        g.gridwidth = 1;

        // No in-form Save/Cancel buttons: the bugtracking framework supplies the
        // standard Save action (enabled when isChanged() reports a change) and
        // the close-time "Task has changes. Save?" prompt via IssueSavable.
        // Keeping to the framework's defaults avoids a competing Save path and
        // duplicate warn-on-close logic.

        // Wrap in a top-aligned BorderLayout so the form sits at the top of the
        // editor area and any extra vertical space falls below (not above) the
        // form, instead of the GridBagLayout panel being vertically centered.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    /**
     * Adds a labelled cell at (row y, column-pair xPair). xPair 0 occupies
     * grid columns 0–1, xPair 2 occupies 2–3. {@code span} sets the field's
     * gridwidth (1 = single column, 3 = spans cols 1–3 for full-width rows).
     * {@code fill} stretches the field horizontally; when false the control
     * keeps its preferred size.
     */
    private void addCell(JPanel panel, GridBagConstraints g, int y, int xPair,
            String label, JComponent field, boolean fill, int span) {
        g.gridy = y;
        g.gridx = xPair;
        g.gridwidth = 1;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), g);
        g.gridx = xPair + 1;
        g.gridwidth = span;
        g.weightx = fill ? 1 : 0;
        g.fill = fill ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;
        panel.add(field, g);
        g.gridwidth = 1; // reset for next call
    }

    private void markChanged() {
        if (!changed) {
            changed = true;
            pcs.firePropertyChange(PROP_CHANGED, false, true);
        }
    }

    /** Builds the priority list as NATO phonetic words (A=Alpha ... Z=Zulu). */
    private static List<String> buildPriorities() {
        return new ArrayList<>(java.util.Arrays.asList(NATO));
    }

    /** Collects open project names from the cached project list, spaces→hyphens. */
    private static Set<String> collectOpenProjectNames() {
        Set<String> names = new LinkedHashSet<>();
        PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
        if (bridge == null) {
            return names;
        }
        Project[] projects = bridge.projectContext().getAllOpenProjects();
        if (projects == null) {
            return names;
        }
        for (Project p : projects) {
            String name = TasksProject.displayName(p);
            if (name != null && !name.isEmpty()) {
                names.add(name.replace(" ", "-"));
            }
        }
        return names;
    }

    /** Renders a {@link TaskStatus} using its title-case display label. */
    private static final class TaskStatusRenderer extends JLabel
            implements ListCellRenderer<TaskStatus> {

        @Override
        public Component getListCellRendererComponent(JList<? extends TaskStatus> list,
                TaskStatus value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value == null ? "" : value.value());
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
