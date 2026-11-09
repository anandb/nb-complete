package github.anandb.netbeans.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.FileCacheQuery;
import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.pattern.FileNameMatcher;
import github.anandb.netbeans.support.FileAccessGuard;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * MCP tool for finding a file path by name, over the same index as Navigate &gt; Jump to File
 * ({@link FileCacheQuery}, built by {@code FileCacheManager} from the open projects and their VCS
 * ignore rules). The matching rules live in {@link FileNameMatcher}, shared with the dialog, so
 * both searches return the same files for the same query.
 *
 * <p>Results are scoped to the session's working directory by default: the agent works in that
 * directory, and a file elsewhere in the project is usually the wrong hit. {@code scope=projects}
 * widens the search to every open project, a session without a directory falls back to that
 * automatically, and an empty scoped answer is retried across projects before it is reported —
 * a directory the index cannot cover must not read as "no such file".</p>
 */
final class FindFileToolProvider {

    /** The tool returns at most this many matches; the description states it. */
    static final int MAX_RESULTS = 5;

    /**
     * How many candidates are collected before ranking. A broad query stops here instead of
     * sorting the whole index, and the cap is far above {@link #MAX_RESULTS} so file-name hits a
     * user cares about are not crowded out by path matches.
     */
    private static final int SCAN_CAP = 200;

    /** Scope: only the session's working directory. */
    static final String SCOPE_SESSION = "session";
    /** Scope: every open project. */
    static final String SCOPE_PROJECTS = "projects";

    public void registerTools(McpTools mcpTools) {
        ObjectNode schema = ToolSchema.object()
                .str("query", "File name or path fragment; glob is allowed (*Test, **/main/**, *.java)")
                .str("scope", "session (default) or projects")
                .require("query")
                .build();

        mcpTools.registerTool(
                "find_file",
                ToolDescriptions.get("find_file"),
                schema,
                new ToolExecutor<FindFileInput, Map<String, Object>>(FindFileInput.class) {
                    @Override
                    public Map<String, Object> execute(FindFileInput args) {
                        return executeFindFile(args);
                    }
                });
    }

