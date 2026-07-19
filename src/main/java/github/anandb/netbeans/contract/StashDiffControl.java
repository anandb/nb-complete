package github.anandb.netbeans.contract;

import java.io.File;

/**
 * Port for opening stash diffs from non-UI layers (e.g. MCP tools).
 * Implementations live in {@code ui/}; consumers look up via {@code Lookup}.
 */
public interface StashDiffControl {

    /** Open the stash diff viewer for the given stash index. */
    void openStashDiff(File repoDir, int stashIndex);

    /** Validate that a stash exists and return a human-readable error, or {@code null} if valid. */
    String validateStash(File repoDir, int stashIndex);
}
