package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Policy matrix shared by the ACP fs tools and the internal MCP file tools. */
class FileAccessGuardTest {

    private static Predicate<String> inside(String projectRoot) {
        return path -> FileAccessGuard.isInsideProject(projectRoot, path);
    }

    private static final Predicate<String> ALWAYS_INSIDE = path -> true;

    @Test
    void allowConfinesReadsAndWritesToTheProject(@TempDir Path dir) {
        String project = dir.resolve("project").toString();

        assertEquals(FileAccessGuard.Denial.ALLOWED,
                FileAccessGuard.check(AcpFsPolicy.ALLOW, project + "/src/Main.java", false, inside(project)));
        assertEquals(FileAccessGuard.Denial.ALLOWED,
                FileAccessGuard.check(AcpFsPolicy.ALLOW, project + "/src/Main.java", true, inside(project)));
        assertEquals(FileAccessGuard.Denial.OUTSIDE_PROJECT,
                FileAccessGuard.check(AcpFsPolicy.ALLOW, dir.resolve("other.txt").toString(), false,
                        inside(project)));
        assertEquals(FileAccessGuard.Denial.OUTSIDE_PROJECT,
                FileAccessGuard.check(AcpFsPolicy.ALLOW, dir.resolve("other.txt").toString(), true,
                        inside(project)));
    }

    @Test
    void readOnlyDeniesWritesButAllowsProjectReads(@TempDir Path dir) {
        String project = dir.toString();

        assertEquals(FileAccessGuard.Denial.ALLOWED,
                FileAccessGuard.check(AcpFsPolicy.READ_ONLY, project + "/a.txt", false, inside(project)));
        assertEquals(FileAccessGuard.Denial.WRITE_DENIED,
                FileAccessGuard.check(AcpFsPolicy.READ_ONLY, project + "/a.txt", true, inside(project)));
    }

    @Test
    void readAnyAllowsExternalReadsAndStillConfinesWrites(@TempDir Path dir) {
        String project = dir.resolve("project").toString();
        String outside = dir.resolve("outside.txt").toString();

        assertEquals(FileAccessGuard.Denial.ALLOWED,
                FileAccessGuard.check(AcpFsPolicy.READ_ANY_WRITE_PROJECT, outside, false, inside(project)));
        assertEquals(FileAccessGuard.Denial.OUTSIDE_PROJECT,
                FileAccessGuard.check(AcpFsPolicy.READ_ANY_WRITE_PROJECT, outside, true, inside(project)));
        assertEquals(FileAccessGuard.Denial.ALLOWED,
                FileAccessGuard.check(AcpFsPolicy.READ_ANY_WRITE_PROJECT, project + "/a.txt", true,
                        inside(project)));
    }

    @Test
    void secretFilesAreDeniedForReadAndWrite() {
        assertEquals(FileAccessGuard.Denial.SECRET_FILE,
                FileAccessGuard.check(AcpFsPolicy.READ_ANY_WRITE_PROJECT, "/tmp/.env", false, ALWAYS_INSIDE));
        assertEquals(FileAccessGuard.Denial.SECRET_FILE,
                FileAccessGuard.check(AcpFsPolicy.ALLOW, "/tmp/.env", true, ALWAYS_INSIDE));
    }

    @Test
    void protectedDirectoriesAreDeniedForWritesOnly() {
        assertEquals(FileAccessGuard.Denial.PROTECTED_PATH,
                FileAccessGuard.check(AcpFsPolicy.ALLOW, "/tmp/.git/hooks/pre-commit", true, ALWAYS_INSIDE));
        assertEquals(FileAccessGuard.Denial.ALLOWED,
                FileAccessGuard.check(AcpFsPolicy.ALLOW, "/tmp/.git/hooks/pre-commit", false, ALWAYS_INSIDE));
    }

    @Test
    void nullPolicyBehavesLikeAllow() {
        assertEquals(FileAccessGuard.Denial.OUTSIDE_PROJECT,
                FileAccessGuard.check(null, "/tmp/x.txt", false, ALWAYS_INSIDE.negate()));
    }

    @Test
    void isInsideProjectUsesPathBoundariesAndResolvesParentSegments(@TempDir Path dir) {
        Path project = dir.resolve("project");
        assertTrue(FileAccessGuard.isInsideProject(project.toString(), project.toString()));
        assertTrue(FileAccessGuard.isInsideProject(project.toString(),
                project.resolve("src/Main.java").toString()));
        assertFalse(FileAccessGuard.isInsideProject(project.toString(),
                dir.resolve("project-other").toString()),
                "a sibling sharing the prefix is outside the project");
        assertFalse(FileAccessGuard.isInsideProject(project.toString(),
                project.resolve("../escape.txt").toString()));
    }
}
