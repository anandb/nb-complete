package github.anandb.netbeans.ui.mdproject;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.awt.ActionID;
import org.openide.awt.DynamicMenuContent;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.Presenter;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.project.mdproject.MdProjectMarker;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.ui.components.UIUtils;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.rendering.ThemeManager;

/**
 * Turns an ordinary folder into a Markdown project: writes the {@code .mdproject} marker and
 * opens the folder in the Projects window.
 *
 * <p>Registered in {@code layer.xml} as one action with two presentations: the File menu and the
 * main toolbar next to Open Project. It always asks for the folder, so both presentations behave
 * the same from anywhere in the IDE and never act on whatever happens to be selected.</p>
 *
 * <p>Both presentations disappear while <em>Enable Markdown Project Type</em> is off, using the
 * same presenter pattern as the stash-diff toolbar button: a preference listener refreshes the
 * toolbar button, and {@link DynamicMenuContent} returns no menu items.</p>
 *
 * <p>Only the folder chooser and the status line touch Swing. Loading the project and writing the
 * marker happen on a background thread, because both are I/O and the project manager takes a lock
 * that a running save or build can hold. One conversion runs at a time; re-clicks are answered
 * with a status line instead of queueing a second run.</p>
 */
@ActionID(category = "Project", id = "github.anandb.netbeans.ui.mdproject.UseAsMarkdownProjectAction")
public final class UseAsMarkdownProjectAction extends AbstractAction
        implements Presenter.Toolbar, DynamicMenuContent {

    private static final Logger LOG = Logger.from(UseAsMarkdownProjectAction.class);

    private static final String ICON_FILE = "markdown_project.svg";
    /** Menus draw 16 px icons whatever the source size. */
    private static final int MENU_ICON_SIZE = 16;

    private static final RequestProcessor WORK = new RequestProcessor("Markdown project conversion", 1);

    /** Set while a conversion is posted and not finished, so re-clicks queue nothing. */
    private static final AtomicBoolean CONVERTING = new AtomicBoolean();

    /** What the conversion did, so callers (and tests) do not have to read the status bar. */
    enum Outcome {
        ALREADY_OPEN, OPENED_EXISTING, CONVERTED, DISABLED, FAILED
    }

    /** Buttons and menu items read these values, not the layer attributes. */
    public UseAsMarkdownProjectAction() {
        putValue(NAME, NbBundle.getMessage(UseAsMarkdownProjectAction.class, "CTL_UseAsMarkdownProject"));
        putValue(SHORT_DESCRIPTION,
                NbBundle.getMessage(UseAsMarkdownProjectAction.class, "CTL_UseAsMarkdownProjectTip"));
        putValue(SMALL_ICON, ThemeManager.getIcon(ICON_FILE, PluginSettings.getToolbarIconSize()));
    }

    @Override
    public boolean isEnabled() {
        return PluginSettings.isMarkdownProjectEnabled();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        // Always ask: the global context carries whatever is selected or open (an open project's
        // directory, the file in the editor), which would silently override the user's intent here.
        FileObject folder = chooseFolder();
        if (folder != null) {
            convertInBackground(folder);
        }
    }

    // --- Presentations ---

    /** Toolbar button, hidden (not greyed) while the project type is switched off. */
    @Override
    public Component getToolbarPresenter() {
        return new ToolbarButton(this);
    }

    /**
     * The main-toolbar button. Package-visible so the preference-refresh contract can be driven
     * headlessly by tests; registered into the layer like the stash-diff button.
     */
    static final class ToolbarButton extends JButton {
        private static final long serialVersionUID = 1L;

        private final PreferenceChangeListener prefListener = this::preferenceChanged;

        ToolbarButton(UseAsMarkdownProjectAction action) {
            Icon enabledIcon = ThemeManager.getIcon(ICON_FILE, PluginSettings.getToolbarIconSize());
            setIcon(enabledIcon);
            setToolTipText(action.getValue(SHORT_DESCRIPTION).toString());
            getAccessibleContext().setAccessibleName(action.getValue(NAME).toString());
            getAccessibleContext().setAccessibleDescription(
                    action.getValue(SHORT_DESCRIPTION).toString());
            UIUtils.styleToolbarButton(this);
            addActionListener(action);
        }

        @Override
        public Point getToolTipLocation(MouseEvent event) {
            return UIUtils.tooltipAboveButton(event, this);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            updateState();
            PlatformBridge.prefStoreSafe().addChangeListener(prefListener);
        }

        @Override
        public void removeNotify() {
            PlatformBridge.prefStoreSafe().removeChangeListener(prefListener);
            super.removeNotify();
        }

        /** One preference event, whatever thread delivered it. */
        void preferenceChanged(PreferenceChangeEvent evt) {
            if (PreferenceKeys.ACTIONS_MARKDOWN_PROJECT.equals(evt.getKey())) {
                // Preference events arrive on the JDK preference dispatcher, not on the EDT.
                SwingUtilities.invokeLater(this::updateState);
            }
        }

        private void updateState() {
            boolean available = PluginSettings.isMarkdownProjectEnabled();
            setVisible(available);
            setEnabled(available);
        }
    }

    @Override
    public JComponent[] getMenuPresenters() {
        return menuPresenters();
    }

    @Override
    public JComponent[] synchMenuPresenters(JComponent[] items) {
        return menuPresenters();
    }

    /** No items at all while the project type is off: the item must not be present, not grey. */
    private JComponent[] menuPresenters() {
        if (!PluginSettings.isMarkdownProjectEnabled()) {
            return new JComponent[0];
        }
        JMenuItem item = new JMenuItem(getValue(NAME).toString());
        Icon icon = ThemeManager.getIcon(ICON_FILE, MENU_ICON_SIZE);
        if (icon != null) {
            item.setIcon(icon);
        }
        item.addActionListener(this);
        return new JComponent[] {item};
    }

    // --- Work ---

    /** Shows the folder chooser; must be called on the EDT. */
    private static FileObject chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(NbBundle.getMessage(UseAsMarkdownProjectAction.class, "MSG_ChooseFolder"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(WindowManager.getDefault().getMainWindow()) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File selected = chooser.getSelectedFile();
        if (selected == null) {
            return null;
        }
        FileObject folder = FileUtil.toFileObject(FileUtil.normalizeFile(selected));
        if (folder == null || !folder.isFolder()) {
            // A path the filesystem cannot resolve (unmounted, unreadable) must not look like a
            // cancel: the user did pick something.
            status("MSG_MarkdownProjectFailed", selected.getName());
            return null;
        }
        return folder;
    }

    /** Marks the folder and opens it off the EDT, then reports the result on the status bar. */
    static void convertInBackground(FileObject folder) {
        if (!CONVERTING.compareAndSet(false, true)) {
            status("MSG_ConversionInProgress");
            return;
        }
        status("MSG_Converting", folder.getNameExt());
        WORK.post(() -> {
            try {
                useAsProject(folder);
            } finally {
                CONVERTING.set(false);
            }
        });
    }

    /**
     * Marks {@code folder} and opens it; every outcome is reported in the status bar.
     *
     * <p>Does project loading and file I/O, so it must not run on the EDT — see
     * {@link #convertInBackground}. A marker written by this call is removed again on any
     * failure — opened or not, thrown or not — so a failed conversion never leaves the folder
     * marked. A marker some other writer created is left alone.</p>
     */
    static Outcome useAsProject(FileObject folder) {
        return useAsProject(folder, REAL);
    }

    /** The conversion itself, with the project-manager calls behind a seam so it can be tested. */
    static Outcome useAsProject(FileObject folder, ProjectGateway projects) {
        if (folder == null || !folder.isFolder()) {
            return report(Outcome.FAILED, null);
        }
        try {
            Project existing = projects.findProject(folder);
            if (existing != null) {
                // Nothing to mark up — the folder is already a project of some type. Opening it is
                // what the user wanted anyway, and a status line alone is easy to miss.
                return openExisting(existing, projects);
            }
            if (!PluginSettings.isMarkdownProjectEnabled()) {
                return report(Outcome.DISABLED, null);
            }
            MdProjectMarker.Created written = MdProjectMarker.ensureCreating(folder);
            boolean succeeded = false;
            try {
                projects.clearNonProjectCache();
                Project project = projects.findProject(folder);
                if (project == null) {
                    return report(Outcome.FAILED, folder.getNameExt());
                }
                projects.open(project);
                succeeded = true;
                return report(Outcome.CONVERTED, folder.getNameExt());
            } finally {
                // Every failure keeps the folder what it was.
                if (written.created() && !succeeded) {
                    deleteMarker(folder);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            LOG.log(Level.WARNING, "Could not mark {0} as a Markdown project", folder.getPath(), ex);
            return report(Outcome.FAILED, folder.getNameExt());
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Unexpected failure marking {0} as a Markdown project",
                    folder.getPath(), ex);
            return report(Outcome.FAILED, folder.getNameExt());
        }
    }

    /** Opens a project the folder already belongs to, or says it is already open. */
    private static Outcome openExisting(Project project, ProjectGateway projects) {
        String name = projects.displayName(project);
        if (projects.isOpen(project)) {
            return report(Outcome.ALREADY_OPEN, name);
        }
        projects.open(project);
        return report(Outcome.OPENED_EXISTING, name);
    }

    /** Best effort: the folder is reported a failure either way, and the reason stays in the log. */
    private static void deleteMarker(FileObject folder) {
        FileObject marker = folder.getFileObject(MdProjectMarker.NAME);
        if (marker == null) {
            return;
        }
        try {
            marker.delete();
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.FINE, "Could not remove the marker from {0}", folder.getPath(), ex);
        }
    }

    private static Outcome report(Outcome outcome, String detail) {
        switch (outcome) {
            case ALREADY_OPEN -> status("MSG_AlreadyOpen", detail);
            case OPENED_EXISTING -> status("MSG_AlreadyProject", detail);
            case CONVERTED -> status("MSG_NowMarkdownProject", detail);
            case DISABLED -> status("MSG_MarkdownProjectDisabled");
            case FAILED -> {
                if (detail == null || detail.isBlank()) {
                    status("MSG_MarkdownProjectFailedPlain");
                } else {
                    status("MSG_MarkdownProjectFailed", detail);
                }
            }
        }
        return outcome;
    }

    /** Status writes are Swing work, whatever thread the conversion finished on. */
    private static void status(String key, Object... args) {
        String text = NbBundle.getMessage(UseAsMarkdownProjectAction.class, key, args);
        SwingUtilities.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(text));
    }

    /** The project-manager calls a conversion makes, so tests need no project infrastructure. */
    interface ProjectGateway {
        Project findProject(FileObject dir) throws IOException;

        void clearNonProjectCache();

        void open(Project project);

        boolean isOpen(Project project);

        String displayName(Project project);
    }

    private static final ProjectGateway REAL = new ProjectGateway() {
        @Override
        public Project findProject(FileObject dir) throws IOException {
            return ProjectManager.getDefault().findProject(dir);
        }

        @Override
        public void clearNonProjectCache() {
            MdProjectCache.clear();
        }

        @Override
        public void open(Project project) {
            OpenProjects.getDefault().open(new Project[] {project}, false, true);
        }

        @Override
        public boolean isOpen(Project project) {
            for (Project open : OpenProjects.getDefault().getOpenProjects()) {
                if (open == project || open.getProjectDirectory().equals(project.getProjectDirectory())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String displayName(Project project) {
            return ProjectUtils.getInformation(project).getDisplayName();
        }
    };
}
