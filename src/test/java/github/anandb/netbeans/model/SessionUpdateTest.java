package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionUpdateTest {

    @Test
    void testSessionUpdateStructure() {
        SessionUpdate.Params params = new SessionUpdate.Params("session1", null);
        SessionUpdate update = new SessionUpdate("2.0", "session/update", params);

        assertEquals("2.0", update.jsonrpc());
        assertEquals("session/update", update.method());
        assertEquals("session1", update.params().sessionId());
        assertNull(update.update());
    }

    @Test
    void testNestedMessageInUpdate() {
        Message msg = new Message("m1", "thought", null, null, "thinking");
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(
            "message", null, "m1", null, msg, null, null, true, null, null, null, null, null, null, null, null
        );
        SessionUpdate.Params params = new SessionUpdate.Params("s1", data);
        SessionUpdate update = new SessionUpdate("2.0", "session/update", params);

        assertNotNull(update.message());
        assertEquals("m1", update.message().id());
        assertEquals("thought", update.message().type());
    }
}
