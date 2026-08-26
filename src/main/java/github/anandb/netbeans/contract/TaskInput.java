package github.anandb.netbeans.contract;

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
        return split(tags);
    }

    /** Projects as a list, splitting on commas (used by the store). */
    public List<String> projectsList() {
        return split(projects);
    }

    private static List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String part : s.split(",")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return List.copyOf(out);
    }
}
