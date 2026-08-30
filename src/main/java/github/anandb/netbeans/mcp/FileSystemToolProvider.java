package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;

import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import org.openide.util.Lookup;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.netbeans.api.project.Project;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Registers MCP tools for file system operations: read, write, replace,
 * insert, search, list directory, run command, and apply unified diff patches.
 */
public class FileSystemToolProvider {

    private static final Logger LOG = Logger.from(FileSystemToolProvider.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    public void registerTools(McpTools mcpTools) {
        registerReadFile(mcpTools);
        registerWriteToFile(mcpTools);
        registerReplaceLines(mcpTools);
        registerInsertInFile(mcpTools);
        registerSearchProject(mcpTools);
        registerListDirectory(mcpTools);
        registerRunCommand(mcpTools);
        registerApplyPatch(mcpTools);
    }

    private void registerReadFile(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode filePathProp = properties.putObject("filePath");
        filePathProp.put("type", "string");
        filePathProp.put("description", "Absolute path to file");

        ObjectNode startLineProp = properties.putObject("startLine");
        startLineProp.put("type", "integer");
        startLineProp.put("description", "Start line (1-indexed, inclusive)");

        ObjectNode endLineProp = properties.putObject("endLine");
        endLineProp.put("type", "integer");
        endLineProp.put("description", "End line (1-indexed, inclusive)");

        ArrayNode required = schema.putArray("required");
        required.add("filePath");

        mcpTools.registerTool(
                "read_file",
                "Read content of a file. Returns file contents as text.",
                schema,
                new ToolExecutor<ReadFileInput, Map<String, Object>>(ReadFileInput.class) {
                    @Override
                    public Map<String, Object> execute(ReadFileInput args) throws Exception {
                        if (isBlank(args.filePath())) {
                            return Map.of("status", "error", "message", "filePath is required");
                        }
                        File file = new File(args.filePath());
                        if (!file.exists()) {
                            return Map.of("status", "error", "message", "File not found: " + args.filePath());
                        }
                        if (!file.isFile()) {
                            return Map.of("status", "error", "message", "Not a file: " + args.filePath());
                        }
                        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                        int start = args.startLine() != null ? args.startLine() : 1;
                        int end = args.endLine() != null ? args.endLine() : lines.size();
                        start = Math.max(1, start);
                        end = Math.min(lines.size(), end);
                        StringBuilder sb = new StringBuilder();
                        for (int i = start - 1; i < end; i++) {
                            sb.append(lines.get(i));
                            if (i < end - 1) sb.append("\n");
                        }
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "ok");
                        result.put("content", sb.toString());
                        result.put("totalLines", lines.size());
                        result.put("linesReturned", end - start + 1);
                        return result;
                    }
                });
    }

    private void registerWriteToFile(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode filePathProp = properties.putObject("filePath");
        filePathProp.put("type", "string");
        filePathProp.put("description", "Absolute path to file");

        ObjectNode contentProp = properties.putObject("content");
        contentProp.put("type", "string");
        contentProp.put("description", "Content to write");

        ArrayNode required = schema.putArray("required");
        required.add("filePath");
        required.add("content");

        mcpTools.registerTool(
                "write_to_file",
                "Write content to a file (creates or overwrites).",
                schema,
                new ToolExecutor<WriteToFileInput, Map<String, Object>>(WriteToFileInput.class) {
                    @Override
                    public Map<String, Object> execute(WriteToFileInput args) throws Exception {
                        if (isBlank(args.filePath())) {
                            return Map.of("status", "error", "message", "filePath is required");
                        }
                        if (args.content() == null) {
                            return Map.of("status", "error", "message", "content is required");
                        }
                        File file = new File(args.filePath());
                        File parent = file.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        Files.writeString(file.toPath(), args.content(), StandardCharsets.UTF_8);
                        return Map.of("status", "ok", "bytesWritten", args.content().length());
                    }
                });
    }

