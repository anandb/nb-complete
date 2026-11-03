package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Pins the UNC repair applied at the session cwd funnels. */
class ProjectPathsTest {

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
