package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.ModelRecords.ConfigItem;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * The model dropdown is built from the parsed variant map, so a model id must only
 * be collapsed into a base model when its trailing path segment is an effort level
 * the server actually declares. Namespaced ids (Pi's {@code lm-studio/qwen/...})
 * keep their full id; collapsing them hides real models behind a bogus entry.
 */
class ModelVariantResolverTest {

    private static final Set<String> PI_EFFORTS = Set.of("off", "auto", "low", "medium", "high");

    private static SessionConfigOption modelOption(String... values) {
        List<SessionConfigSelectOption> options = new ArrayList<>();
        for (String v : values) {
            options.add(new SessionConfigSelectOption(v, v, null));
        }
        return new SessionConfigOption("model", "Model", null, "model", "select", values[0], options);
    }

    /** Model option whose entries carry explicit display names: {value, name} pairs. */
    private static SessionConfigOption namedModelOption(String[]... pairs) {
        List<SessionConfigSelectOption> options = new ArrayList<>();
        for (String[] p : pairs) {
            options.add(new SessionConfigSelectOption(p[0], p[1], null));
        }
        return new SessionConfigOption("model", "Model", null, "model", "select", pairs[0][0], options);
    }

    private static LinkedHashMap<String, List<ConfigItem>> parse(SessionConfigOption opt, Set<String> tokens) {
        try (MockedStatic<PlatformBridge> bridge = Mockito.mockStatic(PlatformBridge.class)) {
            bridge.when(PlatformBridge::sessionServiceSafe)
                    .thenReturn((SessionService) () -> mock(SessionControl.class));
            ModelVariantResolver resolver = new ModelVariantResolver();
            resolver.parseModelVariants(opt, tokens);
            return resolver.getModelVariants();
        }
    }

    @Test
    void namespacedModelIdsKeepTheirFullId() {
        LinkedHashMap<String, List<ConfigItem>> variants = parse(modelOption(
                "lm-studio/qwen/qwen3.5-9b",
                "lm-studio/google/gemma-4-12b-qat",
                "opencode-go/glm-5.3-flash"), PI_EFFORTS);

        assertEquals(List.of(
                "lm-studio/qwen/qwen3.5-9b",
                "lm-studio/google/gemma-4-12b-qat",
                "opencode-go/glm-5.3-flash"), List.copyOf(variants.keySet()));
    }

    @Test
    void declaredEffortTailCollapsesIntoItsBaseModel() {
        LinkedHashMap<String, List<ConfigItem>> variants = parse(modelOption(
                "opencode-go/gpt-5.6-luna/high",
                "opencode-go/gpt-5.6-luna/low"), Set.of("low", "medium", "high"));

        assertEquals(List.of("opencode-go/gpt-5.6-luna"), List.copyOf(variants.keySet()));
        assertEquals(List.of("high", "low"),
                variants.get("opencode-go/gpt-5.6-luna").stream().map(ConfigItem::name).toList());
    }

    @Test
    void undeclaredEffortsKeepEachVariantIndividuallySelectable() {
        LinkedHashMap<String, List<ConfigItem>> variants = parse(namedModelOption(
                new String[]{"opencode-go/gpt-5.6-luna/high", "GPT 5.6 Luna (High)"},
                new String[]{"opencode-go/gpt-5.6-luna/low", "GPT 5.6 Luna (Low)"}), Set.of());

        assertEquals(List.of("opencode-go/gpt-5.6-luna/high", "opencode-go/gpt-5.6-luna/low"),
                List.copyOf(variants.keySet()));
        assertEquals("GPT 5.6 Luna (High)",
                variants.get("opencode-go/gpt-5.6-luna/high").get(0).baseName(),
                "without an effort option the parenthetical is what distinguishes the rows");
    }

    @Test
    void collapsedVariantUsesStrippedBaseName() {
        LinkedHashMap<String, List<ConfigItem>> variants = parse(namedModelOption(
                new String[]{"opencode-go/gpt-5.6-luna/high", "GPT 5.6 Luna (High)"},
                new String[]{"opencode-go/gpt-5.6-luna/low", "GPT 5.6 Luna (Low)"}), Set.of("low", "high"));

        assertEquals(List.of("opencode-go/gpt-5.6-luna"), List.copyOf(variants.keySet()));
        assertEquals("GPT 5.6 Luna", variants.get("opencode-go/gpt-5.6-luna").get(0).baseName(),
                "the collapsed base item drops the effort suffix");
    }
}
