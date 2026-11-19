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
     * @return {@link Outcome#ok()} when the viewer was opened; the error
     *         otherwise, with {@code vcsFailure} marking a broken VCS command
     */
    Outcome openDiffRange(File repoDir, String fromRevision, String toRevision, Integer lastCommits);

    /**
     * One open request's outcome: a null error means the diff was opened.
     * {@code vcsFailure} marks a timed-out, truncated or nonzero VCS command —
     * the command itself is broken, so tools report it as fatal rather than
     * retrying the same arguments.
     */
    record Outcome(String error, boolean vcsFailure) {
        /** The diff opened. */
        public static Outcome ok() {
            return new Outcome(null, false);
        }

        /** The request was invalid (bad revision, no repository, no range). */
        public static Outcome invalid(String message) {
            return new Outcome(message, false);
        }

        /** A VCS subprocess failed, timed out or was truncated. */
        public static Outcome vcs(String message) {
            return new Outcome(message, true);
        }
    }
}
