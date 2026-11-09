package github.anandb.netbeans.ui.mdproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import github.anandb.netbeans.project.mdproject.MdProjectMarker;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;

/**
 * "Open as Markdown Project" must be invisible while the project type is disabled, must convert a
 * folder only when it can also open it, and must leave the folder exactly as it was on every
 * failure path — thrown or returned, half-way or not started.
 */
class UseAsMarkdownProjectActionTest {

    private boolean original;

    @BeforeEach
    void rememberSetting() {
        original = PluginSettings.isMarkdownProjectEnabled();
    }

    @AfterEach
    void restoreSetting() throws Exception {
        PluginSettings.setMarkdownProjectEnabled(original);
        pumpEdt(); // the toolbar presenter refreshes through invokeLater
    }

    @Test
    void theFileMenuItemIsAbsentWhileTheTypeIsDisabled() throws Exception {
        PluginSettings.setMarkdownProjectEnabled(false);
        assertEquals(0, menuItems().length, "a disabled project type must not offer a menu item");
    }

    @Test
    void theFileMenuItemIsPresentWhileTheTypeIsEnabled() throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        assertEquals(1, menuItems().length);
    }

    @Test
    void theToolbarButtonHidesWhileTheTypeIsDisabled() throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        Component button = toolbarButton();
        assertShown(button);

        PluginSettings.setMarkdownProjectEnabled(false);
        prefChange(button, PreferenceKeys.ACTIONS_MARKDOWN_PROJECT,
                "false"); // the preference event the JDK dispatcher would deliver
        pumpEdt();
        assertHidden(button);

        PluginSettings.setMarkdownProjectEnabled(true);
        prefChange(button, PreferenceKeys.ACTIONS_MARKDOWN_PROJECT, "true");
        pumpEdt();
        assertShown(button);
    }

    @Test
    void anotherPreferenceKeyNeverTogglesThePresenter() throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        Component button = toolbarButton();

        prefChange(button, "some.other.key", "false");
        pumpEdt();

        assertShown(button, "an unrelated key must not refresh the toolbar button");
    }

    @Test
    void convertingADisabledTypeLeavesTheFolderAlone(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(false);
        FileObject folder = folder(dir);
        FakeProjects projects = new FakeProjects((Project) null);

        assertEquals(UseAsMarkdownProjectAction.Outcome.DISABLED,
                UseAsMarkdownProjectAction.useAsProject(folder, projects));
        assertFalse(Files.exists(dir.resolve(MdProjectMarker.NAME)),
                "nothing may be written while the project type is off");
        assertTrue(projects.opened.isEmpty());
    }

    @Test
    void convertingAPlainFolderWritesTheMarkerAndOpensIt(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        FileObject folder = folder(dir);
        FakeProjects projects = new FakeProjects(null, new FakeProject(folder));

        assertEquals(UseAsMarkdownProjectAction.Outcome.CONVERTED,
                UseAsMarkdownProjectAction.useAsProject(folder, projects));
        assertTrue(Files.exists(dir.resolve(MdProjectMarker.NAME)),
                "the conversion must leave a marker behind");
        assertEquals(1, projects.opened.size(), "the new project must be opened");
        assertEquals(1, projects.cacheClears, "the cached 'not a project' answer must be dropped");
    }

    @Test
    void aConversionThatCannotOpenTheFolderRemovesTheMarkerAgain(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        FakeProjects projects = new FakeProjects(null, null);

        assertEquals(UseAsMarkdownProjectAction.Outcome.FAILED,
                UseAsMarkdownProjectAction.useAsProject(folder(dir), projects));
        assertFalse(Files.exists(dir.resolve(MdProjectMarker.NAME)),
                "a failed conversion must not leave the folder marked");
    }

    @Test
    void aThrownFindProjectAfterTheMarkerWasWrittenAlsoCleansUp(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        FileObject folder = folder(dir);
        FakeProjects projects = new FakeProjects((Project) null);
        projects.throwIOExceptionOnCall(2, new IOException("disk died"));

        assertEquals(UseAsMarkdownProjectAction.Outcome.FAILED,
                UseAsMarkdownProjectAction.useAsProject(folder, projects));
        assertFalse(Files.exists(dir.resolve(MdProjectMarker.NAME)),
                "every failure, thrown or returned, leaves the folder unmarked");
    }

    @Test
    void anUnexpectedThrowableIsReportedAndCleansUp(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        FileObject folder = folder(dir);
        FakeProjects projects = new FakeProjects(null, new FakeProject(folder));
        projects.openThrows = true;

        assertEquals(UseAsMarkdownProjectAction.Outcome.FAILED,
                UseAsMarkdownProjectAction.useAsProject(folder, projects));
        assertFalse(Files.exists(dir.resolve(MdProjectMarker.NAME)),
                "a RuntimeException must not skip the cleanup");
    }

    @Test
    void aFailedConversionKeepsAMarkerTheUserAlreadyHad(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        Files.writeString(dir.resolve(MdProjectMarker.NAME), "# my notes");
        FileObject folder = folder(dir);
        FakeProjects projects = new FakeProjects(null, null);

        assertEquals(UseAsMarkdownProjectAction.Outcome.FAILED,
                UseAsMarkdownProjectAction.useAsProject(folder, projects));
        assertEquals("# my notes", Files.readString(dir.resolve(MdProjectMarker.NAME)),
                "a marker this call did not create must never be deleted");
    }

    @Test
    void aFailedConversionKeepsAMarkerAConcurrentWriterJustCreated() throws IOException {
        PluginSettings.setMarkdownProjectEnabled(true);
        FileObject raced = mock(FileObject.class);
        FileObject dir = mock(FileObject.class);
        // ensure() loses the race to a concurrent writer, findProject still comes up empty.
        when(dir.isFolder()).thenReturn(true);
        when(dir.getFileObject(MdProjectMarker.NAME)).thenReturn(null, raced, raced);
        when(dir.createData(MdProjectMarker.NAME)).thenThrow(new IOException("already exists"));
        FakeProjects projects = new FakeProjects(null, null);

        assertEquals(UseAsMarkdownProjectAction.Outcome.FAILED,
                UseAsMarkdownProjectAction.useAsProject(dir, projects));
        verify(raced, never()).delete();
    }

    @Test
    void aFolderThatIsAlreadySomeOtherProjectIsOpenedWithoutAMarker(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        FileObject folder = folder(dir);
        FakeProject existing = new FakeProject(folder);
        FakeProjects projects = new FakeProjects(existing);

        assertEquals(UseAsMarkdownProjectAction.Outcome.OPENED_EXISTING,
                UseAsMarkdownProjectAction.useAsProject(folder, projects));
        assertFalse(Files.exists(dir.resolve(MdProjectMarker.NAME)));
        assertEquals(List.of(existing), projects.opened);
    }

    @Test
    void anAlreadyOpenProjectIsReportedWithoutOpeningItAgain(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        FileObject folder = folder(dir);
        FakeProject existing = new FakeProject(folder);
        FakeProjects projects = new FakeProjects(existing);
        projects.alreadyOpen = true;

        assertEquals(UseAsMarkdownProjectAction.Outcome.ALREADY_OPEN,
                UseAsMarkdownProjectAction.useAsProject(folder, projects));
        assertTrue(projects.opened.isEmpty(), "nothing to open");
    }

    @Test
    void aNonFolderIsRejectedWithoutWritingAnything(@TempDir Path dir) throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        FileObject data = FileUtil.toFileObject(
                Files.createFile(dir.resolve("notes.txt")).toFile());
        assertNotNull(data);
        FakeProjects projects = new FakeProjects();

        assertEquals(UseAsMarkdownProjectAction.Outcome.FAILED,
                UseAsMarkdownProjectAction.useAsProject(data, projects));
        assertFalse(Files.exists(dir.resolve(MdProjectMarker.NAME)));
    }

    @Test
    void aNullFolderIsRejectedWithoutTouchingTheProjectManager() {
        FakeProjects projects = new FakeProjects();
        assertEquals(UseAsMarkdownProjectAction.Outcome.FAILED,
                UseAsMarkdownProjectAction.useAsProject(null, projects));
    }

    private static FileObject folder(Path dir) throws IOException {
        FileObject folder = FileUtil.toFileObject(dir.toFile());
        assertNotNull(folder);
        return folder;
    }

    private static JComponent[] menuItems() throws Exception {
        AtomicReference<JComponent[]> items = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                items.set(new UseAsMarkdownProjectAction().getMenuPresenters()));
        return items.get();
    }

    private static Component toolbarButton() throws Exception {
        AtomicReference<Component> button = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                button.set((UseAsMarkdownProjectAction.ToolbarButton)
                        new UseAsMarkdownProjectAction().getToolbarPresenter()));
        return button.get();
    }

    private static void prefChange(Component button, String key, String value) throws Exception {
        UseAsMarkdownProjectAction.ToolbarButton toolbarButton =
                (UseAsMarkdownProjectAction.ToolbarButton) button;
        SwingUtilities.invokeAndWait(() -> toolbarButton.preferenceChanged(
                new PreferenceChangeEvent(Preferences.userRoot(), key, value)));
    }

    /** Flushes the marshalled EDT tasks of the updateState deferral. */
    private static void pumpEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void assertHidden(Component button) {
        assertFalse(button.isVisible(), "the button must vanish while the type is off");
        assertFalse(button.isEnabled());
    }

    private static void assertShown(Component button) {
        assertShown(button, null);
    }

    private static void assertShown(Component button, String message) {
        assertTrue(button.isVisible(), message != null ? message + ": visible" : "the button must be visible");
        assertTrue(button.isEnabled(), message != null ? message + ": enabled" : "the button must be enabled");
    }

    /** Answers {@code findProject} from a list, one per call; records what was opened. */
    private static final class FakeProjects implements UseAsMarkdownProjectAction.ProjectGateway {
        private final List<Project> answers = new ArrayList<>();
        private int next;
        private int findCalls;
        private IOException ioToThrow;
        private int ioThrowCall;
        final List<Project> opened = new ArrayList<>();
        int cacheClears;
        boolean alreadyOpen;
        boolean openThrows;

        FakeProjects(Project... findProjectAnswers) {
            Collections.addAll(answers, findProjectAnswers);
        }

        void throwIOExceptionOnCall(int call, IOException ex) {
            ioThrowCall = call;
            ioToThrow = ex;
        }

        @Override
        public Project findProject(FileObject dir) throws IOException {
            int call = ++findCalls;
            if (call == ioThrowCall) {
                throw ioToThrow;
            }
            return next < answers.size() ? answers.get(next++) : null;
        }

        @Override
        public void clearNonProjectCache() {
            cacheClears++;
        }

        @Override
        public void open(Project project) {
            if (openThrows) {
                throw new IllegalStateException("project UI unreachable");
            }
            opened.add(project);
        }

        @Override
        public boolean isOpen(Project project) {
            return alreadyOpen;
        }

        @Override
        public String displayName(Project project) {
            return "fake";
        }
    }

    private static final class FakeProject implements Project {
        private final FileObject dir;

        FakeProject(FileObject dir) {
            this.dir = dir;
        }

        @Override
        public FileObject getProjectDirectory() {
            return dir;
        }

        @Override
        public Lookup getLookup() {
            return Lookups.fixed(this);
        }
    }
}
