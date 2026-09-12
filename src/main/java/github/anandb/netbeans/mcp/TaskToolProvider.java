package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.openide.util.Lookup;

import github.anandb.netbeans.contract.TaskInput;
import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.model.TaskStatus;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;

/**
 * Registers the {@code add_task} MCP tool so the chat plugin (or any ACP
 * client) can create tasks directly in a Beanbot Tasks repository.
 */
public class TaskToolProvider {

    private static final Logger LOG = Logger.from(TaskToolProvider.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    public void registerTools(McpTools mcpTools) {
        registerAddTask(mcpTools);
        registerCloseTask(mcpTools);
        registerUpdateTask(mcpTools);
        registerSearchTask(mcpTools);
    }

    private void registerAddTask(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoId = properties.putObject("repoId");
        repoId.put("type", "string");
        repoId.put("description", "Id of the repository to add the task to. Optional if only one repository exists.");

        ObjectNode summary = properties.putObject("summary");
        summary.put("type", "string");
        summary.put("description", "Short task summary/title.");

        ObjectNode status = properties.putObject("status");
        status.put("type", "string");
        status.put("description", "Free-text status, e.g. 'open', 'closed'. 'closed' marks completion.");

        ObjectNode priority = properties.putObject("priority");
        priority.put("type", "string");
        priority.put("description", "Single uppercase letter A-Z (priority ranking).");

        ObjectNode projects = properties.putObject("projects");
        projects.put("type", "string");
        projects.put("description", "Comma-separated project names (stored as +project in todo.txt).");

        ObjectNode tags = properties.putObject("tags");
        tags.put("type", "string");
        tags.put("description", "Comma-separated tags (stored as @tag in todo.txt).");

        ObjectNode dueDate = properties.putObject("dueDate");
        dueDate.put("type", "string");
        dueDate.put("description", "ISO-8601 due date, optional.");

        ObjectNode estimate = properties.putObject("estimate");
        estimate.put("type", "integer");
        estimate.put("description", "Estimated effort in arbitrary user-inferred units.");

        ObjectNode consumed = properties.putObject("consumed");
        consumed.put("type", "integer");
        consumed.put("description", "Consumed effort in arbitrary user-inferred units.");

        ArrayNode required = schema.putArray("required");
        required.add("summary");

        mcpTools.registerTool(
            "add_task",
            """
            Creates a task in a Beanbot Tasks repository.

            Use when the user wants to add, store, track, or create a task or todo item, including:
            - Explicit requests: 'add a task', 'create a todo', 'track this', 'log this as a task'
            - Reminders: 'remind me about X tomorrow/next week/next Thursday'
            - Quick captures: 'note this down', 'put this on my list', 'save for later'

            Priority guidelines:
            - User says 'critical', 'urgent', 'important', or 'high priority': use 'A'
            - User says 'low priority', 'nice to have', or 'whenever': use 'T' (or 'S' for someday/maybe)
            - Otherwise, infer importance from context and assign A-Z (default 'N' for normal tasks)

            Due dates:
            - If user specifies a date ('tomorrow', 'next week', 'Sept 5'), parse and set dueDate
            - If no date is mentioned or cannot be inferred, omit dueDate (task has no deadline)

            Examples:
            - 'Add a task to fix the login bug' -> summary='Fix the login bug', priority='B'
            - 'Remind me to run tests next Monday' -> summary='Run tests', dueDate='2026-09-07', priority='B'
            - 'Critical: deploy the hotfix today' -> summary='Deploy the hotfix', dueDate='2026-08-27', priority='A'
            - 'Add a todo: update API docs for v2 release' -> summary='Update API docs for v2 release', priority='B'
            - 'Track this as a task for the backend project, tag it @api' -> summary='[user query]', projects='backend', tags='api'
            """,
            schema,
            new ToolExecutor<AddTaskInput, Map<String, Object>>(AddTaskInput.class) {
                @Override
                public Map<String, Object> execute(AddTaskInput args) throws Exception {
                    TaskRepositoryControl control = Lookup.getDefault().lookup(TaskRepositoryControl.class);
                    if (control == null) {
                        return Map.of("status", "error", "message", "TaskRepositoryControl not available");
                    }
                    if (args.summary() == null || args.summary().isBlank()) {
                        return Map.of("status", "error", "message", "summary is required");
                    }
                    String repoId = resolveRepository(control, args.repoId());
                    if (repoId == null) {
                        return resolveError(control, args.repoId());
                    }
                    // Validate the repository is accessible
                    String tasksPath = control.tasksPathOf(repoId);
                    if (tasksPath == null || tasksPath.isBlank()) {
                        return Map.of("status", "error", "message",
                            "Repository '" + repoId + "' has no file path configured.");
                    }
                    File tasksFile = new File(tasksPath);
                    if (!tasksFile.exists()) {
                        return Map.of("status", "error", "message",
                            "Repository file not found: " + tasksPath + ". Repository may not be open or configured correctly.");
                    }
                    if (!tasksFile.canRead()) {
                        return Map.of("status", "error", "message",
                            "Repository file is not readable: " + tasksPath + ". Check file permissions.");
                    }
                    String priority = emptyIfNull(args.priority());
                    if (!priority.isEmpty() && !priority.matches("[A-Z]")) {
                        return Map.of("status", "error", "message",
                            "Invalid priority '" + args.priority() + "'. Priority must be a single uppercase letter A-Z.");
                    }
                    String status = emptyIfNull(args.status());
                    if (!status.isEmpty() && !isValidStatus(status)) {
                        return Map.of("status", "error", "message",
                            "Invalid status '" + args.status() + "'. Status must be 'open' or 'closed'.");
                    }
                    TaskInput input = new TaskInput(
                        status, priority,
                        args.summary().trim(), emptyIfNull(args.tags()),
                        emptyIfNull(args.projects()), emptyIfNull(args.dueDate()),
                        toInt(args.estimate()), toInt(args.consumed()));
                    TaskRecord created = control.add(repoId, input);
                    LOG.info("add_task tool called: repo={0} id={1} summary={2}", repoId, created.id(), args.summary());
                    return Map.of("status", "ok", "id", created.id(),
                        "message", "Task added to '" + control.displayNameOf(repoId) + "'.");
                }
            });
    }

    private void registerCloseTask(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoId = properties.putObject("repoId");
        repoId.put("type", "string");
        repoId.put("description", "Id of the repository holding the task. Optional if only one repository exists.");

        ObjectNode taskId = properties.putObject("taskId");
        taskId.put("type", "string");
        taskId.put("description", "Id of the task to close (as returned by add_task).");

        ArrayNode required = schema.putArray("required");
        required.add("taskId");

        mcpTools.registerTool(
            "close_task",
            """
            Closes (marks as done) a task in a Beanbot Tasks repository.

            Use when the user wants to complete, finish, close, or mark a task as done, including:
            - 'close that task', 'mark it as done', 'task is complete'
            - 'finish the login-bug task', 'that todo is done'

            Only sets status to 'closed'; other task fields (summary, priority, tags, due date)
            are left untouched. The completion timestamp is recorded automatically.

            Examples:
            - 'Close task t-3' -> taskId='t-3'
            - 'The deploy task is done, close it' -> taskId=<id from add_task or list>
            """,
            schema,
            new ToolExecutor<CloseTaskInput, Map<String, Object>>(CloseTaskInput.class) {
                @Override
                public Map<String, Object> execute(CloseTaskInput args) throws Exception {
                    TaskRepositoryControl control = Lookup.getDefault().lookup(TaskRepositoryControl.class);
                    if (control == null) {
                        return Map.of("status", "error", "message", "TaskRepositoryControl not available");
                    }
                    if (args.taskId() == null || args.taskId().isBlank()) {
                        return Map.of("status", "error", "message", "taskId is required");
                    }
                    String repoId = resolveRepository(control, args.repoId());
                    if (repoId == null) {
                        return resolveError(control, args.repoId());
                    }
                    TaskRecord task = control.get(repoId, args.taskId().trim());
                    if (task == null) {
                        return Map.of("status", "error", "message",
                            "Task '" + args.taskId() + "' not found in '" + control.displayNameOf(repoId) + "'.");
                    }
                    if (task.isFinished()) {
                        return Map.of("status", "ok", "id", task.id(),
                            "message", "Task '" + task.summary() + "' is already closed.");
                    }
                    TaskRecord closed = task.withDetails("closed", task.priority(), task.summary(),
                        task.tags(), task.projects(), task.dueDate(), task.estimate(), task.consumed());
                    boolean updated = control.update(repoId, closed);
                    if (!updated) {
                        return Map.of("status", "error", "message",
                            "Failed to close task '" + args.taskId() + "'.");
                    }
                    LOG.info("close_task tool called: repo={0} id={1}", repoId, task.id());
                    return Map.of("status", "ok", "id", task.id(),
                        "message", "Task '" + task.summary() + "' closed in '" + control.displayNameOf(repoId) + "'.");
                }
            });
    }

    private void registerUpdateTask(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoId = properties.putObject("repoId");
        repoId.put("type", "string");
        repoId.put("description", "Id of the repository holding the task. Optional if only one repository exists.");

        ObjectNode taskId = properties.putObject("taskId");
        taskId.put("type", "string");
        taskId.put("description", "Id of the task to update (as returned by add_task).");

        ObjectNode summary = properties.putObject("summary");
        summary.put("type", "string");
        summary.put("description", "New summary/title. Omitted fields are left unchanged.");

        ObjectNode status = properties.putObject("status");
        status.put("type", "string");
        status.put("description", "'open' or 'closed'. Setting 'closed' records completion; 'open' reopens.");

        ObjectNode priority = properties.putObject("priority");
        priority.put("type", "string");
        priority.put("description", "Single uppercase letter A-Z.");

        ObjectNode projects = properties.putObject("projects");
        projects.put("type", "string");
        projects.put("description", "Comma-separated project names. Empty string clears; omitted keeps current.");

        ObjectNode tags = properties.putObject("tags");
        tags.put("type", "string");
        tags.put("description", "Comma-separated tags. Empty string clears; omitted keeps current.");

        ObjectNode dueDate = properties.putObject("dueDate");
        dueDate.put("type", "string");
        dueDate.put("description", "ISO-8601 due date. Empty string clears; omitted keeps current.");

        ObjectNode estimate = properties.putObject("estimate");
        estimate.put("type", "integer");
        estimate.put("description", "Estimated effort units.");

        ObjectNode consumed = properties.putObject("consumed");
        consumed.put("type", "integer");
        consumed.put("description", "Consumed effort units.");

        ArrayNode required = schema.putArray("required");
        required.add("taskId");

        mcpTools.registerTool(
            "update_task",
            """
            Updates an existing task in a Beanbot Tasks repository.

            Use when the user wants to change, edit, rename, reschedule, or
            reopen a task, including:
            - 'rename task t-3 to ...', 'change its priority to A'
            - 'reschedule the deploy task to Friday', 'clear the due date'
            - 'reopen task t-7'

            Only the provided fields change; omitted fields keep their values.
            Empty tags/projects/dueDate clear those values. status='closed'
            records completion, 'open' clears it. Idempotent fields are
            validated (status must be open|closed, priority a single A-Z).

            Examples:
            - 'Rename task t-3' -> taskId='t-3', summary='New title'
            - 'Priority A for the login task' -> taskId='t-1', priority='A'
            - 'Reopen the deploy task' -> taskId='t-7', status='open'
            """,
            schema,
            new ToolExecutor<UpdateTaskInput, Map<String, Object>>(UpdateTaskInput.class) {
                @Override
                public Map<String, Object> execute(UpdateTaskInput args) throws Exception {
                    TaskRepositoryControl control = Lookup.getDefault().lookup(TaskRepositoryControl.class);
                    if (control == null) {
                        return Map.of("status", "error", "message", "TaskRepositoryControl not available");
                    }
                    if (args.taskId() == null || args.taskId().isBlank()) {
                        return Map.of("status", "error", "message", "taskId is required");
                    }
                    String repoId = resolveRepository(control, args.repoId());
                    if (repoId == null) {
                        return resolveError(control, args.repoId());
                    }
                    TaskRecord task = control.get(repoId, args.taskId().trim());
                    if (task == null) {
                        return Map.of("status", "error", "message",
                            "Task '" + args.taskId() + "' not found in '" + control.displayNameOf(repoId) + "'.");
                    }
                    if (args.summary() != null && args.summary().isBlank()) {
                        return Map.of("status", "error", "message", "summary cannot be blank");
                    }
                    if (args.priority() != null && !args.priority().matches("[A-Z]")) {
                        return Map.of("status", "error", "message",
                            "Invalid priority '" + args.priority() + "'. Priority must be a single uppercase letter A-Z.");
                    }
                    if (args.status() != null && !isValidStatus(args.status())) {
                        return Map.of("status", "error", "message",
                            "Invalid status '" + args.status() + "'. Status must be 'open' or 'closed'.");
                    }
                    String summary = args.summary() != null ? args.summary().trim() : task.summary();
                    String status = args.status() != null ? args.status().trim() : task.status();
                    String priority = args.priority() != null ? args.priority() : task.priority();
                    List<String> tags = args.tags() != null ? splitCsv(args.tags()) : task.tags();
                    List<String> projects = args.projects() != null ? splitCsv(args.projects()) : task.projects();
                    String dueDate = args.dueDate() != null ? args.dueDate().trim() : task.dueDate();
                    int estimate = args.estimate() != null ? args.estimate() : task.estimate();
                    int consumed = args.consumed() != null ? args.consumed() : task.consumed();
                    TaskRecord updated = task.withDetails(status, priority, summary,
                        tags, projects, dueDate, estimate, consumed);
                    boolean ok = control.update(repoId, updated);
                    if (!ok) {
                        return Map.of("status", "error", "message",
                            "Failed to update task '" + args.taskId() + "'.");
                    }
                    LOG.info("update_task tool called: repo={0} id={1}", repoId, task.id());
                    return Map.of("status", "ok", "id", task.id(),
                        "message", "Task '" + task.id() + "' updated in '" + control.displayNameOf(repoId) + "'.");
                }
            });
    }

    /** Splits a comma-separated MCP string into a trimmed list; blank clears. */
    private static List<String> splitCsv(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return List.copyOf(out);
    }

    private void registerSearchTask(McpTools mcpTools) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode repoId = properties.putObject("repoId");
        repoId.put("type", "string");
        repoId.put("description", "Id of the repository to search. Optional; searches all repositories when omitted.");

        ObjectNode query = properties.putObject("query");
        query.put("type", "string");
        query.put("description", "Case-insensitive substring to match against open task summaries.");

        ArrayNode required = schema.putArray("required");
        required.add("query");

        mcpTools.registerTool(
            "search_task",
            """
            Searches open tasks in Beanbot Tasks repositories by a substring of their summary.

            Returns at most the top 10 matching open tasks, each with its repoId and taskId so the
            result can be passed directly to close_task. Closed tasks are excluded.

            Use when the user wants to find, look up, or list tasks, especially before closing one:
            - 'which tasks mention login?'
            - 'find the bug task', 'search for the deploy task'
            - 'list all the open tasks'

            Examples:
            - 'search_task query=login' -> matches tasks whose summary contains 'login'
            - 'search_task repoId=backend query=test' -> search only the backend repo
            """,
            schema,
            new ToolExecutor<SearchTaskInput, Map<String, Object>>(SearchTaskInput.class) {
                @Override
                public Map<String, Object> execute(SearchTaskInput args) throws Exception {
                    TaskRepositoryControl control = Lookup.getDefault().lookup(TaskRepositoryControl.class);
                    if (control == null) {
                        return Map.of("status", "error", "message", "TaskRepositoryControl not available");
                    }
                    if (args.query() == null || args.query().isBlank()) {
                        return Map.of("status", "error", "message", "query is required");
                    }
                    String needle = args.query().trim().toLowerCase(Locale.ROOT);

                    List<String> ids = control.repositoryIds();
                    if (ids.isEmpty()) {
                        return Map.of("status", "error", "message",
                            "No Beanbot Tasks repository configured. Add one in Tasks options first.");
                    }
                    List<String> scope = ids;
                    if (args.repoId() != null && !args.repoId().isBlank()) {
                        if (!ids.contains(args.repoId())) {
                            return Map.of("status", "error", "message",
                                "Unknown repository '" + args.repoId() + "'. Available: " + String.join(", ", ids));
                        }
                        scope = List.of(args.repoId());
                    }

                    List<Map<String, Object>> matches = new ArrayList<>();
                    for (String repo : scope) {
                        for (TaskRecord task : control.list(repo)) {
                            if (task.isFinished()) {
                                continue;
                            }
                            if (task.summary() != null
                                    && task.summary().toLowerCase(Locale.ROOT).contains(needle)) {
                                matches.add(Map.of(
                                    "repoId", repo,
                                    "taskId", task.id(),
                                    "summary", task.summary(),
                                    "displayName", control.displayNameOf(repo)));
                                if (matches.size() >= SEARCH_MAX_RESULTS) {
                                    LOG.info("search_task tool called: query={0} matched={1}", args.query(), matches.size());
                                    return Map.of("status", "ok", "matches", matches);
                                }
                            }
                        }
                    }
                    LOG.info("search_task tool called: query={0} matched={1}", args.query(), matches.size());
                    return Map.of("status", "ok", "matches", matches);
                }
            });
    }

