package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcpFsPolicyTest {

    @Test
    void storedValuesRoundTrip() {
        for (AcpFsPolicy policy : AcpFsPolicy.values()) {
            assertEquals(policy, AcpFsPolicy.fromStoredValue(policy.storedValue()));
        }
    }

    @Test
    void unknownAndRetiredValuesFallBackToTheDefault() {
        assertEquals(AcpFsPolicy.DEFAULT, AcpFsPolicy.fromStoredValue(null));
        assertEquals(AcpFsPolicy.DEFAULT, AcpFsPolicy.fromStoredValue(""));
        assertEquals(AcpFsPolicy.DEFAULT, AcpFsPolicy.fromStoredValue("deny"),
                "the retired deny mode must fall back to the default");
        assertEquals(AcpFsPolicy.READ_ONLY, AcpFsPolicy.fromStoredValue("Read-Only"));
    }
}
