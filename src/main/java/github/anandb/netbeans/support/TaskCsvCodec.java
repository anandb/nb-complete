package github.anandb.netbeans.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import github.anandb.netbeans.model.TaskRecord;

/**
 * Pure CSV serialization/parsing for {@link TaskRecord} with RFC-4180 quoting.
 * Zero external dependencies; headless-testable. {@code subtasks} are stored in
 * the eponymous cell joined by {@code ;}. Unknown/extra columns are tolerated on
 * parse (ignored) so the schema can evolve without breaking existing files; a
 * column set to an empty string is treated as absent.
 */
public final class TaskCsvCodec {

    /** Canonical column order (matches {@code TaskRecord} component order). */
    public static final List<String> COLUMNS = List.of(
        "id", "status", "priority", "summary", "description",
        "filePath", "tags", "dueDate", "subtasks", "createdAt", "updatedAt");

    /** Separator used to store a task's subtask ids inside the {@code subtasks} cell. */
    public static final String SUBTASK_SEPARATOR = ";";

    private TaskCsvCodec() {
    }

    /** Serializes the given tasks (plus a header row) to a CSV string. */
    public static String serialize(List<TaskRecord> tasks) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(joinRow(COLUMNS)).append('\n');
        for (TaskRecord t : tasks) {
            sb.append(joinRow(List.of(
                t.id(), t.status(), t.priority(), t.summary(), t.description(),
                t.filePath(), t.tags(), t.dueDate(),
                t.subtasks() == null ? "" : String.join(SUBTASK_SEPARATOR, t.subtasks()),
                t.createdAt(), t.updatedAt())));
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Parses a CSV document into tasks; malformed/blank rows are skipped. */
    public static List<TaskRecord> parse(String content) {
        List<TaskRecord> result = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return result;
        }
        List<List<String>> rows = parseRows(content);
        if (rows.isEmpty()) {
            return result;
        }
        Map<String, Integer> colIndex = new LinkedHashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) {
            String h = rows.get(0).get(i);
            if (h != null && !h.isBlank()) {
                colIndex.put(h, i);
            }
        }
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (isEmptyRow(row)) {
                continue;
            }
            String id = cell(row, colIndex, "id");
            if (id == null || id.isEmpty()) {
                continue; // rows without an id are not loadable tasks
            }
            String subtasks = cell(row, colIndex, "subtasks");
            List<String> subList = subtasks == null || subtasks.isBlank()
                    ? List.of()
                    : Arrays.stream(subtasks.split(SUBTASK_SEPARATOR))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
            result.add(new TaskRecord(
                id,
                cell(row, colIndex, "status"),
                cell(row, colIndex, "priority"),
                cell(row, colIndex, "summary"),
                cell(row, colIndex, "description"),
                cell(row, colIndex, "filePath"),
                cell(row, colIndex, "tags"),
                cell(row, colIndex, "dueDate"),
                subList,
                cell(row, colIndex, "createdAt"),
                cell(row, colIndex, "updatedAt")));
        }
        return result;
    }

    /** Splits a single CSV row's cell values into a header + field list. */
    private static String joinRow(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(values.get(i)));
        }
        return sb.toString();
    }

    /** Quotes a value per RFC-4180 (quotes doubled inside). */
    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Splits a CSV document into logical rows honoring quoted fields. */
    private static List<List<String>> parseRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int len = content.length();
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++; // escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                switch (c) {
                    case '"' -> inQuotes = true;
                    case ',' -> {
                        row.add(field.toString());
                        field.setLength(0);
                    }
                    case '\n' -> {
                        trimCarriageReturn(field);
                        row.add(field.toString());
                        rows.add(row);
                        row = new ArrayList<>();
                        field.setLength(0);
                    }
                    case '\r' -> {
                        // skip; handled by trimCarriageReturn on newline
                    }
                    default -> field.append(c);
                }
            }
        }
        if (inQuotes) {
            // unterminated quote — salvage what we have
            field.append(content.charAt(len - 1));
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static void trimCarriageReturn(StringBuilder field) {
        if (field.length() > 0 && field.charAt(field.length() - 1) == '\r') {
            field.deleteCharAt(field.length() - 1);
        }
    }

    private static boolean isEmptyRow(List<String> row) {
        for (String s : row) {
            if (s != null && !s.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(List<String> row, Map<String, Integer> colIndex, String col) {
        Integer idx = colIndex.get(col);
        if (idx == null || idx >= row.size()) {
            return "";
        }
        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }
}