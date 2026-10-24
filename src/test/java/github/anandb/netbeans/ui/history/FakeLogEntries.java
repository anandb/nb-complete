package github.anandb.netbeans.ui.history;

/**
 * Stand-in for the versioning module's log entries: a package-private concrete
 * class extending a public base that declares {@code getMessage()}. The real
 * git/Hg entries have exactly this shape, which is why reflecting
 * {@code getMessage()} off the concrete class fails across packages.
 */
public final class FakeLogEntries {

    public abstract static class Entry {
        public abstract String getMessage();
    }

    private static final class HiddenEntry extends Entry {
        private final String message;

        HiddenEntry(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    private FakeLogEntries() {
    }

    public static Object commit(String message) {
        return new HiddenEntry(message);
    }

    /** A file/event row: user data without a {@code getMessage()}. */
    public static Object file() {
        return new Object();
    }
}
