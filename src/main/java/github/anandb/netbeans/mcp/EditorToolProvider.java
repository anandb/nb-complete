package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.project.ACPProjectManager;
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

public class EditorToolProvider {

    private static final Logger LOG = Logger.from(EditorToolProvider.class);
    private final ObjectMapper mapper = MapperSupplier.get();

    public void registerTools(McpTools mcpTools) {
        registerGetOpenedFiles(mcpTools);
        registerOpenFileAtLine(mcpTools);
        registerRenameSession(mcpTools);
    }

    private void registerGetOpenedFiles(McpTools mcpTools) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        mcpTools.registerTool(
                "get_opened_files",
                "Returns a list of file paths currently open in editor tabs",
                schema,
                new ToolExecutor<EmptyToolInput, List<String>>(EmptyToolInput.class) {
                    @Override
                    public List<String> execute(EmptyToolInput args) throws Exception {
                        java.util.concurrent.CompletableFuture<List<String>> future =
                                new java.util.concurrent.CompletableFuture<>();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                Project[] projects = ACPProjectManager.getInstance().getAllOpenProjects();
                                List<String> paths = new ArrayList<>();
                                for (var editor : EditorRegistry.componentList()) {
                                    FileObject fo = NbEditorUtilities.getFileObject(editor.getDocument());
                                    if (fo == null) continue;
                                    String filePath = fo.getPath();
                                    // Only include files inside open projects — phantom paths
                                    // like /tmp/xxx (when no project is open) confuse the AI.
                                    boolean inProject = false;
                                    for (Project p : projects) {
                                        String projectDir = p.getProjectDirectory().getPath();
                                        if (filePath.startsWith(projectDir)) {
                                            inProject = true;
                                            break;
                                        }
                                    }
                                    if (inProject) {
                                        paths.add(filePath);
                                    }
                                }
                                future.complete(paths);
                            } catch (Exception e) {
                                future.completeExceptionally(e);
                            }
                        });
                        return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                    }
                });
    }

    private void registerOpenFileAtLine(McpTools mcpTools) {
        ObjectNode schema = mapper.createObjectNode();
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
                "open_file_at_line",
                "Opens a file at the specified line number in the editor. Only works for files within the current project. " +
                "Call when you see phrases like 'Show me where this happens in the code, or 'open the files where we use this'" +
                "'Where all do we have this kind of pattern', 'Show me the code where XXX'",
                schema,
                new ToolExecutor<OpenFileInput, Map<String, Object>>(OpenFileInput.class) {
                    @Override
                    public Map<String, Object> execute(OpenFileInput args) throws Exception {
                        if (args.filePath() == null) {
                            return Map.of("status", "error", "message", "No file path provided");
                        }
                        SwingUtilities.invokeLater(() -> {
                            boolean inProject = false;
                            for (Project p : ACPProjectManager.getInstance().getAllOpenProjects()) {
                                String projectDir = p.getProjectDirectory().getPath();
                                if (args.filePath().startsWith(projectDir)) {
                                    inProject = true;
                                    break;
                                }
                            }
                            if (!inProject) {
                                return;
                            }
                            try {
                                FileObject fo = FileUtil.toFileObject(new File(args.filePath()));
                                if (fo != null) {
                                    DataObject data = DataObject.find(fo);
                                    LineCookie lc = data.getLookup().lookup(LineCookie.class);
                                    if (lc != null) {
                                        Line.Set lineSet = lc.getLineSet();
                                        Line line = lineSet.getOriginal(Math.max(0, args.line() - 1));
                                        line.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                                    }
                                }
                            } catch (Exception e) {
                                LOG.warn("Failed to open file at line: {0}", e.getMessage());
                            }
                        });
                        return Map.of("status", "ok");
                    }
                });
    }

    private void registerRenameSession(McpTools mcpTools) {
        ObjectNode schema = mapper.createObjectNode();
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
                "Sets a custom title for a chat session. Use this to give sessions meaningful names. " +
                "Omit sessionId to rename the current active session.",
                schema,
                new ToolExecutor<RenameSessionInput, Map<String, Object>>(RenameSessionInput.class) {
                    @Override
                    public Map<String, Object> execute(RenameSessionInput args) throws Exception {
                        if (args.title() == null) {
                            return Map.of("status", "error", "message", "title is required");
                        }
                        String title = args.title().trim();
                        LOG.info("rename_session tool called: title=\"{0}\", sessionId={1}",
                                title, args.sessionId() != null ? args.sessionId() : "(current)");
                        SwingUtilities.invokeLater(() -> {
                            SessionControl sc = Lookup.getDefault().lookup(SessionControl.class);
                            if (sc == null) {
                                LOG.warn("rename_session: SessionControl not found");
                                return;
                            }
                            String sessionId = args.sessionId() != null
                                    ? args.sessionId()
                                    : sc.getCurrentSessionId();
                            if (sessionId == null) {
                                LOG.warn("rename_session: no active session");
                                return;
                            }
                            sc.renameSession(sessionId, title);
                            LOG.info("rename_session completed: sessionId={0}, title=\"{1}\"",
                                    sessionId, title);
                        });
                        return Map.of("status", "ok");
                    }
                });
    }
}
