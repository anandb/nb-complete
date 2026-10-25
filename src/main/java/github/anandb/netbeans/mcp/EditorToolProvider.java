package github.anandb.netbeans.mcp;

import org.apache.commons.lang3.exception.ExceptionUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.contract.EditorContextQuery;
import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

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

    public void registerTools(McpTools mcpTools) {
        registerGetCurrentFileContext(mcpTools);
        registerGetOpenedFiles(mcpTools);
        registerOpenFileAtLine(mcpTools);
        registerRenameSession(mcpTools);
    }

    private void registerGetCurrentFileContext(McpTools mcpTools) {
        ObjectNode schema = ToolSchema.object().build();

        mcpTools.registerTool(
                "get_current_file_context",
                ToolDescriptions.get("get_current_file_context"),
                schema,
                new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
                    @Override
                    public Map<String, Object> execute(EmptyToolInput args) throws Exception {
                        EditorContextQuery query = Lookup.getDefault().lookup(EditorContextQuery.class);
                        if (query == null) {
                            return Map.of("status", "error", "message", "Editor context service not available");
                        }
                        Map<String, Object>[] result = new Map[1];
                        SwingUtilities.invokeAndWait(() -> {
                            result[0] = query.capture();
                        });
                        return result[0];
                    }
                });
    }

    private void registerGetOpenedFiles(McpTools mcpTools) {
        ObjectNode schema = ToolSchema.object().build();

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
        ObjectNode schema = ToolSchema.object()
                .str("filePath", NbBundle.getMessage(EditorToolProvider.class, "DESC_FilePath"))
                .number("line", NbBundle.getMessage(EditorToolProvider.class, "DESC_LineNumber"))
                .bool("focus", NbBundle.getMessage(EditorToolProvider.class, "DESC_Focus"))
                .require("filePath", "line")
                .build();

        mcpTools.registerTool(
                "open_pos",
                ToolDescriptions.get("open_pos"),
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
                                    // focus=true means the user asked to be taken
                                    // there ("show me..."); the default is background
                                    // navigation. OPEN + NONE (= SHOW_SHOW) opens the
                                    // editor and moves the caret without focusing it;
                                    // FOCUS (= SHOW_GOTO) activates the editor, which
                                    // would interrupt the user mid-edit.
                                    Line.ShowVisibilityType visibility = args.focus()
                                            ? Line.ShowVisibilityType.FOCUS
                                            : Line.ShowVisibilityType.NONE;
                                    SwingUtilities.invokeLater(() -> {
                                        try {
                                            line.show(Line.ShowOpenType.OPEN, visibility);
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
        ObjectNode schema = ToolSchema.object()
                .str("sessionId", "The ID of the session to rename. Omit to rename the current session.")
                .str("title", "The new custom title for the session")
                .require("title")
                .build();

        mcpTools.registerTool(
                "rename_session",
                ToolDescriptions.get("rename_session"),
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
