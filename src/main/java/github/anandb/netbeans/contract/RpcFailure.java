package github.anandb.netbeans.contract;

/**
 * A failed JSON-RPC call as far as a consumer needs to see it: the numeric code and the
 * {@code data} the server attached to the error. The manager's protocol exception
 * implements this, so the UI can classify a failure without importing the transport.
 */
public interface RpcFailure {

    /** JSON-RPC error code for "authentication required" (goose: {@code data.reason=turn_failed}). */
    int AUTH_REQUIRED = -32000;

    /** JSON-RPC "Internal error". */
    int INTERNAL_ERROR = -32603;

    /** {@code data.reason} of a prompt that failed after the turn started. */
    String REASON_TURN_FAILED = "turn_failed";

    /** {@code data.reason} of a prompt rejected for exhausted provider credits or quota. */
    String REASON_CREDITS_EXHAUSTED = "credits_exhausted";

    /** The JSON-RPC error code, or 0 when the server sent none. */
    int code();

    /**
     * Text value of {@code data.<field>}, or null when the error carries no {@code data}
     * object, or the field is absent or not textual.
     */
    String dataField(String field);
}
