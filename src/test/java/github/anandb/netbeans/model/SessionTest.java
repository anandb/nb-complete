package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionTest {

    @Test
    void testSessionCreation() {
        Session session = new Session("id123", "Title", "/path/to/cwd", "/path/to/dir",
                null, null, null, null, null, null);
        assertEquals("id123", session.id());
        assertEquals("Title", session.title());
        assertEquals("/path/to/cwd", session.cwd());
        assertEquals("/path/to/dir", session.directory());
    }

    @Test
    void testEffectiveDirectory() {
        Session sessionWithCwd = new Session("1", "T", "/some/cwd", null, null, null, null, null, null, null);
        assertEquals("/some/cwd", sessionWithCwd.effectiveDirectory());

        Session sessionWithDir = new Session("2", "T", null, "/some/dir", null, null, null, null, null, null);
        assertEquals("/some/dir", sessionWithDir.effectiveDirectory());
    }

    @Test
    void testSessionConfigOption() {
        SessionConfigOption option = new SessionConfigOption("opt1", "label", "desc", "category", "text", "val", null);
        assertEquals("opt1", option.id());
        assertEquals("category", option.category());
        assertEquals("label", option.name());
        assertEquals("desc", option.description());
        assertEquals("val", option.currentValue());
        assertNull(option.options());
    }

    @Test
    void projectNameUsesTheEffectiveDirectory() {
        assertEquals("proj", new Session("1", "T", "/work/proj", null, null, null,
                null, null, null, null).projectName());
        assertEquals("proj", new Session("2", "T", null, "/work/proj/", null, null,
                null, null, null, null).projectName());
        assertNull(new Session("3", "T", null, " ", null, null, null, null, null, null)
                .projectName());
        assertNull(new Session("4", "T", null, null, null, null, null, null, null, null)
                .projectName());
    }

    @Test
    void withModelsAndModesKeepsSessionIdentity() {
        Session session = new Session("id", "Title", "/cwd", "/dir", "parent", "now",
                null, null, null, null);
        ModelsInfo models = new ModelsInfo(List.of(), "model-1");
        ModesInfo modes = new ModesInfo(List.of(), "mode-1");

        Session copy = session.withModelsAndModes(models, modes);

        assertEquals(session.id(), copy.id());
        assertEquals(session.title(), copy.title());
        assertEquals(session.cwd(), copy.cwd());
        assertEquals(session.directory(), copy.directory());
        assertEquals(session.parentID(), copy.parentID());
        assertEquals(session.updatedAt(), copy.updatedAt());
        assertEquals(models, copy.models());
        assertEquals(modes, copy.modes());
    }
}
