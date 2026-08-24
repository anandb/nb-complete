package github.anandb.netbeans.tasks;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.netbeans.modules.bugtracking.spi.QueryController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.tasks.TasksModel.TaskQuery;

/**
 * {@link QueryController} for the fixed "All tasks" query. Provides an
 * {@link QueryMode#EDIT} criteria editor that lets the user filter the
 * repository's tasks by tag: the selected tags are stored on the
 * {@link TaskQuery} and the provider re-runs the search (OR semantics — a task
 * matches if it carries any selected tag). The filter is session-only and not
 * persisted.
 */
public final class TaskQueryController implements QueryController {

    private static final String[] PREDEFINED_TAGS = {
        "bug", "documentation", "help wanted", "dependencies", "duplicate",
        "enhancement", "feedback", "invalid", "wontfix"
    };

    private final TaskQueryProvider provider;
    private final TaskQuery query;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final TagsEditor editor = new TagsEditor();
    private JComponent component;

    public TaskQueryController(TaskQueryProvider provider, TaskQuery query) {
        this.provider = provider;
        this.query = query;
    }

    @Override
    public boolean providesMode(QueryMode mode) {
        return mode == QueryMode.EDIT;
    }

    @Override
    public JComponent getComponent(QueryMode mode) {
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
    public void opened() {
    }

    @Override
    public void closed() {
    }

    @Override
    public boolean saveChanges(String name) {
        // Filter is session-only; nothing to persist.
        return true;
    }

    @Override
    public boolean discardUnsavedChanges() {
        return true;
    }

    @Override
    public boolean isChanged() {
        // Editing the filter does not require a save (it is applied live).
        return false;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private JComponent buildComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.NORTHWEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 0;
        panel.add(new JLabel("Tags:"), g);

        g.gridx = 1;
        g.weightx = 1;
        // Seed the editor with any tags already present in the repository so the
        // dropdown offers the real vocabulary, not just the predefined set.
        editor.setAvailableTags(collectRepositoryTags());
        editor.setSelectedTags(query.getTagFilter());
        editor.addChangeListener(() -> {
            // Apply live so the Dashboard task list tracks the selection.
            provider.setTagFilter(query, editor.getSelectedTags());
        });
        panel.add(editor, g);

        // Explicit "Search" button: the query editor has no toolbar Search
        // action, and saving is a no-op, so this is the visible way to re-run
        // the tag filter.
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(ev -> {
            provider.setTagFilter(query, editor.getSelectedTags());
        });
        g.gridx = 0;
        g.gridy = 1;
        g.gridwidth = 2;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        panel.add(searchBtn, g);
        g.gridwidth = 1;

        // Wrap in a top-aligned BorderLayout so the form pins to the top of the
        // query editor area and any extra vertical space falls below, instead of
        // the GridBagLayout panel being vertically centered.
        JPanel wrapper = new JPanel(new java.awt.BorderLayout());
        wrapper.add(panel, java.awt.BorderLayout.NORTH);
        return wrapper;
    }

    private Set<String> collectRepositoryTags() {
        Set<String> tags = new LinkedHashSet<>(List.of(PREDEFINED_TAGS));
        TaskRepositoryControl s = Lookup.getDefault().lookup(TaskRepositoryControl.class);
        String repoId = query.getRepository() == null ? null : query.getRepository().getRepositoryId();
        if (s != null && repoId != null) {
            for (TaskRecord t : s.list(repoId)) {
                if (t.tags() != null) {
                    for (String tag : t.tags()) {
                        String v = tag.trim();
                        if (!v.isEmpty()) {
                            tags.add(v);
                        }
                    }
                }
            }
        }
        return tags;
    }
}
