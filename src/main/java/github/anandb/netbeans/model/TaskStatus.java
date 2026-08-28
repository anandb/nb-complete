package github.anandb.netbeans.model;

/**
 * The fixed set of task statuses. The todo.txt file uses the {@code x }
 * completion prefix (no {@code status:} token), so this enum is a typed view
 * over the free-text {@code status} field where only {@code closed} marks
 * completion; any other value (including blank) is treated as {@code open}.
 */
public enum TaskStatus {

    OPEN("Open"),
    CLOSED("Closed");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    /** The stored status value. */
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
            if (s.value.equalsIgnoreCase(v)) {
                return s;
            }
        }
        return OPEN;
    }
}