    /**
     * Validates the file path and reads all lines. Returns {@code null} when
     * {@code filePath} is blank; throws an {@link IOException} when the file
     * does not exist.
     */
    private List<String> readLinesFromPath(String filePath) throws IOException {
        if (isBlank(filePath)) {
            return null;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }
        return new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }

    private void registerReplaceLines(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode filePathProp = properties.putObject("filePath");
        filePathProp.put("type", "string");
        filePathProp.put("description", "Absolute path to file");

        ObjectNode startLineProp = properties.putObject("startLine");
        startLineProp.put("type", "integer");
        startLineProp.put("description", "Start line to replace (1-indexed, inclusive)");

        ObjectNode endLineProp = properties.putObject("endLine");
        endLineProp.put("type", "integer");
        endLineProp.put("description", "End line to replace (1-indexed, inclusive)");

        ObjectNode contentProp = properties.putObject("content");
        contentProp.put("type", "string");
        contentProp.put("description", "Replacement content for lines startLine-endLine");

        ArrayNode required = schema.putArray("required");
        required.add("filePath");
        required.add("startLine");
        required.add("endLine");
        required.add("content");

        mcpTools.registerTool(
                "replace_lines",
                "Replace specific lines in a file with new content.",
                schema,
                new ToolExecutor<ReplaceLinesInput, Map<String, Object>>(ReplaceLinesInput.class) {
                    @Override
                    public Map<String, Object> execute(ReplaceLinesInput args) throws Exception {
                        List<String> lines = readLinesFromPath(args.filePath());
                        if (lines == null) {
                            return Map.of("status", "error", "message", "filePath is required");
                        }
                        File file = new File(args.filePath());
                        int start = Math.max(1, args.startLine());
                        int end = Math.min(lines.size(), args.endLine());
                        if (start > end) {
                            return Map.of("status", "error", "message", "startLine > endLine");
                        }
                        String[] replacementLines = args.content().split("\n", -1);
                        lines.subList(start - 1, end).clear();
                        for (int i = replacementLines.length - 1; i >= 0; i--) {
                            lines.add(start - 1, replacementLines[i]);
                        }
                        Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
                        return Map.of("status", "ok", "linesReplaced", end - start + 1);
                    }
                });
    }

    private void registerInsertInFile(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode filePathProp = properties.putObject("filePath");
        filePathProp.put("type", "string");
        filePathProp.put("description", "Absolute path to file");

        ObjectNode lineProp = properties.putObject("line");
        lineProp.put("type", "integer");
        lineProp.put("description", "Line number to insert before");

        ObjectNode contentProp = properties.putObject("content");
        contentProp.put("type", "string");
        contentProp.put("description", "Content to insert");

        ArrayNode required = schema.putArray("required");
        required.add("filePath");
        required.add("line");
        required.add("content");

        mcpTools.registerTool(
                "insert_in_file",
                "Insert content at a specific line in a file.",
                schema,
                new ToolExecutor<InsertInFileInput, Map<String, Object>>(InsertInFileInput.class) {
                    @Override
                    public Map<String, Object> execute(InsertInFileInput args) throws Exception {
                        List<String> lines = readLinesFromPath(args.filePath());
                        if (lines == null) {
                            return Map.of("status", "error", "message", "filePath is required");
                        }
                        File file = new File(args.filePath());
                        int line = Math.max(1, Math.min(lines.size() + 1, args.line()));
                        String[] insertionLines = args.content().split("\n", -1);
                        for (int i = 0; i < insertionLines.length; i++) {
                            lines.add(line - 1 + i, insertionLines[i]);
                        }
                        Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
                        return Map.of("status", "ok", "linesInserted", insertionLines.length);
                    }
                });
    }

