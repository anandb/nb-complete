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
 * Registers MCP tools for Mercurial operations: status, diff, log, and file
 * history. Mirrors {@link GitToolProvider}: the default repository root comes
 * from the first open project (walking up for {@code .hg}), every resolved
 * path is checked with the {@link ProjectPathGuard} containment rules, and
 * user-supplied revision/date tokens are validated against safe patterns so
 * option flags cannot be smuggled into the argv.
 */
public class HgToolProvider {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    /** Accepts revision tokens only (hashes, tags, "tip", "."). Rejects option
     *  injection — a leading '-' would let a caller smuggle hg flags such as
     *  --config into the diff command. */
    private static final Pattern SAFE_HG_TARGET = Pattern.compile("^[A-Za-z0-9._/~^:+-]+$");

    /** Accepts date filters for hg_log (ISO dates or relative forms like
     *  "2 weeks ago"). A leading '-' would smuggle hg flags, so it is
     *  rejected; the value is bound as a single --date=>{value} token. */
    private static final Pattern SINCE_PATTERN = Pattern.compile("^[A-Za-z0-9 .:/_+~-]+$");

    /** One-line template shared by hg_log and hg_file_hist (tab-separated). */
    private static final String LOG_TEMPLATE =
            "--template={node|short}\\t{date|isodate}\\t{author|person}\\t{desc|firstline}\\n";

    public void registerTools(McpTools mcpTools) {
        registerHgStatus(mcpTools);
        registerHgDiff(mcpTools);
        registerHgLog(mcpTools);
        registerHgFileHistory(mcpTools);
    }

    private void registerHgStatus(McpTools mcpTools) {
        mcpTools.registerTool(
                "hg_status",
                "Show the Mercurial status of the repository.",
                VcsToolSupport.repoDirSchema(MAPPER),
                new ToolExecutor<HgStatusInput, Map<String, Object>>(HgStatusInput.class) {
                    @Override
                    public Map<String, Object> execute(HgStatusInput args) throws Exception {
                        var repo = VcsToolSupport.resolveRepo(args.repoDir(), ".hg",
                                "No Mercurial repository found");
                        if (!repo.ok()) {
                            return repo.error();
                        }
                        return VcsToolSupport.run(repo.dir(), "Hg", "hg", "status");
                    }
                });
    }

    private void registerHgDiff(McpTools mcpTools) {
        ObjectNode schema = VcsToolSupport.repoDirSchema(MAPPER);
        ObjectNode targetProp = ((ObjectNode) schema.get("properties")).putObject("target");
        targetProp.put("type", "string");
        targetProp.put("description",
                "Revision to diff against ('.', 'tip', a hash, or a tag); blank diffs the working directory");

        mcpTools.registerTool(
                "hg_diff",
                "Show the Mercurial diff of the working directory or of a specific revision.",
                schema,
                new ToolExecutor<HgDiffInput, Map<String, Object>>(HgDiffInput.class) {
                    @Override
                    public Map<String, Object> execute(HgDiffInput args) throws Exception {
                        var repo = VcsToolSupport.resolveRepo(args.repoDir(), ".hg",
                                "No Mercurial repository found");
                        if (!repo.ok()) {
                            return repo.error();
                        }
                        String target = args.target();
                        if (isBlank(target)) {
                            return VcsToolSupport.run(repo.dir(), "Hg", "hg", "diff");
                        }
                        Map<String, Object> targetErr = VcsToolSupport.invalidRevision(target, SAFE_HG_TARGET);
                        if (targetErr != null) {
                            return targetErr;
                        }
                        return VcsToolSupport.run(repo.dir(), "Hg", "hg", "diff", "-r", target);
                    }
                });
    }

    private void registerHgLog(McpTools mcpTools) {
        mcpTools.registerTool(
                "hg_log",
                "Show the commit history of the Mercurial repository (hash, date, author, subject per line).",
                VcsToolSupport.logSchema(MAPPER,
                        "Only commits after this date (bound as >{since}; Mercurial date syntax, e.g. '2026-09-01' or '2w')"),
                new ToolExecutor<HgLogInput, Map<String, Object>>(HgLogInput.class) {
                    @Override
                    public Map<String, Object> execute(HgLogInput args) throws Exception {
                        var repo = VcsToolSupport.resolveRepo(args.repoDir(), ".hg",
                                "No Mercurial repository found");
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
                        List<String> command = new ArrayList<>(List.of("hg", "log",
                                LOG_TEMPLATE, "-l", String.valueOf(maxCount.value())));
                        if (args.since() != null) {
                            command.add("--date=>" + args.since());
                        }
                        return VcsToolSupport.run(repo.dir(), "Hg", command.toArray(new String[0]));
                    }
                });
    }

    private void registerHgFileHistory(McpTools mcpTools) {
        mcpTools.registerTool(
                "hg_file_hist",
                "Show the commit history of a single Mercurial-tracked file, including each version's patch.",
                VcsToolSupport.fileHistorySchema(MAPPER),
                new ToolExecutor<HgFileHistoryInput, Map<String, Object>>(HgFileHistoryInput.class) {
                    @Override
                    public Map<String, Object> execute(HgFileHistoryInput args) throws Exception {
                        var hist = VcsToolSupport.prepareFileHistory(args.repoDir(), ".hg",
                                "No Mercurial repository found", args.path(), args.maxCount());
                        if (!hist.ok()) {
                            return hist.error();
                        }
                        return VcsToolSupport.run(hist.repoDir(), "Hg",
                                "hg", "log", "--follow", "-p",
                                LOG_TEMPLATE, "-l", String.valueOf(hist.maxCount()),
                                "--", hist.relative());
                    }
                });
    }
}
