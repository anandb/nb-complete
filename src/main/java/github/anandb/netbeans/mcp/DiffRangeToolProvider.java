package github.anandb.netbeans.mcp;

import java.io.File;
import java.util.Map;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openide.util.Lookup;

import github.anandb.netbeans.contract.DiffRangeControl;
import github.anandb.netbeans.mcp.input.DiffRangeInput;
import github.anandb.netbeans.pattern.GitDiffTargetPattern;
import github.anandb.netbeans.pattern.HgDiffTargetPattern;
import github.anandb.netbeans.stereotype.McpToolProvider;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.VcsDiffRanges;
import github.anandb.netbeans.support.VcsUtils;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Registers {@code diff_range}: a multi-file diff between any two commits for
 * Git or Mercurial, opened in the same viewer as the stash diff.
 */
@McpToolProvider
public class DiffRangeToolProvider {

    private static final Logger LOG = Logger.from(DiffRangeToolProvider.class);
    private static final int MAX_COMMITS = 1000;

    public void registerTools(McpTools mcpTools) {
        ObjectNode schema = ToolSchema.object()
                .str("repoDir", "Repository directory (defaults to the first open project)")
                .str("fromRevision", "Start revision: a hash, branch, tag, or expression such as "
                        + "'HEAD~10'. Ignored when lastCommits is set.")
                .str("toRevision", "End revision. Omit to include uncommitted working-tree "
                        + "changes; when set, the diff is commit-to-commit only.")
                .integer("lastCommits", "Instead of fromRevision: show the last N commits. "
                        + "The diff starts at the oldest of those N commits (N=5 starts at "
                        + "HEAD~4) and ends at toRevision or the working tree.")
                .build();

        mcpTools.registerTool(
                "diff_range",
                ToolDescriptions.get("diff_range"),
                schema,
                new ToolExecutor<DiffRangeInput, Map<String, Object>>(DiffRangeInput.class) {
                    @Override
                    public Map<String, Object> execute(DiffRangeInput args) throws Exception {
                        DiffRangeControl control = Lookup.getDefault().lookup(DiffRangeControl.class);
                        if (control == null) {
                            return ToolResults.unavailable("DiffRangeControl");
                        }

                        Integer lastCommits = args.lastCommits();
                        if (lastCommits != null && (lastCommits < 1 || lastCommits > MAX_COMMITS)) {
                            return ToolResults.error("lastCommits must be between 1 and " + MAX_COMMITS);
                        }
                        if (isBlank(args.fromRevision()) && lastCommits == null) {
                            return ToolResults.error("fromRevision or lastCommits is required");
                        }

                        File startDir;
                        if (!isBlank(args.repoDir())) {
                            startDir = new File(args.repoDir().trim());
                            if (!startDir.isDirectory()) {
                                return ToolResults.error("Repository directory not found: "
                                        + args.repoDir().trim());
                            }
                            if (!ProjectPathGuard.isInOpenProject(startDir.getAbsolutePath())) {
                                return ProjectPathGuard.outsideProjectError(startDir.getAbsolutePath());
                            }
                        } else {
                            String projectRoot = ProjectPathGuard.firstOpenProjectRoot();
                            if (projectRoot == null) {
                                return ToolResults.error(
                                        "No open project found. Specify repoDir explicitly.");
                            }
                            startDir = new File(projectRoot);
                        }

                        VcsUtils.VcsInfo info = VcsDiffRanges.findRepo(startDir.getAbsolutePath());
                        if (info == null) {
                            return ToolResults.error("Not a Git or Mercurial repository: "
                                    + startDir.getAbsolutePath());
                        }
                        Predicate<String> allowedRevision = info.type() == VcsUtils.VcsType.GIT
                                ? GitDiffTargetPattern::matches
                                : HgDiffTargetPattern::matches;
                        Map<String, Object> invalid = invalidRevision(args.fromRevision(), allowedRevision);
                        if (invalid != null) {
                            return invalid;
                        }
                        invalid = invalidRevision(args.toRevision(), allowedRevision);
                        if (invalid != null) {
                            return invalid;
                        }

                        String from = isBlank(args.fromRevision()) ? null : args.fromRevision().trim();
                        String to = isBlank(args.toRevision()) ? null : args.toRevision().trim();
                        LOG.info("diff_range tool called: repo={0}, from={1}, to={2}, lastCommits={3}",
                                info.rootDir().getAbsolutePath(), from, to, lastCommits);

                        String error = control.openDiffRange(info.rootDir(), from, to, lastCommits);
                        if (error != null) {
                            return ToolResults.error(error);
                        }
                        String message;
                        if (lastCommits != null) {
                            message = "Opened diff for the last " + lastCommits + " commits";
                            if (to == null) {
                                message += " including working tree changes";
                            } else if (!to.equals(VcsDiffRanges.defaultTip(info.type()))) {
                                message += " to " + to;
                            }
                        } else {
                            message = "Opened diff for " + from + ".."
                                    + (to != null ? to : "working tree");
                        }
                        return Map.of("status", "ok", "message", message);
                    }
                });
    }

    private static Map<String, Object> invalidRevision(String revision, Predicate<String> allowed) {
        if (isBlank(revision)) {
            return null;
        }
        return VcsToolSupport.invalidRevision(revision.trim(), allowed);
    }
}