    private void registerSearchProject(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode queryProp = properties.putObject("query");
        queryProp.put("type", "string");
        queryProp.put("description", "Search pattern (regex supported)");

        ObjectNode filePatternProp = properties.putObject("filePattern");
        filePatternProp.put("type", "string");
        filePatternProp.put("description", "Glob filter, e.g. '*.java'");

        ObjectNode directoryProp = properties.putObject("directory");
        directoryProp.put("type", "string");
        directoryProp.put("description", "Subdirectory to restrict search");

        ArrayNode required = schema.putArray("required");
        required.add("query");

        mcpTools.registerTool(
                "search_project",
                "Search for a text pattern or regex in project files. Returns matching file paths and line numbers.",
                schema,
                new ToolExecutor<SearchProjectInput, Map<String, Object>>(SearchProjectInput.class) {
                    @Override
                    public Map<String, Object> execute(SearchProjectInput args) throws Exception {
                        if (isBlank(args.query())) {
                            return Map.of("status", "error", "message", "query is required");
                        }
                        Pattern pattern;
                        try {
                            pattern = Pattern.compile(args.query(), Pattern.CASE_INSENSITIVE);
                        } catch (PatternSyntaxException e) {
                            return Map.of("status", "error", "message", "Invalid regex: " + e.getMessage());
                        }
                        ProjectQuery pq = Lookup.getDefault().lookup(ProjectQuery.class);
                        Project[] projects = pq == null ? new Project[0] : pq.getAllOpenProjects();
                        if (projects.length == 0) {
                            return Map.of("status", "error", "message", "No open projects");
                        }
                        String searchDir = args.directory();
                        String fileGlob = args.filePattern();
                        List<Map<String, Object>> matches = new ArrayList<>();
                        for (Project p : projects) {
                            File projectDirFile = org.openide.filesystems.FileUtil.toFile(p.getProjectDirectory());
                            if (projectDirFile == null) continue;
                            Path projectDir = projectDirFile.toPath();
                            Path searchRoot = searchDir != null ? projectDir.resolve(searchDir) : projectDir;
                            if (!Files.isDirectory(searchRoot)) continue;
                            Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                    if (fileGlob != null && !fileGlob.isEmpty()) {
                                        String fileName = file.getFileName().toString();
                                        if (!matchGlob(fileName, fileGlob)) {
                                            return FileVisitResult.CONTINUE;
                                        }
                                    }
                                    try {
                                        List<String> fileLines = Files.readAllLines(file, StandardCharsets.UTF_8);
                                        for (int i = 0; i < fileLines.size(); i++) {
                                            if (pattern.matcher(fileLines.get(i)).find()) {
                                                Map<String, Object> match = new HashMap<>();
                                                match.put("file", file.toString());
                                                match.put("line", i + 1);
                                                match.put("content", fileLines.get(i).trim());
                                                matches.add(match);
                                                if (matches.size() >= 200) {
                                                    return FileVisitResult.TERMINATE;
                                                }
                                            }
                                        }
                                    } catch (IOException e) {
                                        LOG.fine("Error reading file: {0}", e.getMessage());
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                            if (matches.size() >= 200) break;
                        }
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "ok");
                        result.put("matches", matches);
                        result.put("totalMatches", matches.size());
                        return result;
                    }

                    private boolean matchGlob(String fileName, String glob) {
                        String regex = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
                        return fileName.matches(regex);
                    }
                });
    }

