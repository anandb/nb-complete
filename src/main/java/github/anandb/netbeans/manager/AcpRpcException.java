package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.contract.RpcFailure;

/**
 * JSON-RPC error response returned by the ACP server, preserving the numeric
 * error code and the {@code data} the server attached, so callers can distinguish
 * a missing method ({@code -32601}) from an authentication failure or an exhausted
 * credit balance instead of matching on the message text.
 */
public final class AcpRpcException extends RuntimeException implements RpcFailure {

    private static final long serialVersionUID = 1L;

    /** JSON-RPC "Method not found". */
    public static final int METHOD_NOT_FOUND = -32601;

    /** JSON-RPC "Internal error". */
    public static final int INTERNAL_ERROR = RpcFailure.INTERNAL_ERROR;

    private final int code;
    private final JsonNode data;

    public AcpRpcException(int code, String message) {
        this(code, message, null);
    }

    /**
     * @param data the {@code error.data} node: an object for goose's classified failures, a
     *             bare string for its plain {@code invalid_params} messages, or null.
     */
    public AcpRpcException(int code, String message, JsonNode data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    @Override
    public int code() {
        return code;
    }

    /** The raw {@code error.data} node, or null when the server sent none. */
    public JsonNode data() {
        return data;
    }

    @Override
    public String dataField(String field) {
        if (data == null || field == null) {
            return null;
        }
        JsonNode value = data.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    /** True when the server does not implement the requested method. */
    public boolean isMethodNotFound() {
        return code == METHOD_NOT_FOUND;
    }
}
