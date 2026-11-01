package github.anandb.netbeans.support;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedPathsTest {

    @Test
    void vcsAndMavenDirectoriesAreProtected(@TempDir File project) {
        assertTrue(ProtectedPaths.isProtected(new File(project, ".git/hooks/pre-commit").getPath()));
        assertTrue(ProtectedPaths.isProtected(new File(project, ".git/config").getPath()));
        assertTrue(ProtectedPaths.isProtected(new File(project, ".hg/hgrc").getPath()));
        assertTrue(ProtectedPaths.isProtected(new File(project, ".mvn/jvm.config").getPath()));
        assertTrue(ProtectedPaths.isProtected(new File(project, ".mvn").getPath()));
    }

    @Test
    void ordinaryProjectFilesAreNotProtected(@TempDir File project) {
        assertFalse(ProtectedPaths.isProtected(new File(project, "src/Main.java").getPath()));
        assertFalse(ProtectedPaths.isProtected(new File(project, ".gitignore").getPath()));
        assertFalse(ProtectedPaths.isProtected(new File(project, "pom.xml").getPath()));
        assertFalse(ProtectedPaths.isProtected(null));
        assertFalse(ProtectedPaths.isProtected(""));
    }
}
