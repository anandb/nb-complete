package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/** Pins the UNC repair applied at the session cwd funnels. */
class ProjectPathsTest {

    @Test
    void nativePathUsesTheLocalFileOfAFileObject(@TempDir File dir) throws Exception {
        FileObject fo = FileUtil.toFileObject(dir);

        assertEquals(dir.getAbsolutePath(), ProjectPaths.nativePath(fo));
        assertNull(ProjectPaths.nativePath((FileObject) null));
    }

    @Test
    void relativeToUsesASeparatorBoundary(@TempDir File root) {
        File inside = new File(root, "src/Main.java");
        File sibling = new File(root.getParentFile(), root.getName() + "-sibling/Main.java");

        assertEquals("src/Main.java", ProjectPaths.relativeTo(root, inside));
        assertNull(ProjectPaths.relativeTo(root, sibling));
        assertNull(ProjectPaths.relativeTo(root, root));
        assertNull(ProjectPaths.relativeTo(null, inside));
        assertNull(ProjectPaths.relativeTo(root, null));
    }

    @Test
    void canonicalPathResolvesAndIsNullSafe(@TempDir File dir) throws Exception {
        File file = new File(dir, "a/../b.txt");

        assertNull(ProjectPaths.canonicalPath(null));
        assertEquals(file.getCanonicalPath(), ProjectPaths.canonicalPath(file.getPath()));
    }

    @Test
    void windowsVirtualUncRootBecomesNative() {
        assertEquals("\\\\SRV1/PROJETS/www/mytest",
                ProjectPaths.nativeForm("//SRV1/PROJETS/www/mytest", '\\'));
    }

    @Test
    void otherWindowsFormsAreLeftAlone() {
        assertEquals("C:/work/proj", ProjectPaths.nativeForm("C:/work/proj", '\\'));
        assertEquals("\\\\SRV1\\PROJETS", ProjectPaths.nativeForm("\\\\SRV1\\PROJETS", '\\'));
    }

    @Test
    void posixPathsAreLeftAlone() {
        // A leading double slash is not a UNC root off Windows.
        assertEquals("//SRV1/PROJETS", ProjectPaths.nativeForm("//SRV1/PROJETS", '/'));
        assertEquals("/home/me/proj", ProjectPaths.nativeForm("/home/me/proj", '/'));
    }

    @Test
    void repairIsIdempotentAndNullSafe() {
        String once = ProjectPaths.nativeForm("//SRV1/PROJETS", '\\');
        assertEquals(once, ProjectPaths.nativeForm(once, '\\'));
        assertNull(ProjectPaths.nativeForm(null, '\\'));
    }
}
