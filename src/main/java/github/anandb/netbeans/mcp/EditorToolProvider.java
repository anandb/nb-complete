package github.anandb.netbeans.mcp;

import org.apache.commons.lang3.exception.ExceptionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import github.anandb.netbeans.support.MapperSupplier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.Project;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class EditorToolProvider {

    private static final Logger LOG = Logger.from(EditorToolProvider.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    public void registerTools(McpTools mcpTools) {
        registerGetOpenedFiles(mcpTools);
        registerOpenFileAtLine(mcpTools);
        registerRenameSession(mcpTools);
    }

    private void registerGetOpenedFiles(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        mcpTools.registerTool(
                "get_tabs",
                "Returns a list of file paths currently open in editor tabs",
                schema,
                new ToolExecutor<EmptyToolInput, List<String>>(EmptyToolInput.class) {
                    @Override
                    public List<String> execute(EmptyToolInput args) throws Exception {
                        // EditorRegistry.componentList() reads a CopyOnWriteArrayList
                        // and NbEditorUtilities.getFileObject() takes a Document (model
                        // object, not a Swing component) — both are safe off-EDT.
                        ProjectQuery pq = Lookup.getDefault().lookup(ProjectQuery.class);
                        Project[] projects = pq == null ? new Project[0] : pq.getAllOpenProjects();
                        List<String> paths = new ArrayList<>();
                        for (var editor : EditorRegistry.componentList()) {
                            FileObject fo = NbEditorUtilities.getFileObject(editor.getDocument());
                            if (fo == null) continue;
                            File file = FileUtil.toFile(fo);
                            String filePath = (file != null) ? file.getAbsolutePath() : fo.getPath();
                            // Only include files inside open projects — phantom paths
                            // like /tmp/xxx (when no project is open) confuse the AI.
                            boolean inProject = false;
                            for (Project p : projects) {
                                FileObject projectDirFO = p.getProjectDirectory();
                                File projectDirFile = FileUtil.toFile(projectDirFO);
                                String projectDir = (projectDirFile != null) ? projectDirFile.getAbsolutePath() : projectDirFO.getPath();
                                if (filePath.startsWith(projectDir)) {
                                    inProject = true;
                                    break;
                                }
                            }
                            if (inProject) {
                                paths.add(filePath);
                            }
                        }
                        return paths;
                    }
                });
    }

    private void registerOpenFileAtLine(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode filePathProp = properties.putObject("filePath");
        filePathProp.put("type", "string");
        filePathProp.put("description", NbBundle.getMessage(EditorToolProvider.class, "DESC_FilePath"));

        ObjectNode lineProp = properties.putObject("line");
        lineProp.put("type", "number");
        lineProp.put("description", NbBundle.getMessage(EditorToolProvider.class, "DESC_LineNumber"));

        ArrayNode required = schema.putArray("required");
        required.add("filePath");
        required.add("line");

        mcpTools.registerTool(
                "open_pos",
                """
                Opens a file at the specified line number in the editor. The cursor jumps to that line and the file
                is focused. Only works for files within the current project (files outside the project are rejected).

                Use when the user wants to:
                - Navigate to a specific location in the code
                - See where something is defined or used
                - Jump to an error, warning, or referenced location
                - Explore code structure visually

                Trigger phrases:
                - 'Show me where this happens'
                - 'Open the file at line X'
                - 'Where is this defined?'
                - 'Show me the code for...'
                - 'Jump to...'
                - 'Where all do we have this pattern?'

                Examples:
                - 'Show me the login method in AuthController' -> filePath='.../AuthController.java', line=42
                - 'Open pom.xml at line 15' -> filePath='.../pom.xml', line=15
                - 'Where is ProcessManager used?' -> nb_open_pos at each call site
                """,
                schema,
                new ToolExecutor<OpenFileInput, Map<String, Object>>(OpenFileInput.class) {
                    @Override
                    public Map<String, Object> execute(OpenFileInput args) throws Exception {
                        if (args.filePath() == null) {
                            return Map.of("status", "error", "message", "No file path provided");
                        }
                        try {
                            File requestedFile = new File(args.filePath()).getCanonicalFile();
                            String canonicalRequested = requestedFile.getCanonicalPath();
                            boolean inProject = false;
                            ProjectQuery pq = Lookup.getDefault().lookup(ProjectQuery.class);
                            Project[] openProjects = pq == null ? new Project[0] : pq.getAllOpenProjects();
                            for (Project p : openProjects) {
                                FileObject projectDirFO = p.getProjectDirectory();
                                File projectDirFile = FileUtil.toFile(projectDirFO);
                                if (projectDirFile == null) continue;
                                String canonicalProject = projectDirFile.getCanonicalPath();
                                if (canonicalRequested.startsWith(canonicalProject)) {
                                    inProject = true;
                                    break;
                                }
                            }
                            if (!inProject) {
                                return Map.of("status", "error", "message", "File is not in the current project");
                            }
                            FileObject fo = FileUtil.toFileObject(requestedFile);
                            if (fo != null) {
                                DataObject data = DataObject.find(fo);
                                LineCookie lc = data.getLookup().lookup(LineCookie.class);
                                if (lc != null) {
                                    Line.Set lineSet = lc.getLineSet();
                                    Line line = lineSet.getOriginal(Math.max(0, args.line() - 1));
                                    SwingUtilities.invokeLater(() -> {
                                        try {
                                            line.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                                        } catch (Exception e) {
                                            LOG.warn("Failed to show line in editor: {0}", ExceptionUtils.getMessage(e));
                                        }
                                    });
                                    return Map.of("status", "ok");
                                }
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed to resolve file at line on background thread: {0}", ExceptionUtils.getMessage(e));
                            return Map.of("status", "error", "message", "Failed to open file");
                        }
                        return Map.of("status", "error", "message", "File or line not found");
                    }
                });
    }

    private void registerRenameSession(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode sessionIdProp = properties.putObject("sessionId");
        sessionIdProp.put("type", "string");
        sessionIdProp.put("description", "The ID of the session to rename. Omit to rename the current session.");

        ObjectNode titleProp = properties.putObject("title");
        titleProp.put("type", "string");
        titleProp.put("description", "The new custom title for the session");

        ArrayNode required = schema.putArray("required");
        required.add("title");

        mcpTools.registerTool(
                "rename_session",
                """
                Sets a custom title for a chat session. Use this to give sessions meaningful names instead of
                auto-generated ones. Omit sessionId to rename the current active session.

                Use when the user wants to:
                - Name a session after its topic (e.g., "Debug login bug", "Refactor auth module")
                - Organize multiple sessions by giving them descriptive titles
                - Rename the current session to something memorable

                Trigger phrases:
                - 'Rename this session to...'
                - 'Call this session...'
                - 'Set the title to...'
                - 'Name this chat...'

                Examples:
                - 'Rename this session to API refactoring' -> title='API refactoring'
                - 'Call this one database migration work' -> title='database migration work'
                - 'Set session title to debug auth flow' -> title='debug auth flow'
                """,
                schema,
                new ToolExecutor<RenameSessionInput, Map<String, Object>>(RenameSessionInput.class) {
                    @Override
                    public Map<String, Object> execute(RenameSessionInput args) throws Exception {
                        if (isBlank(args.title())) {
                            return Map.of("status", "error", "message", "title is required");
                        }

                        LOG.info("rename_session tool called: title=\"{0}\", sessionId={1}",
                                args.title(), isNotBlank(args.sessionId()) ? args.sessionId() : "(current)");

                        final String title = args.title().trim();
                        final SessionControl sc = Lookup.getDefault().lookup(SessionControl.class);
                        final String sessionId = defaultIfBlank(args.sessionId(), sc.getCurrentSessionId());

                        if (isBlank(sessionId)) {
                            LOG.warn("rename_session: no active session");
                            return Map.of("status", "error", "message", "No active session");
                        }

                        sc.renameSession(sessionId, title);
                        LOG.info("rename_session completed: sessionId={0}, title=\"{1}\"", sessionId, title);
                        return Map.of("status", "ok");
                    }
                });
    }
}
