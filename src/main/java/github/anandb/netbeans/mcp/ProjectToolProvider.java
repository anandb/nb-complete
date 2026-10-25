package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.contract.ProjectQuery;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Registers MCP tools that expose read-only IDE state to the AI agent.
 * Tools that invoke build/test/run actions were removed: NetBeans action
 * providers are fire-and-forget and return no result to the agent, so they
 * provide no feedback loop. Only tools that return real data are registered.
 */
public class ProjectToolProvider {

    public void registerTools(McpTools mcpTools) {
        registerListProjects(mcpTools);
    }

    private void registerListProjects(McpTools mcpTools) {
        ObjectNode schema = ToolSchema.object().build();

        mcpTools.registerTool(
                "list_projects",
                ToolDescriptions.get("list_projects"),
                schema,
                new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
                    @Override
                    public Map<String, Object> execute(EmptyToolInput args) throws Exception {
                        ProjectQuery query = Lookup.getDefault().lookup(ProjectQuery.class);
                        var projects = query == null ? null : query.getAllOpenProjects();
                        List<String> paths = new ArrayList<>();
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
