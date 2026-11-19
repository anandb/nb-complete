package github.anandb.netbeans.support;

import java.io.IOException;

/**
 * A VCS subprocess failed, timed out or produced truncated output: the command
 * itself is broken, not the request's arguments. MCP tools report this class
 * of failure as fatal ({@code fatal: true}) — retrying the same arguments
 * cannot succeed.
 */
public class VcsCommandException extends IOException {

    public VcsCommandException(String message) {
        super(message);
    }
}
