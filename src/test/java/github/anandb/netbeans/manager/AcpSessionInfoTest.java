package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcpSessionInfoTest {

    @Test
    void harnessIdStripsScopePrefix() {
        AcpSessionInfo info = AcpSessionInfo.getInstance();
        try {
            info.setHarnessInfo("@geohar/pi-acp", "1.0");
            assertEquals("pi-acp", info.getHarnessId());

            info.setHarnessInfo("goose", "1.0");
            assertEquals("goose", info.getHarnessId());

            info.setHarnessInfo("scope/nested/name", "1.0");
            assertEquals("name", info.getHarnessId());
        } finally {
            info.setHarnessInfo(null, null);
        }
    }

    @Test
    void missingHarnessNameReadsAsUnknown() {
        AcpSessionInfo info = AcpSessionInfo.getInstance();
        info.setHarnessInfo(null, null);

        assertEquals("unknown", info.getHarnessId());
    }
}
