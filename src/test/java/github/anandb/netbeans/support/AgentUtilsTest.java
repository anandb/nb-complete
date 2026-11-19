package github.anandb.netbeans.support;

import java.io.Closeable;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentUtilsTest {

    @Test
    void readsBundledUtf8ResourcesAndReturnsNullWhenMissing() {
        String rules = AgentUtils.readResource("critical_rules.md");
        assertNotNull(rules);
        assertTrue(rules.contains("Critical"));
        assertNull(AgentUtils.readResource("resource-that-does-not-exist.txt"));
    }

    @Test
    void closeQuietlyIgnoresNullAndCloseFailures() {
        AgentUtils.closeQuietly(null);
        AgentUtils.closeQuietly(new Closeable() {
            @Override
            public void close() throws IOException {
                throw new IOException("expected");
            }
        });
    }
}
