package github.anandb.netbeans.support;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.lang3.exception.ExceptionUtils;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Utility for performing version control system operations (e.g. Git stashing).
 */
public class VcsUtils {

    public enum VcsType { GIT, HG }
    public record VcsInfo(File rootDir, VcsType type) {}

    private static final Logger LOG = Logger.from(VcsUtils.class);

    private VcsUtils() {
        // Utility class
    }

    /**
     * Walks up the directory tree to find the VCS root (e.g. directory containing .git or .hg).
     *
     * @param startDir the directory to start searching from
     * @return the VCS info, or null if not found
     */
    public static VcsInfo findVcsInfo(String startDir) {
        if (startDir == null || startDir.isBlank()) {
            return null;
        }
        File current = new File(startDir);
        while (current != null && current.isDirectory()) {
            if (new File(current, ".git").exists()) {
                return new VcsInfo(current, VcsType.GIT);
            }
            if (new File(current, ".hg").exists()) {
                return new VcsInfo(current, VcsType.HG);
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * Checks if there are uncommitted changes (including untracked files) in the VCS repository.
     *
     * @param vcsInfo the VCS info
     * @return true if there are changes, false otherwise
     */
    public static boolean hasUncommittedChanges(VcsInfo vcsInfo) {
        if (vcsInfo == null) return false;

        try {
            ProcessBuilder pb;
            if (vcsInfo.type() == VcsType.HG) {
                pb = new ProcessBuilder("hg", "status");
            } else {
                pb = new ProcessBuilder("git", "status", "--porcelain");
            }
            pb.directory(vcsInfo.rootDir());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        p.destroyForcibly();
                        return true;
                    }
                }
            }

            p.waitFor(5, TimeUnit.SECONDS);
            return false;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to check for uncommitted changes: {0}", ExceptionUtils.getMessage(e));
            return false; // Safely fail so we don't attempt to stash in a broken state
        }
    }

