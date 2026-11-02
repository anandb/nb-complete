package github.anandb.netbeans.mcp;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

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

    record LogRequest(String repoDir, int maxCount, String since, Map<String, Object> error) {
        boolean ok() {
            return error == null;
        }
    }

    static String findRoot(String markerDir) {
        return VcsUtils.findRoot(ProjectPathGuard.firstOpenProjectRoot(), markerDir);
    }

    static ResolvedRepo resolveRepo(String requestedDir, String markerDir, String notFoundMessage) {
        if (isBlank(requestedDir)) {
            String projectRoot = ProjectPathGuard.firstOpenProjectRoot();
            if (projectRoot == null || VcsUtils.findRoot(projectRoot, markerDir) == null) {
                return new ResolvedRepo(null, Map.of("status", "error", "message", notFoundMessage));
            }
            // git/hg accept a subdirectory of the VCS root. Default cwd is the
            // open project, not the walked-up repo root, so a nested NetBeans
            // project is not rejected as "outside the open projects".
            return new ResolvedRepo(projectRoot, null);
        }
        if (!isInOpenProject(requestedDir)) {
            return new ResolvedRepo(null, outsideProjectError(requestedDir));
        }
        if (VcsUtils.findRoot(requestedDir, markerDir) == null) {
            return new ResolvedRepo(null, Map.of("status", "error", "message", notFoundMessage));
        }
        return new ResolvedRepo(requestedDir, null);
    }

    static ResolvedCount maxCount(Integer requested, int defaultValue, int min, int max) {
        int value = requested == null ? defaultValue : requested;
        if (value < min || value > max) {
            return new ResolvedCount(0, Map.of("status", "error", "message",
                    "maxCount must be between " + min + " and " + max));
        }
        return new ResolvedCount(value, null);
    }

    static Map<String, Object> invalidSince(String since, Predicate<String> allowed) {
        if (since != null && !isSafeToken(since, allowed)) {
            return Map.of("status", "error", "message", "Invalid since filter: " + since);
        }
        return null;
    }

    static Map<String, Object> invalidRevision(String target, Predicate<String> allowed) {
        if (!isSafeToken(target, allowed)) {
            return Map.of("status", "error", "message", "Invalid diff target: " + target);
        }
        return null;
    }

    /** Hyphens may appear inside a token (ISO dates, HEAD~1-style). A leading
     *  '-' is an option flag and must not reach argv. */
    private static boolean isSafeToken(String value, Predicate<String> allowed) {
        return !value.startsWith("-") && allowed.test(value);
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
        try {
            String canonicalRepo = new File(repoDir).getCanonicalPath();
            String canonicalFile = new File(absolute).getCanonicalPath();
            String relative = Path.of(canonicalRepo)
                    .relativize(Path.of(canonicalFile)).toString();
            return new ResolvedPath(relative, null);
        } catch (Exception e) {
            return new ResolvedPath(null, Map.of("status", "error", "message",
                    "Could not resolve path: " + path));
        }
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

    /** Resolves a log tool's repo, max-count and since filter, so the git and hg
     *  registrations differ only in their argv. */
    static LogRequest prepareLog(String requestedDir, String markerDir, String notFoundMessage,
            Integer requestedMaxCount, String since, Predicate<String> sinceAllowed) {
        ResolvedRepo repo = resolveRepo(requestedDir, markerDir, notFoundMessage);
        if (!repo.ok()) {
            return new LogRequest(null, 0, since, repo.error());
        }
        ResolvedCount count = maxCount(requestedMaxCount, 20, 1, 1000);
        if (!count.ok()) {
            return new LogRequest(repo.dir(), 0, since, count.error());
        }
        Map<String, Object> sinceError = invalidSince(since, sinceAllowed);
        if (sinceError != null) {
            return new LogRequest(repo.dir(), count.value(), since, sinceError);
        }
        return new LogRequest(repo.dir(), count.value(), since, null);
    }

    static Map<String, Object> run(String repoDir, String vcsLabel, String... command) throws Exception {
        VcsUtils.CapturedCommand captured = VcsUtils.runCaptured(new File(repoDir), 30, command);
        if (captured.timedOut()) {
            return Map.of("status", "error", "message", vcsLabel + " command timed out", "fatal", true);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("exitCode", captured.exitCode());
        result.put("output", captured.output());
        if (captured.truncated()) {
            result.put("truncated", true);
        }
        if (captured.exitCode() != 0) {
            result.put("status", "error");
            result.put("message", vcsLabel + " command failed");
            result.put("fatal", true);
            return result;
        }
        result.put("status", "ok");
        return result;
    }

    static ToolSchema repoDirSchema() {
        return ToolSchema.object()
                .str("repoDir", "Repository directory (defaults to project root)");
    }

    static ToolSchema logSchema(String sinceDescription) {
        return repoDirSchema()
                .integer("maxCount", "Maximum number of commits (default 20, max 1000)")
                .str("since", sinceDescription);
    }

    static ToolSchema fileHistorySchema() {
        return repoDirSchema()
                .str("path", "Path of the file to inspect, relative to the repository root or absolute")
                .integer("maxCount", "Maximum number of commits (default 10, max 50)");
    }
}
