package github.anandb.netbeans.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Holds an H2 database file open from a separate process so the integration test
 * can observe the lock another IDE instance would hold during an in-place upgrade.
 * Writes the marker file once the connection (and therefore the file lock) is live,
 * then blocks until this process is destroyed.
 */
public final class DbLockHolder {

    private DbLockHolder() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection ignored = DriverManager.getConnection(args[0])) {
            writeMarker(Path.of(args[1]));
            System.in.read();
        }
    }

    private static void writeMarker(Path marker) throws IOException {
        Files.writeString(marker, "locked", StandardCharsets.UTF_8);
    }
}
