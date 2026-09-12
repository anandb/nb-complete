package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import github.anandb.netbeans.support.VcsUtils;

import static github.anandb.netbeans.mcp.ProjectPathGuard.isInOpenProject;
import static github.anandb.netbeans.mcp.ProjectPathGuard.outsideProjectError;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Shared helpers for git/hg MCP tools: schema fragments, open-project repo
 * resolution, file-history path checks, and subprocess capture.
 */
final class VcsToolSupport {

    private VcsToolSupport() {}

    record ResolvedRepo(String dir, Map<String, Object> error) {
        boolean ok() {
            return error == null;
        }
    }

    record ResolvedCount(int value, Map<String, Object> error) {
        boolean ok() {
            return error == null;
        }
    }

    record ResolvedPath(String relative, Map<String, Object> error) {
        boolean ok() {
            return error == null;
        }
    }

    record FileHistory(String repoDir, String relative, int maxCount, Map<String, Object> error) {
        boolean ok() {
            return error == null;
        }
    }

    static String findRoot(String markerDir) {
        return VcsUtils.findRoot(ProjectPathGuard.firstOpenProjectRoot(), markerDir);
    }

    static ResolvedRepo resolveRepo(String requestedDir, String markerDir, String notFoundMessage) {
        String repoDir = isBlank(requestedDir) ? findRoot(markerDir) : requestedDir;
        if (repoDir == null) {
            return new ResolvedRepo(null, Map.of("status", "error", "message", notFoundMessage));
        }
        if (!isInOpenProject(repoDir)) {
            return new ResolvedRepo(null, outsideProjectError(repoDir));
        }
        return new ResolvedRepo(repoDir, null);
    }

    static ResolvedCount maxCount(Integer requested, int defaultValue, int min, int max) {
        int value = requested == null ? defaultValue : requested;
        if (value < min || value > max) {
            return new ResolvedCount(0, Map.of("status", "error", "message",
                    "maxCount must be between " + min + " and " + max));
        }
        return new ResolvedCount(value, null);
    }

    static Map<String, Object> invalidSince(String since, Pattern pattern) {
        if (since != null && !pattern.matcher(since).matches()) {
            return Map.of("status", "error", "message", "Invalid since filter: " + since);
        }
        return null;
    }

    static ResolvedPath historyPath(String repoDir, String path) {
        if (isBlank(path)) {
            return new ResolvedPath(null, Map.of("status", "error", "message", "path is required"));
        }
        File file = new File(path);
        String absolute = file.isAbsolute() ? path : new File(repoDir, path).getAbsolutePath();
        if (!isInOpenProject(absolute)) {
            return new ResolvedPath(null, outsideProjectError(absolute));
        }
        String relative = new File(repoDir).toPath()
                .relativize(new File(absolute).toPath()).toString();
        return new ResolvedPath(relative, null);
    }

    static FileHistory prepareFileHistory(String requestedDir, String markerDir, String notFoundMessage,
            String path, Integer maxCount) {
        ResolvedRepo repo = resolveRepo(requestedDir, markerDir, notFoundMessage);
        if (!repo.ok()) {
            return new FileHistory(null, null, 0, repo.error());
        }
        ResolvedPath histPath = historyPath(repo.dir(), path);
        if (!histPath.ok()) {
            return new FileHistory(null, null, 0, histPath.error());
        }
        ResolvedCount count = maxCount(maxCount, 10, 1, 50);
        if (!count.ok()) {
            return new FileHistory(null, null, 0, count.error());
        }
        return new FileHistory(repo.dir(), histPath.relative(), count.value(), null);
    }

    static Map<String, Object> run(String repoDir, String vcsLabel, String... command) throws Exception {
        VcsUtils.CapturedCommand captured = VcsUtils.runCaptured(new File(repoDir), 30, command);
        if (captured.timedOut()) {
            return Map.of("status", "error", "message", vcsLabel + " command timed out");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("exitCode", captured.exitCode());
        result.put("output", captured.output());
        return result;
    }

    static ObjectNode repoDirSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        addRepoDir(schema.putObject("properties"));
        return schema;
    }

    static ObjectNode logSchema(ObjectMapper mapper, String sinceDescription) {
        ObjectNode schema = repoDirSchema(mapper);
        ObjectNode properties = (ObjectNode) schema.get("properties");
        ObjectNode maxCount = properties.putObject("maxCount");
        maxCount.put("type", "integer");
        maxCount.put("description", "Maximum number of commits (default 20, max 1000)");
        ObjectNode since = properties.putObject("since");
        since.put("type", "string");
        since.put("description", sinceDescription);
        return schema;
    }

    static ObjectNode fileHistorySchema(ObjectMapper mapper) {
        ObjectNode schema = repoDirSchema(mapper);
        ObjectNode properties = (ObjectNode) schema.get("properties");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description",
                "Path of the file to inspect, relative to the repository root or absolute");
        ObjectNode maxCount = properties.putObject("maxCount");
        maxCount.put("type", "integer");
        maxCount.put("description", "Maximum number of commits (default 10, max 50)");
        return schema;
    }

    private static void addRepoDir(ObjectNode properties) {
        ObjectNode repoDir = properties.putObject("repoDir");
        repoDir.put("type", "string");
        repoDir.put("description", "Repository directory (defaults to project root)");
    }
}
