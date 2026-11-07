package github.anandb.netbeans.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Guards the YAML catalog against the definitions it replaced. The expected
 * snapshot was generated from the Java catalog before the conversion; any
 * difference in a field, order, activation or binary lookup fails here.
 */
class HarnessCatalogParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void yamlCatalogMatchesTheJavaDefinitionSnapshot() throws Exception {
        JsonNode expected;
        try (InputStream in = HarnessCatalogParityTest.class.getResourceAsStream("harness-catalog.expected.json")) {
            expected = MAPPER.readTree(in);
        }
        JsonNode actual = MAPPER.readTree(MAPPER.writeValueAsString(snapshot()));
        assertEquals(expected, actual);
    }

    @Test
    void openCodeV2IsDerivedAndNotListed() {
        assertEquals(true, HarnessCatalog.OPENCODE_V2.requiresMessageQueue());
        assertSame(HarnessCatalog.OPENCODE_V2,
                HarnessCatalog.forAgentVersion(HarnessCatalog.OPENCODE, "2.0.20"));
        assertSame(HarnessCatalog.OPENCODE,
                HarnessCatalog.forAgentVersion(HarnessCatalog.OPENCODE, "1.27.0"));
        assertEquals(false, HarnessCatalog.ALL.contains(HarnessCatalog.OPENCODE_V2));
    }

    private static Map<String, Object> snapshot() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> all = new ArrayList<>();
        for (Harness h : HarnessCatalog.ALL) {
            all.add(fields(h));
        }
        root.put("all", all);
        root.put("unknown", fields(HarnessCatalog.UNKNOWN));

        Map<String, Object> activations = new TreeMap<>();
        for (Harness h : HarnessCatalog.ALL) {
            HarnessCatalog.Activation a = HarnessCatalog.activation(h.id());
            activations.put(h.id(), Map.of("command", a.command(), "hint", a.hint()));
        }
        root.put("activations", activations);

        Map<String, Object> binaryLookups = new TreeMap<>();
        for (Harness h : HarnessCatalog.ALL) {
            for (String name : h.binaryNames()) {
                binaryLookups.put(name, HarnessCatalog.byBinaryName(name).id());
            }
        }
        binaryLookups.put("omp.exe", HarnessCatalog.byBinaryName("omp.exe").id());
        binaryLookups.put("OMP.EXE", HarnessCatalog.byBinaryName("OMP.EXE").id());
        binaryLookups.put("missing-agent", HarnessCatalog.byBinaryName("missing-agent").id());
        root.put("binaryLookups", binaryLookups);

        root.put("sortedIds", HarnessCatalog.sortedByName().stream().map(Harness::id).toList());
        return root;
    }

    private static Map<String, Object> fields(Harness h) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (RecordComponent rc : Harness.class.getRecordComponents()) {
            Field f = Harness.class.getDeclaredField(rc.getName());
            f.setAccessible(true);
            m.put(rc.getName(), f.get(h));
        }
        return m;
    }
}
