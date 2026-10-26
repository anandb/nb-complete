package github.anandb.netbeans.manager;

/**
 * JSON-RPC error response returned by the ACP server, preserving the numeric
 * error code so callers can distinguish a missing method ({@code -32601}) from
 * a genuine failure instead of matching on the message text.
 */
public final class AcpRpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** JSON-RPC "Method not found". */
    public static final int METHOD_NOT_FOUND = -32601;

    private final int code;

    public AcpRpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }

    /** True when the server does not implement the requested method. */
    public boolean isMethodNotFound() {
        return code == METHOD_NOT_FOUND;
    }
}
