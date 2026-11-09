package github.anandb.netbeans.mcp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.lang.model.element.TypeElement;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.project.Project;

import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * MCP tool provider for finding Java types by name, mirroring Navigate > Go to
 * Type. The GUI action is internal to the jumpto module and modal, so this tool
 * searches the public {@link ClassIndex} API instead and returns paths.
 */
final class FindTypeToolProvider {

    private static final Logger LOG = Logger.from(FindTypeToolProvider.class);

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    public void registerTools(McpTools mcpTools) {
        ObjectNode schema = ToolSchema.object()
                .str("query", "Type name. camel (default) matches camel-case initials: NPE finds NullPointerException")
                .str("kind", "camel (default), prefix, exact (whole simple name) or regexp (case-insensitive)")
                .str("scope", "all (default: sources plus dependencies), project, or dependencies (JDK and libraries)")
                .str("projectPath", "Absolute root of one open project to search; defaults to all open projects")
                .integer("limit", "Maximum matches returned (1-200)", DEFAULT_LIMIT)
                .require("query")
                .build();

        mcpTools.registerTool(
                "find_java_type",
                ToolDescriptions.get("find_java_type"),
                schema,
                new ToolExecutor<FindTypeInput, Map<String, Object>>(FindTypeInput.class) {
                    @Override
                    public Map<String, Object> execute(FindTypeInput args) {
                        return executeFindType(args);
                    }
                });
    }

    private Map<String, Object> executeFindType(FindTypeInput args) {
        String query = args.query();
        if (isBlank(query)) {
            return Map.of("status", "error", "message", "query is required");
        }
        ClassIndex.NameKind kind = parseKind(args.kind());
        if (kind == null) {
            return Map.of("status", "error",
                    "message", "kind must be one of: camel, prefix, exact, regexp");
        }
        if (kind == ClassIndex.NameKind.CASE_INSENSITIVE_REGEXP) {
            try {
                Pattern.compile(query);
            } catch (PatternSyntaxException e) {
                return Map.of("status", "error",
                        "message", "query is not a valid regular expression: " + e.getDescription());
            }
        }
        Set<ClassIndex.SearchScope> scopes = parseScope(args.scope());
        if (scopes == null) {
            return Map.of("status", "error",
                    "message", "scope must be one of: all, project, dependencies");
        }
        int limit = clampLimit(args.limit());

        ProjectQuery projectQuery = Lookup.getDefault().lookup(ProjectQuery.class);
        if (projectQuery == null) {
            return Map.of("status", "error", "message", "ProjectQuery not available", "fatal", true);
        }
        Project[] projects = projectQuery.getAllOpenProjects();
        if (projects == null || projects.length == 0) {
            return Map.of("status", "error", "message", "No project is open");
        }
        if (!isBlank(args.projectPath())) {
            projects = restrictToProject(projects, args.projectPath());
            if (projects == null) {
                return Map.of("status", "error",
                        "message", "Not an open project: " + args.projectPath());
            }
        }

        Map<String, Map<String, Object>> matches = new TreeMap<>();
        for (Project project : projects) {
            if (collectMatches(project, query, kind, scopes, limit, matches)) {
                break;
            }
        }

        boolean truncated = matches.size() > limit;
        List<Map<String, Object>> list = new ArrayList<>(matches.values());
        if (truncated) {
            list = list.subList(0, limit);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("query", query);
        result.put("count", list.size());
        result.put("matches", list);
        if (truncated) {
            result.put("truncated", true);
        }
        return result;
    }

    /**
     * Adds this project's matches to {@code matches}. Returns true once the limit
     * is exceeded so the caller stops scanning further projects.
     */
    private static boolean collectMatches(Project project, String query, ClassIndex.NameKind kind,
            Set<ClassIndex.SearchScope> scopes, int limit, Map<String, Map<String, Object>> matches) {
        FileObject projectDir = project.getProjectDirectory();
        if (projectDir == null) {
            return false;
        }
        Set<ElementHandle<TypeElement>> hits;
        ClassPath sourcePath;
        try {
            ClassIndex index = ClasspathInfo.create(projectDir).getClassIndex();
            hits = index.getDeclaredTypes(query, kind, scopes);
            sourcePath = ClassPath.getClassPath(projectDir, ClassPath.SOURCE);
        } catch (RuntimeException e) {
            LOG.fine("Type search failed in {0}: {1}", projectDir.getPath(), e.getMessage());
            return false;
        }
        if (hits == null) {
            return false;
        }
        String projectRoot = ProjectPathGuard.absolutePath(projectDir);
        for (ElementHandle<TypeElement> handle : hits) {
            String fqn;
            try {
                fqn = handle.getQualifiedName();
            } catch (IllegalStateException e) {
                continue;
            }
            if (fqn == null || fqn.isEmpty() || matches.containsKey(fqn)) {
                continue;
            }
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("name", fqn);
            String path = findSourcePath(sourcePath, fqn);
            if (path != null) {
                match.put("path", path);
            }
            if (projectRoot != null) {
                match.put("project", projectRoot);
            }
            matches.put(fqn, match);
            if (matches.size() > limit) {
                return true;
            }
        }
        return false;
    }

    private static ClassIndex.NameKind parseKind(String value) {
        if (isBlank(value)) {
            return ClassIndex.NameKind.CAMEL_CASE_INSENSITIVE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "camel" -> ClassIndex.NameKind.CAMEL_CASE_INSENSITIVE;
            case "prefix" -> ClassIndex.NameKind.CASE_INSENSITIVE_PREFIX;
            case "exact" -> ClassIndex.NameKind.SIMPLE_NAME;
            case "regexp" -> ClassIndex.NameKind.CASE_INSENSITIVE_REGEXP;
            default -> null;
        };
    }

    private static Set<ClassIndex.SearchScope> parseScope(String value) {
        if (isBlank(value)) {
            return EnumSet.of(ClassIndex.SearchScope.SOURCE, ClassIndex.SearchScope.DEPENDENCIES);
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "all" -> EnumSet.of(ClassIndex.SearchScope.SOURCE, ClassIndex.SearchScope.DEPENDENCIES);
            case "project" -> EnumSet.of(ClassIndex.SearchScope.SOURCE);
            case "dependencies" -> EnumSet.of(ClassIndex.SearchScope.DEPENDENCIES);
            default -> null;
        };
    }

    private static int clampLimit(Integer value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, value));
    }

    /** Returns a single-element array for the matching open project, or null. */
    private static Project[] restrictToProject(Project[] projects, String projectPath) {
        File requested = new File(projectPath);
        try {
            String canonical = requested.getCanonicalPath();
            for (Project project : projects) {
                FileObject dir = project.getProjectDirectory();
                File root = dir == null ? null : FileUtil.toFile(dir);
                if (root != null && root.getCanonicalPath().equals(canonical)) {
                    return new Project[] {project};
                }
            }
        } catch (IOException e) {
            LOG.fine("Project path check failed for {0}: {1}", projectPath, e.getMessage());
        }
        return null;
    }

    private static String findSourcePath(ClassPath sourcePath, String fqn) {
        if (sourcePath == null) {
            return null;
        }
        FileObject file = sourcePath.findResource(fqn.replace('.', '/') + ".java");
        return file == null ? null : ProjectPathGuard.absolutePath(file);
    }

}
