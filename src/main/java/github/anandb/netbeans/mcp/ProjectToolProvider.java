package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.project.ACPProjectManager;
import github.anandb.netbeans.support.MapperSupplier;

import java.io.File;
import java.util.Map;

import org.openide.filesystems.FileUtil;

/**
 * Registers MCP tools that expose read-only IDE state to the AI agent.
 * Tools that invoke build/test/run actions were removed: NetBeans action
 * providers are fire-and-forget and return no result to the agent, so they
 * provide no feedback loop. Only tools that return real data are registered.
 */
public class ProjectToolProvider {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    public void registerTools(McpTools mcpTools) {
        registerListProjects(mcpTools);
    }

    private void registerListProjects(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        mcpTools.registerTool(
                "list_projects",
                "Returns a list of absolute paths of all open NetBeans projects.",
                schema,
                new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
                    @Override
                    public Map<String, Object> execute(EmptyToolInput args) throws Exception {
                        var projects = ACPProjectManager.getInstance().getAllOpenProjects();
                        java.util.List<String> paths = new java.util.ArrayList<>();
                        if (projects != null) {
                            for (var p : projects) {
                                if (p == null) continue;
                                File dir = FileUtil.toFile(p.getProjectDirectory());
                                paths.add(dir != null ? dir.getAbsolutePath() : p.getProjectDirectory().getPath());
                            }
                        }
                        return Map.of("status", "ok", "projects", paths);
                    }
                });
    }
}