    private Map<String, Object> executeFindFile(FindFileInput args) {
        if (isBlank(args.query())) {
            return Map.of("status", "error", "message", "query is required");
        }
        FileNameMatcher.Query query = FileNameMatcher.compile(args.query());
        if (!query.usable()) {
            return Map.of("status", "error", "message",
                    "query must be at least " + FileNameMatcher.MIN_PLAIN_LENGTH
                            + " characters, or a glob pattern");
        }
        String requestedScope = scope(args.scope());
        if (requestedScope == null) {
            return Map.of("status", "error", "message",
                    "scope must be one of: " + SCOPE_SESSION + ", " + SCOPE_PROJECTS);
        }
        // A session without a directory cannot narrow anything, so it searches the projects.
        String sessionDirectory = SCOPE_PROJECTS.equals(requestedScope) ? null : currentSessionDirectory();
        FileCacheQuery cache = Lookup.getDefault().lookup(FileCacheQuery.class);
        if (cache == null) {
            return Map.of("status", "error", "message", "FileCacheQuery not available", "fatal", true);
        }

        // Put the project into the query itself: without it the matcher runs over every open
        // project's files and discards the out-of-scope hits afterwards.
        String projectName = sessionDirectory == null ? null : cache.getProjectName(sessionDirectory);
        String directory = sessionDirectory;
        Candidates candidates = collect(cache, query, directory, projectName);
        boolean widened = false;
        if (directory != null && candidates.hits().isEmpty()) {
            // A directory the index does not cover (a container path, a folder outside every
            // project) must not answer "no such file" when the file exists elsewhere.
            Candidates acrossProjects = collect(cache, query, null, null);
            if (!acrossProjects.hits().isEmpty()) {
                candidates = acrossProjects;
                directory = null;
                widened = true;
            }
        }
        boolean truncated = candidates.capped() || candidates.hits().size() > MAX_RESULTS;
        List<Map<String, Object>> matches = new ArrayList<>();
        int returned = Math.min(MAX_RESULTS, candidates.hits().size());
        for (int i = 0; i < returned; i++) {
            matches.add(describe(candidates.hits().get(i)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("query", args.query());
        result.put("scope", directory != null ? SCOPE_SESSION : SCOPE_PROJECTS);
        if (directory != null) {
            result.put("directory", directory);
        }
        result.put("count", matches.size());
        result.put("matches", matches);
        if (truncated) {
            result.put("truncated", true);
        }
        List<String> notes = new ArrayList<>();
        if (widened) {
            result.put("widened", true);
            notes.add("Nothing inside " + sessionDirectory + " matched; searched all open projects.");
        }
        if (!cache.isReady()) {
            // A partial answer while the first index build runs; the agent can retry.
            result.put("indexing", true);
            notes.add("The file index is still building; results may be incomplete.");
        }
        if (!notes.isEmpty()) {
            result.put("message", String.join(" ", notes));
        }
        return result;
    }

    /** Blank means the default scope; anything unrecognised returns null. */
    private static String scope(String requested) {
        if (isBlank(requested)) {
            return SCOPE_SESSION;
        }
        String value = requested.trim().toLowerCase(Locale.ROOT);
        if (SCOPE_SESSION.equals(value) || SCOPE_PROJECTS.equals(value)) {
            return value;
        }
        return null;
    }

    private static String currentSessionDirectory() {
        SessionQuery sessions = Lookup.getDefault().lookup(SessionQuery.class);
        String directory = sessions == null ? null : sessions.getCurrentSessionDirectory();
        return isBlank(directory) ? null : directory;
    }

    /**
     * Matches, ranked: file-name hits first, then by project and relative path. When
     * {@code projectName} is known only that project's entries are matched; {@code directory}
     * still narrows the result to a subdirectory of it.
     */
    private static Candidates collect(FileCacheQuery cache, FileNameMatcher.Query query,
            String directory, String projectName) {
        Collection<FileCacheQuery.CachedFile> source = projectName == null
                ? cache.getAllFiles() : cache.getFiles(projectName);
        List<Hit> hits = new ArrayList<>();
        for (FileCacheQuery.CachedFile file : source) {
            if (hits.size() >= SCAN_CAP) {
                return new Candidates(rank(hits), true);
            }
            String name = file.fileObject().getNameExt();
            if (!query.matches(name, file.relativePath())) {
                continue;
            }
            String path = absolutePath(file.fileObject());
            // A scoped search cannot vouch for a file with no java.io.File behind it.
            if (directory != null && (path == null || !FileAccessGuard.isInsideProject(directory, path))) {
                continue;
            }
            hits.add(new Hit(file, path, query.namePrefix(name)));
        }
        return new Candidates(rank(hits), false);
    }

    /** Ranking the candidates, rather than trusting index order, keeps repeated calls stable. */
    private static List<Hit> rank(List<Hit> hits) {
        hits.sort(Comparator.comparing((Hit hit) -> !hit.namePrefix())
                .thenComparing(hit -> hit.file().projectName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(hit -> hit.file().relativePath(), String.CASE_INSENSITIVE_ORDER));
        return hits;
    }

    private static Map<String, Object> describe(Hit hit) {
        FileCacheQuery.CachedFile file = hit.file();
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("name", file.fileObject().getNameExt());
        match.put("project", file.projectName());
        match.put("relativePath", file.relativePath());
        if (hit.path() != null) {
            match.put("path", hit.path());
        }
        return match;
    }

    private static String absolutePath(FileObject fileObject) {
        File file = FileUtil.toFile(fileObject);
        return file == null ? null : file.getAbsolutePath();
    }

    /**
     * One candidate: its cache entry, its absolute path (null for a virtual file) and whether its
     * file name — not just its path — starts with the query.
     */
    private record Hit(FileCacheQuery.CachedFile file, String path, boolean namePrefix) {
    }

    /** The ranked matches, and whether the scan stopped at {@link #SCAN_CAP}. */
    private record Candidates(List<Hit> hits, boolean capped) {
    }
}