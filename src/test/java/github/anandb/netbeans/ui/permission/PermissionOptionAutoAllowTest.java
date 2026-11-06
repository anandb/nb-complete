package github.anandb.netbeans.ui.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Break mode answers with the narrowest allow option and never escalates scope. */
class PermissionOptionAutoAllowTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode options(String... idKindPairs) {
        var array = MAPPER.createArrayNode();
        for (int i = 0; i < idKindPairs.length; i += 2) {
            array.addObject()
                    .put("optionId", idKindPairs[i])
                    .put("kind", idKindPairs[i + 1])
                    .put("name", idKindPairs[i]);
        }
        return array;
    }

    @Test
    void prefersAllowOnceOverAllowAlways() {
        JsonNode opts = options(
                "allow_always", "allow_always",
                "allow_once", "allow_once",
                "reject", "reject");
        assertEquals("allow_once", PermissionOption.autoAllowId(opts));
    }

    @Test
    void picksPlainAllowWhenNoOnceScopeIsNamed() {
        JsonNode opts = options("yes", "allow", "always_yes", "allow_always");
        assertEquals("yes", PermissionOption.autoAllowId(opts));
    }

    @Test
    void neverEscalatesToAlwaysScope() {
        JsonNode opts = options(
                "allow_always", "allow_always",
                "allow_always_global", "allow_always",
                "switch_bypass", "allow_always",
                "allow_session", "allow_session");
        assertNull(PermissionOption.autoAllowId(opts), "must not silently widen the permission scope");
    }

    @Test
    void missingOptionsFallBackToLegacyAllow() {
        assertEquals("allow", PermissionOption.autoAllowId(null));
        assertEquals("allow", PermissionOption.autoAllowId(MAPPER.createArrayNode()));
        assertEquals("allow", PermissionOption.autoAllowId(MAPPER.getNodeFactory().textNode("not-an-array")));
    }

    @Test
    void rejectOnlyRowYieldsNoAnswer() {
        assertNull(PermissionOption.autoAllowId(options("reject", "reject", "deny", "deny")));
        assertNull(PermissionOption.autoAllowId(options("reject_once", "reject", "allow_once", "reject")));
    }

    @Test
    void nameLookupReturnsOnlyTheSelectedOptionLabel() {
        JsonNode opts = options("allow_once", "allow_once", "reject", "reject");
        assertEquals("allow_once", PermissionOption.autoAllowName(opts, "allow_once"));
        assertNull(PermissionOption.autoAllowName(opts, "missing"));
        assertNull(PermissionOption.autoAllowName(null, "allow_once"));
    }
}
