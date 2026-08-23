package github.anandb.netbeans.model;

/**
 * The fixed set of task statuses. The CSV stores the lowercase
 * {@link #value()} string, so this enum is a typed view over the free-text
 * {@code status} column (legacy values such as "done"/"completed" are accepted
 * on read and mapped to {@link #CLOSED}).
 */
public enum TaskStatus {

    OPEN("open"),
    IN_PROGRESS("in-progress"),
    CLOSED("closed");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    /** The stored CSV value. */
    public String value() {
        return value;
    }

    /** Maps a stored status string to this enum, defaulting to {@link #OPEN}. */
    public static TaskStatus fromValue(String status) {
        if (status == null) {
            return OPEN;
        }
        String v = status.trim().toLowerCase(java.util.Locale.ROOT);
        for (TaskStatus s : values()) {
            if (s.value.equals(v)) {
                return s;
            }
        }
        return OPEN;
    }
}
