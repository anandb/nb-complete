package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static github.anandb.netbeans.mcp.ProjectPathGuard.isInOpenProject;
import static github.anandb.netbeans.mcp.ProjectPathGuard.outsideProjectError;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Registers MCP tools for git operations: status and diff.
 */
public class GitToolProvider {

    private static final Logger LOG = Logger.from(GitToolProvider.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    /** Accepts git revision tokens only (hashes, refs, ranges like a..b,
     *  HEAD~1, ref:path). Rejects option injection — a leading '-' would let
     *  a caller smuggle git flags such as --output into the diff command. */
    private static final Pattern SAFE_DIFF_TARGET = Pattern.compile("^[A-Za-z0-9._/~^:-]+$");

    public void registerTools(McpTools mcpTools) {
        registerGitStatus(mcpTools);
        registerGitDiff(mcpTools);
    }

    private void registerGitStatus(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoDirProp = properties.putObject("repoDir");
        repoDirProp.put("type", "string");
        repoDirProp.put("description", "Repository directory (defaults to project root)");

        mcpTools.registerTool(
                "git_status",
                "Show git status of the repository.",
                schema,
                new ToolExecutor<GitStatusInput, Map<String, Object>>(GitStatusInput.class) {
                    @Override
                    public Map<String, Object> execute(GitStatusInput args) throws Exception {
                        String repoDir = isBlank(args.repoDir()) ? findGitRoot() : args.repoDir();
                        if (repoDir == null) {
                            return Map.of("status", "error", "message", "No git repository found");
                        }
                        // Containment: without this, a caller could read status
                        // (file names) of any git repository on disk.
                        if (!isInOpenProject(repoDir)) {
                            return outsideProjectError(repoDir);
                        }
                        return runGitCommand(repoDir, "git", "status", "--short");
                    }
                });
    }

    private void registerGitDiff(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoDirProp = properties.putObject("repoDir");
        repoDirProp.put("type", "string");
        repoDirProp.put("description", "Repository directory (defaults to project root)");

        ObjectNode targetProp = properties.putObject("target");
        targetProp.put("type", "string");
        targetProp.put("description", "e.g. 'HEAD', a commit hash, or 'staged'");

        mcpTools.registerTool(
                "git_diff",
                "Show git diff (staged, unstaged, or of a specific commit).",
                schema,
                new ToolExecutor<GitDiffInput, Map<String, Object>>(GitDiffInput.class) {
                    @Override
                    public Map<String, Object> execute(GitDiffInput args) throws Exception {
                        String repoDir = isBlank(args.repoDir()) ? findGitRoot() : args.repoDir();
                        if (repoDir == null) {
                            return Map.of("status", "error", "message", "No git repository found");
                        }
                        if (!isInOpenProject(repoDir)) {
                            return outsideProjectError(repoDir);
                        }
                        String target = args.target();
                        if ("staged".equalsIgnoreCase(target)) {
                            return runGitCommand(repoDir, "git", "diff", "--cached");
                        } else if (isBlank(target)) {
                            return runGitCommand(repoDir, "git", "diff");
                        } else if (!SAFE_DIFF_TARGET.matcher(target).matches()) {
                            return Map.of("status", "error", "message", "Invalid diff target: " + target);
                        } else {
                            return runGitCommand(repoDir, "git", "diff", target);
                        }
                    }
                });
    }

    private String findGitRoot() {
        File cwd = new File(System.getProperty("user.dir"));
        File current = cwd;
        while (current != null) {
            File gitDir = new File(current, ".git");
            if (gitDir.exists()) {
                return current.getAbsolutePath();
            }
            current = current.getParentFile();
        }
        return null;
    }

    private Map<String, Object> runGitCommand(String repoDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(repoDir));
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output;
        try (var reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            output = sb.toString();
        }
        boolean finished = proc.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            return Map.of("status", "error", "message", "Git command timed out");
        }
        int exitCode = proc.exitValue();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("exitCode", exitCode);
        result.put("output", output);
        return result;
    }
}
