package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static github.anandb.netbeans.mcp.ProjectPathGuard.isInOpenProject;
import static github.anandb.netbeans.mcp.ProjectPathGuard.outsideProjectError;
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

    private static final Logger LOG = Logger.from(HgToolProvider.class);
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
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoDirProp = properties.putObject("repoDir");
        repoDirProp.put("type", "string");
        repoDirProp.put("description", "Repository directory (defaults to project root)");

        mcpTools.registerTool(
                "hg_status",
                "Show the Mercurial status of the repository.",
                schema,
                new ToolExecutor<HgStatusInput, Map<String, Object>>(HgStatusInput.class) {
                    @Override
                    public Map<String, Object> execute(HgStatusInput args) throws Exception {
                        String repoDir = isBlank(args.repoDir()) ? findHgRoot() : args.repoDir();
                        if (repoDir == null) {
                            return Map.of("status", "error", "message", "No Mercurial repository found");
                        }
                        // Containment: without this, a caller could read status
                        // (file names) of any Mercurial repository on disk.
                        if (!isInOpenProject(repoDir)) {
                            return outsideProjectError(repoDir);
                        }
                        return runHgCommand(repoDir, "hg", "status");
                    }
                });
    }

    private void registerHgDiff(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoDirProp = properties.putObject("repoDir");
        repoDirProp.put("type", "string");
        repoDirProp.put("description", "Repository directory (defaults to project root)");

        ObjectNode targetProp = properties.putObject("target");
        targetProp.put("type", "string");
        targetProp.put("description", "Revision to diff against ('.', 'tip', a hash, or a tag); blank diffs the working directory");

        mcpTools.registerTool(
                "hg_diff",
                "Show the Mercurial diff of the working directory or of a specific revision.",
                schema,
                new ToolExecutor<HgDiffInput, Map<String, Object>>(HgDiffInput.class) {
                    @Override
                    public Map<String, Object> execute(HgDiffInput args) throws Exception {
                        String repoDir = isBlank(args.repoDir()) ? findHgRoot() : args.repoDir();
                        if (repoDir == null) {
                            return Map.of("status", "error", "message", "No Mercurial repository found");
                        }
                        if (!isInOpenProject(repoDir)) {
                            return outsideProjectError(repoDir);
                        }
                        String target = args.target();
                        if (isBlank(target)) {
                            return runHgCommand(repoDir, "hg", "diff");
                        } else if (!SAFE_HG_TARGET.matcher(target).matches()) {
                            return Map.of("status", "error", "message", "Invalid diff target: " + target);
                        } else {
                            return runHgCommand(repoDir, "hg", "diff", "-r", target);
                        }
                    }
                });
    }

    private void registerHgLog(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoDirProp = properties.putObject("repoDir");
        repoDirProp.put("type", "string");
        repoDirProp.put("description", "Repository directory (defaults to project root)");

        ObjectNode maxCountProp = properties.putObject("maxCount");
        maxCountProp.put("type", "integer");
        maxCountProp.put("description", "Maximum number of commits (default 20, max 1000)");

        ObjectNode sinceProp = properties.putObject("since");
        sinceProp.put("type", "string");
        sinceProp.put("description", "Only commits after this date (bound as >{since}; Mercurial date syntax, e.g. '2026-09-01' or '2w')");

        mcpTools.registerTool(
                "hg_log",
                "Show the commit history of the Mercurial repository (hash, date, author, subject per line).",
                schema,
                new ToolExecutor<HgLogInput, Map<String, Object>>(HgLogInput.class) {
                    @Override
                    public Map<String, Object> execute(HgLogInput args) throws Exception {
                        String repoDir = isBlank(args.repoDir()) ? findHgRoot() : args.repoDir();
                        if (repoDir == null) {
                            return Map.of("status", "error", "message", "No Mercurial repository found");
                        }
                        if (!isInOpenProject(repoDir)) {
                            return outsideProjectError(repoDir);
                        }
                        int maxCount = args.maxCount() == null ? 20 : args.maxCount();
                        if (maxCount < 1 || maxCount > 1000) {
                            return Map.of("status", "error", "message",
                                    "maxCount must be between 1 and 1000");
                        }
                        if (args.since() != null && !SINCE_PATTERN.matcher(args.since()).matches()) {
                            return Map.of("status", "error", "message",
                                    "Invalid since filter: " + args.since());
                        }
                        List<String> command = new ArrayList<>(List.of("hg", "log",
                                LOG_TEMPLATE, "-l", String.valueOf(maxCount)));
                        if (args.since() != null) {
                            command.add("--date=>" + args.since());
                        }
                        return runHgCommand(repoDir, command.toArray(new String[0]));
                    }
                });
    }

    private void registerHgFileHistory(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoDirProp = properties.putObject("repoDir");
        repoDirProp.put("type", "string");
        repoDirProp.put("description", "Repository directory (defaults to project root)");

        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "Path of the file to inspect, relative to the repository root or absolute");

        ObjectNode maxCountProp = properties.putObject("maxCount");
        maxCountProp.put("type", "integer");
        maxCountProp.put("description", "Maximum number of commits (default 10, max 50)");

        mcpTools.registerTool(
                "hg_file_hist",
                "Show the commit history of a single Mercurial-tracked file, including each version's patch.",
                schema,
                new ToolExecutor<HgFileHistoryInput, Map<String, Object>>(HgFileHistoryInput.class) {
                    @Override
                    public Map<String, Object> execute(HgFileHistoryInput args) throws Exception {
                        String repoDir = isBlank(args.repoDir()) ? findHgRoot() : args.repoDir();
                        if (repoDir == null) {
                            return Map.of("status", "error", "message", "No Mercurial repository found");
                        }
                        if (!isInOpenProject(repoDir)) {
                            return outsideProjectError(repoDir);
                        }
                        String path = args.path();
                        if (isBlank(path)) {
                            return Map.of("status", "error", "message", "path is required");
                        }
                        File file = new File(path);
                        String absolute = file.isAbsolute() ? path
                                : new File(repoDir, path).getAbsolutePath();
                        if (!isInOpenProject(absolute)) {
                            return outsideProjectError(absolute);
                        }
                        int maxCount = args.maxCount() == null ? 10 : args.maxCount();
                        if (maxCount < 1 || maxCount > 50) {
                            return Map.of("status", "error", "message",
                                    "maxCount must be between 1 and 50");
                        }
                        // Pass the repo-relative path to hg so --follow works
                        // even when the caller supplied an absolute path.
                        String relative = new File(repoDir).toPath()
                                .relativize(new File(absolute).toPath()).toString();
                        return runHgCommand(repoDir, "hg", "log", "--follow", "-p",
                                LOG_TEMPLATE, "-l", String.valueOf(maxCount), "--", relative);
                    }
                });
    }

    /**
     * Locates the Mercurial repository root by walking up from the first open
     * project's root (never {@code user.dir}, which points at the IDE
     * launcher directory). {@code null} when no project is open or no
     * {@code .hg} directory is found above it.
     */
    private String findHgRoot() {
        String projectRoot = ProjectPathGuard.firstOpenProjectRoot();
        File current = projectRoot == null ? null : new File(projectRoot);
        while (current != null) {
            File hgDir = new File(current, ".hg");
            if (hgDir.exists()) {
                return current.getAbsolutePath();
            }
            current = current.getParentFile();
        }
        return null;
    }

    private Map<String, Object> runHgCommand(String repoDir, String... command) throws Exception {
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
            return Map.of("status", "error", "message", "Hg command timed out");
        }
        int exitCode = proc.exitValue();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("exitCode", exitCode);
        result.put("output", output);
        return result;
    }
}
