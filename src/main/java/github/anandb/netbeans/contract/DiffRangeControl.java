package github.anandb.netbeans.contract;

import github.anandb.netbeans.stereotype.HexagonalPort;

import java.io.File;

/**
 * Port for opening a multi-file diff between two commits from non-UI layers
 * (e.g. MCP tools). Implementations live in {@code ui/}; consumers look up
 * via {@code Lookup}.
 *
 * <p>Implementations perform read-only VCS commands and therefore must be
 * called off the Event Dispatch Thread.
 */
@HexagonalPort
public interface DiffRangeControl {

    /**
     * Opens a side-by-side diff of every file changed between two revisions.
     *
     * @param repoDir repository root (or any directory inside it)
     * @param fromRevision start revision; ignored when {@code lastCommits} is set
     * @param toRevision end revision, or {@code null} to diff against the
     *        working tree (including untracked files)
     * @param lastCommits when set, start at the oldest of the last N commits
     *        before the end revision instead of using {@code fromRevision}
     * @return {@code null} when the viewer was opened, otherwise a
     *         human-readable error
     */
    String openDiffRange(File repoDir, String fromRevision, String toRevision, Integer lastCommits);
}
