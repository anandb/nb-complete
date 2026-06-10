package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.project.ACPProjectManager;
import github.anandb.netbeans.support.Logger;
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
                        List<String>[] result = new List[1];
                        SwingUtilities.invokeAndWait(() -> {
                            List<String> paths = new ArrayList<>();
                            for (var editor : EditorRegistry.componentList()) {
                                FileObject fo = NbEditorUtilities.getFileObject(editor.getDocument());
                                if (fo != null) {
                                    paths.add(fo.getPath());
                                }
                            }
                            result[0] = paths;
                        });
                        return result[0];
                    }
                });
    }

    private void registerOpenFileAtLine(McpTools mcpTools) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode filePathProp = properties.putObject("filePath");
        filePathProp.put("type", "string");
        filePathProp.put("description", "Absolute path to the file to open");

        ObjectNode lineProp = properties.putObject("line");
        lineProp.put("type", "number");
        lineProp.put("description", "Line number to navigate to (1-indexed)");

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
}
