package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import github.anandb.netbeans.pattern.GitDiffTargetPattern;
import github.anandb.netbeans.pattern.SinceFilterPattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Registers MCP tools for git operations: status, diff, log, and file history.
 */
public class GitToolProvider {

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
                VcsToolSupport.repoDirSchema().build(),
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
        ObjectNode schema = VcsToolSupport.repoDirSchema()
                .str("target", "e.g. 'HEAD', a commit hash, or 'staged'")
                .build();

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
                        Map<String, Object> targetErr = VcsToolSupport.invalidRevision(target, GitDiffTargetPattern::matches);
                        if (targetErr != null) {
                            return targetErr;
                        }
                        return VcsToolSupport.run(repo.dir(), "Git", "git", "diff", target);
                    }
                });
    }

    private void registerGitLog(McpTools mcpTools) {
        mcpTools.registerTool(
                "git_log",
                "Show the commit history of the repository (hash, date, author, subject per line).",
                VcsToolSupport.logSchema(
                        "Only commits after this date, e.g. '2026-09-01' or '2 weeks ago'").build(),
                new ToolExecutor<GitLogInput, Map<String, Object>>(GitLogInput.class) {
                    @Override
                    public Map<String, Object> execute(GitLogInput args) throws Exception {
                        var log = VcsToolSupport.prepareLog(args.repoDir(), ".git",
                                "No git repository found", args.maxCount(), args.since(),
                                SinceFilterPattern::matches);
                        if (!log.ok()) {
                            return log.error();
                        }
                        List<String> command = new ArrayList<>(List.of("git", "log",
                                "--no-color", "--date=iso-strict",
                                "--pretty=format:%h%x09%ad%x09%an%x09%s",
                                "--max-count=" + log.maxCount()));
                        if (log.since() != null) {
                            command.add("--since=" + log.since());
                        }
                        return VcsToolSupport.run(log.repoDir(), "Git", command.toArray(String[]::new));
                    }
                });
    }

    private void registerFileHistory(McpTools mcpTools) {
        mcpTools.registerTool(
                "file_history",
                "Show the commit history of a single file, including each version's patch.",
                VcsToolSupport.fileHistorySchema().build(),
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
}
