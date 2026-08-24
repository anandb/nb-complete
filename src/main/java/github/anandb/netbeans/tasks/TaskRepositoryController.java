package github.anandb.netbeans.tasks;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.EventListenerList;

import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.bugtracking.spi.RepositoryController;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.HelpCtx;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.support.TasksMetadata;
import github.anandb.netbeans.tasks.TasksModel.TaskRepository;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProjectContext;
import org.openide.util.Lookup;

/**
 * Create/edit-repository panel: user provides the display name and the external
 * tasks-file (todo.txt) path (prompted via a file chooser). On
 * {@link #applyChanges()} the values are written to the {@link TaskRepository},
 * an id is assigned on first save, and the backing store registers the
 * repository.
 */
public final class TaskRepositoryController implements RepositoryController {

    private final TaskRepository repository;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final EventListenerList changeListeners = new EventListenerList();
    private final ChangeEvent CHANGED = new ChangeEvent(this);

    private JTextField nameField;
    private JTextField pathField;
    private JComponent component;

    TaskRepositoryController(TaskRepository repository) {
        this.repository = repository;
    }

    /** Ensures the edit fields have been built before reading them. */
    private void ensureBuilt() {
        getComponent();
    }

    @Override
    public JComponent getComponent() {
        if (component == null) {
            component = buildComponent();
        }
        return component;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
        ensureBuilt();
        return !nameField.getText().trim().isEmpty()
            && !pathField.getText().trim().isEmpty()
            && !duplicatePath();
    }

    /** True if another repository already uses the chosen CSV path. */
    private boolean duplicatePath() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            return false;
        }
        String canonical = canonical(path);
        String myId = repository.getRepositoryId();

        // Live store.
        TaskRepositoryControl s = Lookup.getDefault().lookup(TaskRepositoryControl.class);
        if (s != null) {
            for (String id : s.repositoryIds()) {
                if (id.equals(myId)) {
                    continue; // editing this repository's own current path
                }
                if (canonical.equals(canonical(s.csvPathOf(id)))) {
                    return true;
                }
            }
        }
        // Persisted metadata (covers the case where the framework hasn't yet
        // restored the store at startup, so the live list is momentarily empty).
        for (String id : TasksMetadata.allIds()) {
            if (id.equals(myId)) {
                continue;
            }
            if (canonical.equals(canonical(TasksMetadata.tasksPathOf(id)))) {
                return true;
            }
        }
        return false;
    }

    private static String canonical(String p) {
        try {
            return new java.io.File(p).getCanonicalPath();
        } catch (java.io.IOException ex) {
            return p;
        }
    }

    @Override
    public void populate() {
        ensureBuilt();
        nameField.setText(repository.getDisplayName() == null ? "" : repository.getDisplayName());
        pathField.setText(repository.getCsvPath() == null ? "" : repository.getCsvPath());
        applyDefaultsIfNew();
    }

    /**
     * For a brand-new repository, pre-fill the display name with the current
     * project's name and the path with {@code beanbot_tasks.txt} in its root.
     * The user may override either value.
     */
    private void applyDefaultsIfNew() {
        if (repository.getRepositoryId() != null) {
            return; // editing an existing repository — don't touch its values
        }
        Project current = currentProject();
        if (current == null) {
            return;
        }
        if (nameField.getText().trim().isEmpty()) {
            String name = projectName(current);
            if (name != null && !name.isEmpty()) {
                nameField.setText(name);
            }
        }
        if (pathField.getText().trim().isEmpty()) {
            File root = FileUtil.toFile(current.getProjectDirectory());
            if (root != null) {
                pathField.setText(new File(root, "beanbot_tasks.txt").getAbsolutePath());
            }
        }
    }

    private Project currentProject() {
        // Mirror the IDE's Run/Debug behavior: follow the project of the
        // currently-focused editor file, then fall back to the main project,
        // then the first open project.
        Project owner = projectOfActiveEditor();
        if (owner != null) {
            return owner;
        }
        Project main = OpenProjects.getDefault().getMainProject();
        if (main != null) {
            return main;
        }
        PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
        ProjectContext ctx = bridge == null ? null : bridge.projectContext();
        if (ctx == null) {
            return null;
        }
        Project[] projects = ctx.getAllOpenProjects();
        return (projects == null || projects.length == 0) ? null : projects[0];
    }

    /** The project that owns the file in the currently focused editor, or null. */
    private static Project projectOfActiveEditor() {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            return null;
        }
        Document doc = editor.getDocument();
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        return fo == null ? null : FileOwnerQuery.getOwner(fo);
    }

    private static String projectName(Project project) {
        ProjectInformation info = project.getLookup().lookup(ProjectInformation.class);
        if (info != null && info.getDisplayName() != null && !info.getDisplayName().isEmpty()) {
            return info.getDisplayName();
        }
        return project.getProjectDirectory().getName();
    }

    @Override
    public String getErrorMessage() {
        ensureBuilt();
        if (nameField.getText().trim().isEmpty()) {
            return "A display name is required.";
        }
        if (pathField.getText().trim().isEmpty()) {
            return "A tasks file path is required.";
        }
        if (duplicatePath()) {
            return "Another repository already uses this tasks file.";
        }
        return null;
    }

    @Override
    public void applyChanges() {
        ensureBuilt();
        if (!isValid()) {
            return;
        }
        String id = repository.getRepositoryId();
        if (id == null || id.isEmpty()) {
            TaskRepositoryControl s = Lookup.getDefault().lookup(TaskRepositoryControl.class);
            id = "repo-" + (s == null
                    ? Long.toHexString(System.nanoTime())
                    : s.createTaskId().replace("t-", ""));
            repository.setRepositoryId(id);
        }
        repository.setDisplayName(nameField.getText().trim());
        repository.setCsvPath(pathField.getText().trim());
        TaskRepositoryControl store = Lookup.getDefault().lookup(TaskRepositoryControl.class);
        if (store != null) {
            store.registerRepository(id, repository.getCsvPath(), repository.getDisplayName());
        }
    }

    @Override
    public void cancelChanges() {
        // repository fields are only written on applyChanges; nothing to undo here.
        populate();
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        changeListeners.add(ChangeListener.class, l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        changeListeners.remove(ChangeListener.class, l);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private void fireChanged() {
        for (ChangeListener l : changeListeners.getListeners(ChangeListener.class)) {
            l.stateChanged(CHANGED);
        }
    }

    private JComponent buildComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        nameField = new JTextField(24);
        pathField = new JTextField(24);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(ev -> browsePath());

        DocumentListener dl = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fireChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fireChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fireChanged();
            }
        };
        nameField.getDocument().addDocumentListener(dl);
        pathField.getDocument().addDocumentListener(dl);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(nameField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Tasks file:"), gbc);
        JPanel pathRow = new JPanel(new BorderLayout(4, 0));
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browse, BorderLayout.EAST);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(pathRow, gbc);
        return panel;
    }

    private void browsePath() {
        JFileChooser chooser = new JFileChooser();
        if (pathField != null && !pathField.getText().trim().isEmpty()) {
            chooser.setSelectedFile(new java.io.File(pathField.getText().trim()));
        }
        if (chooser.showSaveDialog(component) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            fireChanged();
        }
    }
}