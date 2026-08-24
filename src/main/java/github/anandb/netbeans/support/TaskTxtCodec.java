package github.anandb.netbeans.support;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import github.anandb.netbeans.model.TaskRecord;

/**
 * Pure todo.txt serialization/parsing for {@link TaskRecord}.
 *
 * <p>One task per line. Column order:
 * {@code [x ] (P) <completionDate> <creationDate> <summary> @tags +projects
 * due: id: estimate: consumed: upd:}. The {@code x } prefix marks a closed
 * task (todo.txt convention; open is the implicit default, so no
 * {@code status:} token is written). Priority {@code (A)}–{@code (Z)} sits
 * immediately after {@code x } or as the first token when open. Completion and
 * creation dates are positional (date-only) before the summary. Tags/projects
 * are emitted purely as {@code @}/{@code +} tokens (no redundant mirror) so a
 * load→save round-trip never double-counts. {@code estimate}/{@code consumed}
 * are integers; {@code upd:} is the full ISO last-modified timestamp.</p>
 *
 * <p>Zero external dependencies; headless-testable. Unknown tokens are
 * tolerated on parse and ignored; a line without an {@code id:} token is
 * skipped.</p>
 */
public final class TaskTxtCodec {

    /** Prefix marking a completed (closed) task. */
    public static final String DONE_PREFIX = "x ";

    /** Key for the last-modified ISO timestamp token. */
    public static final String KEY_UPD = "upd:";

    /** Key for the estimated-effort integer token. */
    public static final String KEY_ESTIMATE = "estimate:";

    /** Key for the consumed-effort integer token. */
    public static final String KEY_CONSUMED = "consumed:";

    /** Key for the due date token (standard todo.txt extension). */
    public static final String KEY_DUE = "due:";

    /** Key for the task id token. */
    public static final String KEY_ID = "id:";

    private TaskTxtCodec() {
    }

    /** Serializes the given tasks to a todo.txt document (no header row). */
    public static String serialize(List<TaskRecord> tasks) {
        StringBuilder sb = new StringBuilder(256);
        for (TaskRecord t : tasks) {
            sb.append(serializeLine(t)).append('\n');
        }
        return sb.toString();
    }

    private static String serializeLine(TaskRecord t) {
        StringBuilder sb = new StringBuilder(128);
        if (TaskRecord.isFinishedStatus(t.status())) {
            sb.append(DONE_PREFIX);
        }
        String pri = normalizePriority(t.priority());
        if (pri != null) {
            sb.append('(').append(pri).append(") ");
        }
        String created = dateOnly(t.createdAt());
        if (created != null) {
            sb.append(created).append(' ');
        }
        // Summary is the free text; emit verbatim. If blank, fall back to id so
        // the line is never empty.
        String summary = t.summary() == null ? "" : t.summary();
        sb.append(summary.isBlank() ? "" : summary).append(' ');
        List<String> tail = new ArrayList<>();
        if (t.tags() != null) {
            for (String tag : t.tags()) {
                if (tag != null && !tag.isBlank()) {
                    tail.add("@" + tag.trim());
                }
            }
        }
        if (t.projects() != null) {
            for (String p : t.projects()) {
                if (p != null && !p.isBlank()) {
                    tail.add("+" + p.trim());
                }
            }
        }
        if (t.dueDate() != null && !t.dueDate().isBlank()) {
            tail.add(KEY_DUE + t.dueDate().trim());
        }
        if (t.id() != null && !t.id().isBlank()) {
            tail.add(KEY_ID + t.id());
        }
        if (t.estimate() != 0) {
            tail.add(KEY_ESTIMATE + t.estimate());
        }
        if (t.consumed() != 0) {
            tail.add(KEY_CONSUMED + t.consumed());
        }
        if (t.updatedAt() != null && !t.updatedAt().isBlank()) {
            tail.add(KEY_UPD + t.updatedAt().trim());
        }
        sb.append(String.join(" ", tail));
        return sb.toString().stripTrailing();
    }

    /** Parses a todo.txt document into tasks; malformed/blank lines are skipped.
     *  Lines without an {@code id:} token are assigned a fresh NetBeans-format id
     *  ({@code t-XXXXXXXX}), unique within the document. */
    public static List<TaskRecord> parse(String content) {
        List<TaskRecord> result = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return result;
        }
        Set<String> usedIds = new java.util.HashSet<>();
        for (String rawLine : content.split("\n", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            TaskRecord t = parseLine(line, usedIds);
            if (t == null) {
                continue; // unparseable line
            }
            result.add(t);
        }
        return result;
    }

