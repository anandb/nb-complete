package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.ui.EditorContextCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;

public class EditorToolProvider {

    private static final Logger LOG = new Logger(EditorToolProvider.class);
    private final ObjectMapper mapper = MapperSupplier.get();

    public void registerTools(McpTools mcpTools) {
        registerGetCurrentFileContext(mcpTools);
        registerGetOpenedFiles(mcpTools);
    }

    private void registerGetCurrentFileContext(McpTools mcpTools) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        mcpTools.registerTool(
                "get_current_file_context",
                "Returns the current file path, cursor position, and any text selection from the active editor tab",
                schema,
                new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
                    @Override
                    public Map<String, Object> execute(EmptyToolInput args) throws Exception {
                        Map<String, Object>[] result = new Map[1];
                        SwingUtilities.invokeAndWait(() -> {
                            result[0] = EditorContextCapture.capture();
                        });
                        return result[0];
                    }
                });
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
}
