package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.contract.StashDiffControl;
import github.anandb.netbeans.support.PluginSettings;

/**
 * Tests the {@code diff_stash} MCP tool: feature-toggle gating at
 * registration, repository resolution (explicit path vs. first open project),
 * stash-index validation, and delegation to {@link StashDiffControl}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StashDiffToolProviderTest {

    @TempDir
    Path tempDir;

    @Mock
    private McpTools mcpTools;

    @Mock
    private StashDiffControl control;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private Project project;

    @Mock
    private FileObject projectDirFO;

    private Lookup mockLookup;
    private MockedStatic<Lookup> lookupMock;
    private MockedStatic<FileUtil> fileUtilMock;
    private MockedStatic<PluginSettings> settingsMock;
    private StashDiffToolProvider provider;

    @BeforeEach
    void setUp() {
        lookupMock = mockStatic(Lookup.class);
        mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(StashDiffControl.class)).thenReturn(control);
        when(mockLookup.lookup(ProjectQuery.class)).thenReturn(projectQuery);
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[0]);

        fileUtilMock = mockStatic(FileUtil.class);
        provider = new StashDiffToolProvider();
    }

    @AfterEach
    void tearDown() {
        if (lookupMock != null) {
            lookupMock.close();
        }
        if (fileUtilMock != null) {
            fileUtilMock.close();
        }
        if (settingsMock != null) {
            settingsMock.close();
        }
    }

    private void featureEnabled(boolean enabled) {
        settingsMock = mockStatic(PluginSettings.class);
        settingsMock.when(PluginSettings::isStashDiffEnabled).thenReturn(enabled);
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<StashDiffInput, Map<String, Object>> diffStash() {
        if (settingsMock == null) {
            featureEnabled(true);
        }
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("diff_stash"), any(), any(), captor.capture());
        return captor.getValue();
    }

    /** Creates a directory containing a {@code .git} marker (all the provider checks). */
    private File gitRepo(String name) {
        File repo = tempDir.resolve(name).toFile();
        File git = new File(repo, ".git");
        git.mkdirs();
        return repo;
    }

    // ---- registration gating ------------------------------------------------

    @Test
    void registrationSkippedWhenFeatureDisabled() {
        featureEnabled(false);

        provider.registerTools(mcpTools);

        verifyNoInteractions(mcpTools);
    }

    @Test
    void registrationAddsToolWhenFeatureEnabled() {
        featureEnabled(true);

        provider.registerTools(mcpTools);

        verify(mcpTools).registerTool(eq("diff_stash"), any(), any(), any());
    }

    // ---- control availability ----------------------------------------------

    @Test
    void reportsMissingStashDiffControl() throws Exception {
        when(mockLookup.lookup(StashDiffControl.class)).thenReturn(null);
        File repo = gitRepo("repo");

        Map<String, Object> result = diffStash()
                .execute(new StashDiffInput(0, repo.getAbsolutePath()));

        assertEquals("error", result.get("status"));
        assertEquals("StashDiffControl not available", result.get("message"));
    }

    // ---- explicit repoDir ---------------------------------------------------

    @Test
    void rejectsMissingRepoDirectory() throws Exception {
        String missing = tempDir.resolve("nope").toString();

        Map<String, Object> result = diffStash().execute(new StashDiffInput(0, missing));

        assertEquals("error", result.get("status"));
        assertEquals("Repository directory not found: " + missing, result.get("message"));
    }

    @Test
    void rejectsNonGitDirectory() throws Exception {
        File plain = tempDir.resolve("plain").toFile();
        plain.mkdirs();

        Map<String, Object> result = diffStash()
                .execute(new StashDiffInput(0, plain.getAbsolutePath()));

        assertEquals("error", result.get("status"));
        assertEquals("Not a Git repository (no .git directory): " + plain.getAbsolutePath(),
                result.get("message"));
    }

    @Test
    void opensDiffForExplicitRepoAndTrimsWhitespace() throws Exception {
        File repo = gitRepo("repo");
        when(control.validateStash(repo, 2)).thenReturn(null);

        Map<String, Object> result = diffStash()
                .execute(new StashDiffInput(2, "  " + repo.getAbsolutePath() + "  "));

        assertEquals("ok", result.get("status"));
        assertEquals("Opened stash diff for stash@{2}", result.get("message"));
        verify(control).validateStash(repo, 2);
        verify(control).openStashDiff(repo, 2);
    }

    // ---- default repo resolution -------------------------------------------

    @Test
    void defaultsToFirstGitRepoInOpenProjects() throws Exception {
        File repo = gitRepo("repo");
        File other = tempDir.resolve("not-git").toFile();
        other.mkdirs();
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[] { null, project });
        when(project.getProjectDirectory()).thenReturn(projectDirFO);
        fileUtilMock.when(() -> FileUtil.toFile(projectDirFO)).thenReturn(repo);
        when(control.validateStash(repo, 0)).thenReturn(null);

        Map<String, Object> result = diffStash().execute(new StashDiffInput(0, null));

        assertEquals("ok", result.get("status"));
        verify(control).openStashDiff(repo, 0);
    }

    @Test
    void reportsNoGitRepoWhenProjectListEmpty() throws Exception {
        Map<String, Object> result = diffStash().execute(new StashDiffInput(0, " "));

        assertEquals("error", result.get("status"));
        assertEquals("No Git repository found in open projects. Specify repoDir explicitly.",
                result.get("message"));
    }

    @Test
    void reportsNoGitRepoWhenProjectQueryUnavailable() throws Exception {
        when(mockLookup.lookup(ProjectQuery.class)).thenReturn(null);

        Map<String, Object> result = diffStash().execute(new StashDiffInput(0, null));

        assertEquals("error", result.get("status"));
        assertEquals("No Git repository found in open projects. Specify repoDir explicitly.",
                result.get("message"));
    }

    @Test
    void skipsProjectsWithoutGitDirectory() throws Exception {
        File plain = tempDir.resolve("plain").toFile();
        plain.mkdirs();
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[] { project });
        when(project.getProjectDirectory()).thenReturn(projectDirFO);
        fileUtilMock.when(() -> FileUtil.toFile(projectDirFO)).thenReturn(plain);

        Map<String, Object> result = diffStash().execute(new StashDiffInput(0, null));

        assertEquals("error", result.get("status"));
        assertEquals("No Git repository found in open projects. Specify repoDir explicitly.",
                result.get("message"));
    }

    @Test
    void skipsProjectWithUnresolvableDirectory() throws Exception {
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[] { project });
        when(project.getProjectDirectory()).thenReturn(projectDirFO);
        fileUtilMock.when(() -> FileUtil.toFile(projectDirFO)).thenReturn(null);

        Map<String, Object> result = diffStash().execute(new StashDiffInput(0, null));

        assertEquals("error", result.get("status"));
        assertEquals("No Git repository found in open projects. Specify repoDir explicitly.",
                result.get("message"));
    }

    // ---- stash index validation --------------------------------------------

    @Test
    void requiresStashIndex() throws Exception {
        File repo = gitRepo("repo");

        Map<String, Object> result = diffStash()
                .execute(new StashDiffInput(null, repo.getAbsolutePath()));

        assertEquals("error", result.get("status"));
        assertEquals("stashIndex is required", result.get("message"));
        verify(control, never()).openStashDiff(any(), anyInt());
    }

    @Test
    void rejectsNegativeStashIndex() throws Exception {
        File repo = gitRepo("repo");

        Map<String, Object> result = diffStash()
                .execute(new StashDiffInput(-1, repo.getAbsolutePath()));

        assertEquals("error", result.get("status"));
        assertEquals("stashIndex must be non-negative, got: -1", result.get("message"));
    }

    @Test
    void returnsStashValidationError() throws Exception {
        File repo = gitRepo("repo");
        when(control.validateStash(repo, 5)).thenReturn("No such stash: stash@{5}");

        Map<String, Object> result = diffStash()
                .execute(new StashDiffInput(5, repo.getAbsolutePath()));

        assertEquals("error", result.get("status"));
        assertEquals("No such stash: stash@{5}", result.get("message"));
        verify(control, never()).openStashDiff(any(), anyInt());
    }

    @Test
    void schemaIsRegisteredWithDescription() {
        featureEnabled(true);
        provider.registerTools(mcpTools);

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(mcpTools).registerTool(eq("diff_stash"), description.capture(), any(), any());
        assertNotNull(description.getValue());
        assertTrue(description.getValue().contains("stash"),
                "description should explain the tool: " + description.getValue());
    }

}
