package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.support.MapperSupplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Registers MCP tools for git operations: status, diff, log, and file history.
 */
public class GitToolProvider {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    /** Accepts git revision tokens only (hashes, refs, ranges like a..b,
     *  HEAD~1, ref:path). Rejects option injection — a leading '-' would let
     *  a caller smuggle git flags such as --output into the diff command. */
    private static final Pattern SAFE_DIFF_TARGET = Pattern.compile("^[A-Za-z0-9._/~^:-]+$");

    /** Accepts date filters for git_log (ISO dates or relative forms like
     *  "2 weeks ago"). A leading '-' would smuggle git flags, so it is
     *  rejected; the value is bound as a single --since=<value> token. */
    private static final Pattern SINCE_PATTERN = Pattern.compile("^[A-Za-z0-9 .:/_+~-]+$");

    public void registerTools(McpTools mcpTools) {
        registerGitStatus(mcpTools);
        registerGitDiff(mcpTools);
        registerGitLog(mcpTools);
        registerFileHistory(mcpTools);
    }

    private void registerGitStatus(McpTools mcpTools) {
        mcpTools.registerTool(
                "git_status",
                "Show git status of the repository.",
                VcsToolSupport.repoDirSchema(MAPPER),
                new ToolExecutor<GitStatusInput, Map<String, Object>>(GitStatusInput.class) {
                    @Override
                    public Map<String, Object> execute(GitStatusInput args) throws Exception {
                        var repo = VcsToolSupport.resolveRepo(args.repoDir(), ".git",
                                "No git repository found");
                        if (!repo.ok()) {
                            return repo.error();
                        }
                        return VcsToolSupport.run(repo.dir(), "Git", "git", "status", "--short");
                    }
                });
    }

    private void registerGitDiff(McpTools mcpTools) {
        ObjectNode schema = VcsToolSupport.repoDirSchema(MAPPER);
        ObjectNode targetProp = ((ObjectNode) schema.get("properties")).putObject("target");
        targetProp.put("type", "string");
        targetProp.put("description", "e.g. 'HEAD', a commit hash, or 'staged'");

        mcpTools.registerTool(
                "git_diff",
                "Show git diff (staged, unstaged, or of a specific commit).",
                schema,
                new ToolExecutor<GitDiffInput, Map<String, Object>>(GitDiffInput.class) {
                    @Override
                    public Map<String, Object> execute(GitDiffInput args) throws Exception {
                        var repo = VcsToolSupport.resolveRepo(args.repoDir(), ".git",
                                "No git repository found");
                        if (!repo.ok()) {
                            return repo.error();
                        }
                        String target = args.target();
                        if ("staged".equalsIgnoreCase(target)) {
                            return VcsToolSupport.run(repo.dir(), "Git", "git", "diff", "--cached");
                        }
                        if (isBlank(target)) {
                            return VcsToolSupport.run(repo.dir(), "Git", "git", "diff");
                        }
                        if (!SAFE_DIFF_TARGET.matcher(target).matches()) {
                            return Map.of("status", "error", "message", "Invalid diff target: " + target);
                        }
                        return VcsToolSupport.run(repo.dir(), "Git", "git", "diff", target);
                    }
                });
    }

    private void registerGitLog(McpTools mcpTools) {
        mcpTools.registerTool(
                "git_log",
                "Show the commit history of the repository (hash, date, author, subject per line).",
                VcsToolSupport.logSchema(MAPPER,
                        "Only commits after this date, e.g. '2026-09-01' or '2 weeks ago'"),
                new ToolExecutor<GitLogInput, Map<String, Object>>(GitLogInput.class) {
                    @Override
                    public Map<String, Object> execute(GitLogInput args) throws Exception {
                        var repo = VcsToolSupport.resolveRepo(args.repoDir(), ".git",
                                "No git repository found");
                        if (!repo.ok()) {
                            return repo.error();
                        }
                        var maxCount = VcsToolSupport.maxCount(args.maxCount(), 20, 1, 1000);
                        if (!maxCount.ok()) {
                            return maxCount.error();
                        }
                        Map<String, Object> sinceErr = VcsToolSupport.invalidSince(args.since(), SINCE_PATTERN);
                        if (sinceErr != null) {
                            return sinceErr;
                        }
                        List<String> command = new ArrayList<>(List.of("git", "log",
                                "--no-color", "--date=iso-strict",
                                "--pretty=format:%h%x09%ad%x09%an%x09%s",
                                "--max-count=" + maxCount.value()));
                        if (args.since() != null) {
                            command.add("--since=" + args.since());
                        }
                        return VcsToolSupport.run(repo.dir(), "Git", command.toArray(new String[0]));
                    }
                });
    }

    private void registerFileHistory(McpTools mcpTools) {
        mcpTools.registerTool(
                "file_history",
                "Show the commit history of a single file, including each version's patch.",
                VcsToolSupport.fileHistorySchema(MAPPER),
                new ToolExecutor<FileHistoryInput, Map<String, Object>>(FileHistoryInput.class) {
                    @Override
                    public Map<String, Object> execute(FileHistoryInput args) throws Exception {
                        var hist = VcsToolSupport.prepareFileHistory(args.repoDir(), ".git",
                                "No git repository found", args.path(), args.maxCount());
                        if (!hist.ok()) {
                            return hist.error();
                        }
                        return VcsToolSupport.run(hist.repoDir(), "Git",
                                "git", "log", "--follow", "-p", "--no-color",
                                "--date=iso-strict", "--pretty=format:%h|%ad|%an|%s",
                                "--max-count=" + hist.maxCount(), "--", hist.relative());
                    }
                });
    }

    /**
     * Locates the git repository root by walking up from the first open
     * project's root (never {@code user.dir}, which points at the IDE
     * launcher directory). {@code null} when no project is open or no
     * {@code .git} directory is found above it.
     */
    private String findGitRoot() {
        return VcsToolSupport.findRoot(".git");
    }
}