    /** Maximum number of matching tasks returned by {@code search_task}. */
    private static final int SEARCH_MAX_RESULTS = 10;

    /**
     * Resolves the repository id to operate on: the given id if valid, the
     * single existing repository, or null when it cannot be resolved.
     */
    private static String resolveRepository(TaskRepositoryControl control, String repoId) {
        List<String> ids = control.repositoryIds();
        if (ids.isEmpty()) {
            return null;
        }
        if (repoId == null || repoId.isBlank()) {
            return ids.size() == 1 ? ids.get(0) : null;
        }
        return ids.contains(repoId) ? repoId : null;
    }

    /** Builds the error map for an unresolvable repository id. */
    private static Map<String, Object> resolveError(TaskRepositoryControl control, String repoId) {
        List<String> ids = control.repositoryIds();
        if (ids.isEmpty()) {
            return Map.of("status", "error", "message",
                "No Beanbot Tasks repository configured. Add one in Tasks options first.");
        }
        if (repoId == null || repoId.isBlank()) {
            return Map.of("status", "error", "message",
                "Multiple repositories exist; provide repoId. Available: " + String.join(", ", ids));
        }
        return Map.of("status", "error", "message",
            "Unknown repository '" + repoId + "'. Available: " + String.join(", ", ids));
    }

    /**
     * True if the status maps to an explicit {@link TaskStatus}; null/blank and
     * unknown values like "in-progress" or "cancelled" are rejected so a typo
     * never silently becomes "open".
     */
    private static boolean isValidStatus(String status) {
        return TaskStatus.fromValue(status).value().equalsIgnoreCase(status.trim());
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    private static int toInt(Integer v) {
        return v == null ? 0 : v;
    }
}