package github.anandb.netbeans.manager;

/**
 * Signals an expected, client-facing error: either a rejection (e.g. fs/write
 * disabled, path outside project, missing parameter) or an operation failure
 * that the handler has already logged with its root cause. These are not
 * internal errors: {@link AcpProtocolClient} logs them once without a stack
 * trace and returns them to the client, instead of re-logging a full stack
 * trace for every failure.
 */
class RequestRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    RequestRejectedException(String message) {
        super(message);
    }
}
