package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.project.ACPProjectManager;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.StashDiffAction;

import java.io.File;
import java.util.Map;

import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;

public class StashDiffToolProvider {

    private static final Logger LOG = Logger.from(StashDiffToolProvider.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    public void registerTools(McpTools mcpTools) {
        if (!PluginSettings.isStashDiffEnabled()) {
            LOG.info("Stash Diff is disabled, skipping diff_stash MCP tool registration");
            return;
        }

        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode stashIndexProp = properties.putObject("stashIndex");
        stashIndexProp.put("type", "number");
        stashIndexProp.put("description", "The stash index to diff (e.g., 0 for stash@{0})");

        ObjectNode repoDirProp = properties.putObject("repoDir");
        repoDirProp.put("type", "string");
        repoDirProp.put("description", "Optional repository path. If not provided, uses the first Git repo found in open projects.");

        ArrayNode required = schema.putArray("required");
        required.add("stashIndex");

        mcpTools.registerTool(
                "diff_stash",
                "Opens the side-by-side stash diff viewer for a given stash index. " +
                "Useful when the user asks to see what's in a stash or wants to compare stash changes.",
                schema,
                new ToolExecutor<StashDiffInput, Map<String, Object>>(StashDiffInput.class) {
                    @Override
                    public Map<String, Object> execute(StashDiffInput args) throws Exception {
                        File repoDir;
                        if (args.repoDir() != null && !args.repoDir().isBlank()) {
                            repoDir = new File(args.repoDir().trim());
                            if (!repoDir.isDirectory()) {
                                return Map.of("status", "error", "message", "Repository directory not found: " + args.repoDir());
                            }
                        } else {
                            repoDir = findFirstGitRepo();
                            if (repoDir == null) {
                                return Map.of("status", "error", "message", "No Git repository found in open projects. Specify repoDir explicitly.");
                            }
                        }

                        int stashIndex = args.stashIndex();
                        LOG.info("diff_stash tool called: stashIndex={0}, repoDir={1}", stashIndex, repoDir.getAbsolutePath());

                        StashDiffAction.openStashDiff(repoDir, stashIndex);
                        return Map.of("status", "ok", "message", "Opened stash diff for stash@{" + stashIndex + "}");
                    }
                });
    }

    private static File findFirstGitRepo() {
        Project[] projects = ACPProjectManager.getInstance().getAllOpenProjects();
        for (Project p : projects) {
            if (p == null) continue;
            File projectDir = FileUtil.toFile(p.getProjectDirectory());
            if (projectDir != null && new File(projectDir, ".git").exists()) {
                return projectDir;
            }
        }
        return null;
    }
}
