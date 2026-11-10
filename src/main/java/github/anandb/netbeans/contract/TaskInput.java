package github.anandb.netbeans.contract;

import java.util.ArrayList;
import java.util.List;

/**
 * Editable task fields carried by create/update operations.
 *
 * <p>{@code status} is free text ({@code open|closed}); {@code priority} is a
 * single uppercase letter {@code A}–{@code Z}. {@code tags} are
 * {@code @}-prefixed, {@code projects} {@code +}-prefixed, both comma-separated
 * on input. {@code dueDate} is an ISO-8601 date string or empty when unset.
 * {@code estimate}/{@code consumed} are arbitrary user-inferred integer
 * units.</p>
 */
public record TaskInput(
    String status,
    String priority,
    String summary,
    String tags,
    String projects,
    String dueDate,
    int estimate,
    int consumed
) {

    /** Tags as a list, splitting on commas (used by the store). */
    public List<String> tagsList() {
        return splitCsv(tags);
    }

    /** Projects as a list, splitting on commas (used by the store). */
    public List<String> projectsList() {
        return splitCsv(projects);
    }

    /** Splits a comma-separated list into trimmed, non-empty values. */
    public static List<String> splitCsv(String s) {
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
}