    /** Generates a NetBeans-format task id ({@code t-} + 8 hex chars), avoiding
     *  any id already present in {@code usedIds}. */
    private static String generateId(Set<String> usedIds) {
        String id;
        do {
            id = "t-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (usedIds.contains(id));
        usedIds.add(id);
        return id;
    }

    private static TaskRecord parseLine(String line, Set<String> usedIds) {
        boolean done = false;
        if (line.startsWith(DONE_PREFIX)) {
            done = true;
            line = line.substring(DONE_PREFIX.length()).stripLeading();
        }
        String priority = null;
        if (line.startsWith("(") && line.length() > 2 && line.charAt(2) == ')') {
            String p = line.substring(1, 2).toUpperCase(java.util.Locale.ROOT);
            if (p.matches("[A-Z]")) {
                priority = p;
                line = line.substring(3).stripLeading();
            }
        }
        // Positional dates: a completion date only follows x; a creation date
        // follows the completion date (or the priority when open).
        String createdAt = null;
        if (isDateToken(line)) {
            // completion date (only meaningful when done) — consumed, not stored.
            int sp = line.indexOf(' ');
            String rest = sp < 0 ? "" : line.substring(sp + 1).stripLeading();
            if (isDateToken(rest)) {
                createdAt = rest.substring(0, 10);
                line = rest.substring(10).stripLeading();
            } else {
                createdAt = line.substring(0, 10);
                line = rest;
            }
        }
        // Remaining: summary + tokens. Split summary (free text) from trailing
        // @/+ / key:value tokens. We tokenize by spaces but reassemble the
        // summary from any token that is neither @/+ nor key:value.
        List<String> tags = new ArrayList<>();
        List<String> projects = new ArrayList<>();
        String id = null;
        String due = "";
        int estimate = 0;
        int consumed = 0;
        String upd = null;
        List<String> summaryParts = new ArrayList<>();
        for (String tok : line.split(" ")) {
            if (tok.isEmpty()) {
                continue;
            }
            if (tok.startsWith("@")) {
                String v = tok.substring(1).trim();
                if (!v.isEmpty()) {
                    tags.add(v);
                }
            } else if (tok.startsWith("+")) {
                String v = tok.substring(1).trim();
                if (!v.isEmpty()) {
                    projects.add(v);
                }
            } else if (tok.startsWith(KEY_ID)) {
                id = tok.substring(KEY_ID.length()).trim();
            } else if (tok.startsWith(KEY_DUE)) {
                due = tok.substring(KEY_DUE.length()).trim();
            } else if (tok.startsWith(KEY_ESTIMATE)) {
                estimate = parseIntOrZero(tok.substring(KEY_ESTIMATE.length()));
            } else if (tok.startsWith(KEY_CONSUMED)) {
                consumed = parseIntOrZero(tok.substring(KEY_CONSUMED.length()));
            } else if (tok.startsWith(KEY_UPD)) {
                upd = tok.substring(KEY_UPD.length()).trim();
            } else {
                summaryParts.add(tok);
            }
        }
        if (id == null || id.isEmpty()) {
            // No explicit id: assign a fresh NetBeans-format id, unique in this doc.
            id = generateId(usedIds);
        } else {
            usedIds.add(id);
        }
        String summary = String.join(" ", summaryParts).trim();
        String status = done ? "closed" : "open";
        String updatedAt = (upd == null || upd.isEmpty())
                ? (createdAt == null ? "" : createdAt)
                : upd;
        return new TaskRecord(id, status, priority == null ? "" : priority,
                summary, tags, projects, due, estimate, consumed,
                createdAt == null ? "" : createdAt, updatedAt);
    }

    /** True if the start of the line is a {@code YYYY-MM-DD} token. */
    private static boolean isDateToken(String line) {
        if (line.length() < 10) {
            return false;
        }
        String head = line.substring(0, 10);
        if (head.charAt(4) != '-' || head.charAt(7) != '-') {
            return false;
        }
        try {
            LocalDate.parse(head);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    /** Normalizes a stored priority to a single uppercase A–Z letter, or null. */
    private static String normalizePriority(String priority) {
        if (priority == null) {
            return null;
        }
        String p = priority.trim().toUpperCase(java.util.Locale.ROOT);
        return p.matches("[A-Z]") ? p : null;
    }

    /** Extracts a date-only (YYYY-MM-DD) prefix from an ISO timestamp, or null. */
    private static String dateOnly(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        String v = iso.trim();
        if (v.length() >= 10 && v.charAt(4) == '-' && v.charAt(7) == '-') {
            try {
                LocalDate.parse(v.substring(0, 10));
                return v.substring(0, 10);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        return null;
    }

    private static int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
