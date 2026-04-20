package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionTest {

    @Test
    void testSessionCreation() {
        Session session = new Session("id123", "Title", "/path/to/cwd", "/path/to/dir", null, null, null, null);
        assertEquals("id123", session.id());
        assertEquals("Title", session.title());
        assertEquals("/path/to/cwd", session.cwd());
        assertEquals("/path/to/dir", session.directory());
    }

    @Test
    void testEffectiveDirectory() {
        Session sessionWithCwd = new Session("1", "T", "/some/cwd", null, null, null, null, null);
        assertEquals("/some/cwd", sessionWithCwd.effectiveDirectory());

        Session sessionWithDir = new Session("2", "T", null, "/some/dir", null, null, null, null);
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
}