    private void registerListDirectory(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode dirPathProp = properties.putObject("dirPath");
        dirPathProp.put("type", "string");
        dirPathProp.put("description", "Absolute path to directory");

        ObjectNode recursiveProp = properties.putObject("recursive");
        recursiveProp.put("type", "boolean");
        recursiveProp.put("description", "Whether to list recursively");
        recursiveProp.put("default", false);

        ArrayNode required = schema.putArray("required");
        required.add("dirPath");

        mcpTools.registerTool(
                "list_directory",
                "List files and subdirectories in a directory.",
                schema,
                new ToolExecutor<ListDirectoryInput, Map<String, Object>>(ListDirectoryInput.class) {
                    @Override
                    public Map<String, Object> execute(ListDirectoryInput args) throws Exception {
                        if (isBlank(args.dirPath())) {
                            return Map.of("status", "error", "message", "dirPath is required");
                        }
                        File dir = new File(args.dirPath());
                        if (!dir.exists()) {
                            return Map.of("status", "error", "message", "Directory not found: " + args.dirPath());
                        }
                        if (!dir.isDirectory()) {
                            return Map.of("status", "error", "message", "Not a directory: " + args.dirPath());
                        }
                        boolean recursive = args.recursive() != null && args.recursive();
                        List<Map<String, Object>> entries = new ArrayList<>();
                        if (recursive) {
                            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                    Map<String, Object> entry = new HashMap<>();
                                    entry.put("path", file.toString());
                                    entry.put("type", "file");
                                    entry.put("size", attrs.size());
                                    entries.add(entry);
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                    Map<String, Object> entry = new HashMap<>();
                                    entry.put("path", dir.toString());
                                    entry.put("type", "directory");
                                    entries.add(entry);
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } else {
                            File[] files = dir.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    Map<String, Object> entry = new HashMap<>();
                                    entry.put("path", f.getAbsolutePath());
                                    entry.put("type", f.isDirectory() ? "directory" : "file");
                                    entry.put("size", f.length());
                                    entries.add(entry);
                                }
                            }
                        }
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "ok");
                        result.put("entries", entries);
                        result.put("totalEntries", entries.size());
                        return result;
                    }
                });
    }

    private void registerRunCommand(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode commandProp = properties.putObject("command");
        commandProp.put("type", "string");
        commandProp.put("description", "Shell command to execute");

        ObjectNode workDirProp = properties.putObject("workingDirectory");
        workDirProp.put("type", "string");
        workDirProp.put("description", "Working directory for the command");

        ObjectNode timeoutProp = properties.putObject("timeoutSeconds");
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "Timeout in seconds");
        timeoutProp.put("default", 120);

        ArrayNode required = schema.putArray("required");
        required.add("command");

        mcpTools.registerTool(
                "run_command",
                "Execute a shell command. Returns stdout, stderr, and exit code.",
                schema,
                new ToolExecutor<RunCommandInput, Map<String, Object>>(RunCommandInput.class) {
                    @Override
                    public Map<String, Object> execute(RunCommandInput args) throws Exception {
                        if (isBlank(args.command())) {
                            return Map.of("status", "error", "message", "command is required");
                        }
                        String workDir = args.workingDirectory();
                        if (isBlank(workDir)) {
                            workDir = System.getProperty("user.dir");
                        }
                        File dir = new File(workDir);
                        if (!dir.exists() || !dir.isDirectory()) {
                            return Map.of("status", "error", "message", "Invalid working directory: " + workDir);
                        }
                        int timeout = args.timeoutSeconds() != null ? args.timeoutSeconds() : 120;
                        ProcessBuilder pb = new ProcessBuilder("bash", "-c", args.command());
                        pb.directory(dir);
                        pb.redirectErrorStream(true);
                        Process proc = pb.start();
                        StringBuilder output = new StringBuilder();
                        try (var reader = new BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (output.length() > 0) output.append("\n");
                                output.append(line);
                            }
                        }
                        boolean finished = proc.waitFor(timeout, TimeUnit.SECONDS);
                        if (!finished) {
                            proc.destroyForcibly();
                            return Map.of("status", "error", "message", "Command timed out after " + timeout + "s");
                        }
                        int exitCode = proc.exitValue();
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "ok");
                        result.put("exitCode", exitCode);
                        result.put("output", output.toString());
                        return result;
                    }
                });
    }

    /** Removes trailing spaces and tabs from a line. */
    private static String stripTrailingWhitespace(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t')) {
            end--;
        }
        return s.substring(0, end);
    }

    private void registerApplyPatch(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode filePathProp = properties.putObject("filePath");
        filePathProp.put("type", "string");
        filePathProp.put("description", "Absolute path to the file to patch");

        ObjectNode patchProp = properties.putObject("patch");
        patchProp.put("type", "string");
        patchProp.put("description", "Unified diff content to apply");

        ObjectNode dryRunProp = properties.putObject("dryRun");
        dryRunProp.put("type", "boolean");
        dryRunProp.put("description", "If true, apply to a temp copy and return the temp file; the original is untouched");
        dryRunProp.put("default", false);

        ArrayNode required = schema.putArray("required");
        required.add("filePath");
        required.add("patch");

        mcpTools.registerTool(
                "apply_patch",
                "Apply a unified diff patch to a single file. Trailing whitespace is trimmed on patched lines so they don't cause conflicts.",
                schema,
                new ToolExecutor<ApplyPatchInput, Map<String, Object>>(ApplyPatchInput.class) {
                    @Override
                    public Map<String, Object> execute(ApplyPatchInput args) throws Exception {
                        if (isBlank(args.filePath())) {
                            return Map.of("status", "error", "message", "filePath is required");
                        }
                        if (isBlank(args.patch())) {
                            return Map.of("status", "error", "message", "patch is required");
                        }
                        File file = new File(args.filePath());
                        if (!file.exists()) {
                            return Map.of("status", "error", "message", "File not found: " + args.filePath());
                        }
                        if (!file.isFile()) {
                            return Map.of("status", "error", "message", "Not a file: " + args.filePath());
                        }

                        List<String> fileLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                        List<String> trimmedFileLines = new ArrayList<>(fileLines.size());
                        for (String line : fileLines) {
                            trimmedFileLines.add(stripTrailingWhitespace(line));
                        }

                        List<String> patchLines = new ArrayList<>(List.of(args.patch().split("\n", -1)));
                        // A trailing newline yields a final empty element that the
                        // unified-diff parser would treat as an extra context line;
                        // drop it so hunk sizes line up with the file.
                        if (!patchLines.isEmpty() && patchLines.get(patchLines.size() - 1).isEmpty()) {
                            patchLines.remove(patchLines.size() - 1);
                        }

                        Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
                        // Trim trailing whitespace on the lines to be patched so they
                        // match the trimmed file lines and don't cause conflicts.
                        for (AbstractDelta<String> delta : patch.getDeltas()) {
                            List<String> source = delta.getSource().getLines();
                            delta.getSource().setLines(trimAll(source));
                            List<String> target = delta.getTarget().getLines();
                            delta.getTarget().setLines(trimAll(target));
                        }

                        List<String> result;
                        try {
                            result = patch.applyTo(trimmedFileLines);
                        } catch (PatchFailedException e) {
                            return Map.of("status", "error", "message",
                                    "Patch does not apply: " + e.getMessage());
                        }

                        boolean dryRun = args.dryRun() != null && args.dryRun();
                        Path targetPath = file.toPath();
                        if (dryRun) {
                            Path parent = targetPath.getParent();
                            Path dir = parent != null ? parent : Path.of(System.getProperty("java.io.tmpdir"));
                            targetPath = Files.createTempFile(dir,
                                    targetPath.getFileName().toString() + "-", ".dryrun");
                        }
                        Files.writeString(targetPath, String.join("\n", result), StandardCharsets.UTF_8);

                        Map<String, Object> out = new HashMap<>();
                        out.put("status", "ok");
                        out.put("filePath", targetPath.toString());
                        out.put("content", String.join("\n", result));
                        out.put("hunksApplied", patch.getDeltas().size());
                        out.put("dryRun", dryRun);
                        return out;
                    }

                    private List<String> trimAll(List<String> lines) {
                        List<String> trimmed = new ArrayList<>(lines.size());
                        for (String line : lines) {
                            trimmed.add(stripTrailingWhitespace(line));
                        }
                        return trimmed;
                    }
                });
    }
}