    /**
     * Backs up uncommitted changes using git stash or hg shelve.
     *
     * @param vcsInfo the VCS info
     * @return a message describing the backup action, or null if no backup was needed or an error occurred.
     */
    public static String backupUncommittedChanges(VcsInfo vcsInfo) {
        if (vcsInfo == null || !hasUncommittedChanges(vcsInfo)) {
            return null;
        }

        try {
            File vcsRoot = vcsInfo.rootDir();

            if (vcsInfo.type() == VcsType.HG) {
                return backupMercurial(vcsRoot);
            }

            // Get changed files for the git stash message
            ProcessBuilder diffPb = new ProcessBuilder("git", "diff", "--name-only", "HEAD");
            diffPb.directory(vcsRoot);
            diffPb.redirectErrorStream(true);
            Process diffProc = diffPb.start();

            List<String> files = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(diffProc.getInputStream()))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 3) {
                    if (!line.trim().isEmpty()) {
                        String name = new File(line.trim()).getName();
                        files.add(name);
                        count++;
                    }
                }
            }
            diffProc.waitFor(5, TimeUnit.SECONDS);

            String fileList = String.join(", ", files);
            if (files.size() == 3) {
                fileList += "...";
            }
            if (fileList.isEmpty()) {
                fileList = "untracked files";
            }

            String stashMsg = "beanbot: " + fileList;

            // 1. Get HEAD
            String head = execGit(vcsRoot, null, null, "git", "rev-parse", "HEAD");
            if (head.isEmpty()) return null;

            // 2. Index commit
            String iTree = execGit(vcsRoot, null, null, "git", "write-tree");
            String iCommit = execGit(vcsRoot, null, null, "git", "commit-tree", iTree, "-p", head, "-m", "index on beanbot");

            // 3. Untracked commit
            String untracked = execGit(vcsRoot, null, null, "git", "ls-files", "--others", "--exclude-standard");
            String uCommit = null;
            if (!untracked.isEmpty()) {
                File tmpIndexU = new File(vcsRoot, ".git/beanbot_tmp_index_u");
                if (tmpIndexU.exists()) tmpIndexU.delete();

                execGit(vcsRoot, "GIT_INDEX_FILE=.git/beanbot_tmp_index_u", untracked, "git", "update-index", "--add", "--stdin");
                String uTree = execGit(vcsRoot, "GIT_INDEX_FILE=.git/beanbot_tmp_index_u", null, "git", "write-tree");
                uCommit = execGit(vcsRoot, null, null, "git", "commit-tree", uTree, "-m", "untracked files on beanbot");
                tmpIndexU.delete();
            }

            // 4. Working tree commit
            File tmpIndexW = new File(vcsRoot, ".git/beanbot_tmp_index_w");
            File realIndex = new File(vcsRoot, ".git/index");
            if (realIndex.exists()) {
                Files.copy(realIndex.toPath(), tmpIndexW.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            execGit(vcsRoot, "GIT_INDEX_FILE=.git/beanbot_tmp_index_w", null, "git", "add", "-A");
            String wTree = execGit(vcsRoot, "GIT_INDEX_FILE=.git/beanbot_tmp_index_w", null, "git", "write-tree");
            tmpIndexW.delete();

            // Skip if identical to latest stash
            try {
                String lastStashTree = execGit(vcsRoot, null, null, "git", "rev-parse", "stash@{0}^{tree}");
                if (wTree.equals(lastStashTree)) {
                    LOG.fine("Working tree is identical to stash@{0}, skipping duplicate backup.");
                    return null;
                }
            } catch (Exception e) {
                // Ignore, stash@{0} might not exist
            }

            List<String> commitArgs = new ArrayList<>(List.of("git", "commit-tree", wTree, "-p", head, "-p", iCommit));
            if (uCommit != null) {
                commitArgs.add("-p");
                commitArgs.add(uCommit);
            }
            commitArgs.add("-m");
            commitArgs.add(stashMsg);
            String wCommit = execGit(vcsRoot, null, null, commitArgs.toArray(new String[0]));

            // 5. Store stash
            execGit(vcsRoot, null, null, "git", "stash", "store", "-m", stashMsg, wCommit);

            return stashMsg;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to backup changes: {0}", ExceptionUtils.getMessage(e));
            return null;
        }
    }

    private static String backupMercurial(File vcsRoot) throws Exception {
        // Get changed files for the shelf message
        String hgStatus = execGit(vcsRoot, null, null, "hg", "status", "-amru");
        List<String> files = new ArrayList<>();
        String[] lines = hgStatus.split("\\r?\\n");
        for (String line : lines) {
            if (!line.trim().isEmpty() && files.size() < 3) {
                // hg status outputs like "M path/to/file.ext"
                String path = line.substring(2).trim();
                files.add(new File(path).getName());
            }
        }

        String fileList = String.join(", ", files);
        if (lines.length > 3) {
            fileList += "...";
        }
        if (fileList.isEmpty()) {
            fileList = "changes";
        }

        String shelfMsg = "beanbot: " + fileList;

        // Delete existing beanbot-backup shelf to avoid duplicate error
        try {
            execGit(vcsRoot, null, null, "hg", "shelve", "-d", "beanbot-backup");
        } catch (Exception e) {
            // Ignore, shelf probably didn't exist
        }

        // Shelve changes while keeping working directory intact
        execGit(vcsRoot, null, null, "hg", "shelve", "--keep", "--addremove", "--name", "beanbot-backup", "-m", shelfMsg);

        return shelfMsg;
    }

    private static String execGit(File cwd, String env, String stdin, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd);
        if (isNotBlank(env)) {
            String[] parts = env.split("=", 2);
            pb.environment().put(parts[0], parts[1]);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        if (stdin != null) {
            try (OutputStream os = p.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        p.waitFor(10, TimeUnit.SECONDS);
        if (p.exitValue() != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", cmd) + "\nOutput: " + out);
        }
        return out.toString().trim();
    }
}
