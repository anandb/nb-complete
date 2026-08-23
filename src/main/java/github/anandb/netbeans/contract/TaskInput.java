package github.anandb.netbeans.contract;

/**
 * Editable task fields carried by create/update operations.
 *
 * <p>{@code status} is free text; {@code priority} is one of
 * {@code high|normal|low}; {@code tags} is comma-separated; {@code dueDate} is
 * an ISO-8601 date string or empty when unset.</p>
 */
public record TaskInput(
    String status,
    String priority,
    String summary,
    String description,
    String filePath,
    String tags,
    String dueDate
) {

    /** Alias for readability; an unset field is an empty string. */
    public String summaryOrEmpty() {
        return summary == null ? "" : summary;
    }
}