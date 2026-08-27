package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import org.openide.util.Lookup;

import github.anandb.netbeans.contract.TaskInput;
import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
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
                    List<String> ids = control.repositoryIds();
                    if (ids.isEmpty()) {
                        return Map.of("status", "error", "message", "No Beanbot Tasks repository configured. Add one in Tasks options first.");
                    }
                    String repoId = args.repoId();
                    if (repoId == null || repoId.isBlank()) {
                        if (ids.size() == 1) {
                            repoId = ids.get(0);
                        } else {
                            return Map.of("status", "error", "message",
                                "Multiple repositories exist; provide repoId. Available: " + String.join(", ", ids));
                        }
                    } else if (!ids.contains(repoId)) {
                        return Map.of("status", "error", "message",
                            "Unknown repository '" + repoId + "'. Available: " + String.join(", ", ids));
                    }
                    // Validate the repository is accessible
                    String csvPath = control.csvPathOf(repoId);
                    if (csvPath == null || csvPath.isBlank()) {
                        return Map.of("status", "error", "message",
                            "Repository '" + repoId + "' has no file path configured.");
                    }
                    java.io.File csvFile = new java.io.File(csvPath);
                    if (!csvFile.exists()) {
                        return Map.of("status", "error", "message",
                            "Repository file not found: " + csvPath + ". Repository may not be open or configured correctly.");
                    }
                    if (!csvFile.canRead()) {
                        return Map.of("status", "error", "message",
                            "Repository file is not readable: " + csvPath + ". Check file permissions.");
                    }
                    TaskInput input = new TaskInput(
                        emptyIfNull(args.status()), emptyIfNull(args.priority()),
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

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    private static int toInt(Integer v) {
        return v == null ? 0 : v;
    }
}