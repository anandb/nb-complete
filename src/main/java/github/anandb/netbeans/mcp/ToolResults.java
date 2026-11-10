package github.anandb.netbeans.mcp;

import java.util.Map;

/**
 * Standard tool-result maps. {@link #fatal} marks real breakage, which
 * {@code MessageServlet} maps to JSON-RPC {@code isError}; {@link #error} is an
 * ordinary validation or lookup failure the model should read and correct.
 */
final class ToolResults {

    private ToolResults() {
    }

    /** A non-fatal tool failure. */
    static Map<String, Object> error(String message) {
        return Map.of("status", "error", "message", message);
    }

    /** A real tool failure: the harness renders the call as failed. */
    static Map<String, Object> fatal(String message) {
        return Map.of("status", "error", "message", message, "fatal", true);
    }

    /** A missing service that backs the tool; always fatal. */
    static Map<String, Object> unavailable(String control) {
        return fatal(control + " not available");
    }

    /** A missing required argument, e.g. {@code required("filePath")}. */
    static Map<String, Object> required(String field) {
        return error(field + " is required");
    }
}
