package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.AvailableModel;
import github.anandb.netbeans.model.ModelRecords.ConfigItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@link ConfigPanelController#mergeModelItems}. A
 * {@code config_option_update} carries fewer models than {@code session/load}
 * reported; the dropdown must keep the omitted ones without losing its
 * alphabetical order or the user's selection.
 * <p>
 * Also guards the model-cache write decision in {@code cacheModelList}: a cached
 * list that already covers every incoming id must be kept (never shrink), but an
 * incoming list that introduces an unseen id must update even when it is no
 * larger — otherwise a same-size model swap would be silently dropped.
 */
class ConfigPanelControllerTest {

    private static final ConfigItem ALPHA = new ConfigItem("alpha", "alpha");
    private static final ConfigItem BETA = new ConfigItem("beta", "beta");
    private static final ConfigItem GAMMA = new ConfigItem("gamma", "gamma");
    private static final ConfigItem DELTA = new ConfigItem("delta", "delta");
    private static final ConfigItem EPSILON = new ConfigItem("epsilon", "epsilon");
    private static final ConfigItem ZETA = new ConfigItem("zeta", "zeta");

    private static List<String> names(JComboBox<ConfigItem> combo) {
        List<String> out = new ArrayList<>(combo.getItemCount());
        for (int i = 0; i < combo.getItemCount(); i++) {
            out.add(combo.getItemAt(i).name());
        }
        return out;
    }

    /** The combo holds the freshly parsed incoming rows (as populateComboBox left it). */
    private static JComboBox<ConfigItem> comboOf(ConfigItem... items) {
        JComboBox<ConfigItem> combo = new JComboBox<>();
        for (ConfigItem c : items) {
            combo.addItem(c);
        }
        return combo;
    }

    @Test
    void keepsOmittedModelsAndRestoresAlphabeticalOrder() {
        // populateComboBox sorted the incoming rows: beta, gamma, zeta.
        JComboBox<ConfigItem> combo = comboOf(BETA, GAMMA, ZETA);
        List<ConfigItem> previous = List.of(ALPHA, BETA, GAMMA, DELTA, EPSILON);

        ConfigPanelController.mergeModelItems(combo, previous, null, "delta");

        assertEquals(List.of("alpha", "beta", "delta", "epsilon", "gamma", "zeta"), names(combo),
                "omitted models must come back, and the whole list must stay sorted");
    }

    @Test
    void selectsTheOmittedModelThatWasPreviouslyCurrent() {
        JComboBox<ConfigItem> combo = comboOf(BETA, GAMMA, ZETA);
        List<ConfigItem> previous = List.of(ALPHA, BETA, DELTA);

        ConfigItem pick = ConfigPanelController.mergeModelItems(combo, previous, null, "delta");

        assertEquals(DELTA, pick, "the current model must stay selected even when the update omits it");
    }

    @Test
    void keepsTheIncomingPickWhenTheUpdateContainedIt() {
        JComboBox<ConfigItem> combo = comboOf(BETA, GAMMA);
        List<ConfigItem> previous = List.of(ALPHA, DELTA);

        ConfigItem pick = ConfigPanelController.mergeModelItems(combo, previous, GAMMA, "gamma");

        assertSame(GAMMA, pick);
        assertEquals(List.of("alpha", "beta", "delta", "gamma"), names(combo));
    }

    @Test
    void doesNotDuplicateModelsPresentInBothLists() {
        JComboBox<ConfigItem> combo = comboOf(BETA, GAMMA);
        List<ConfigItem> previous = List.of(BETA, GAMMA);

        ConfigPanelController.mergeModelItems(combo, previous, null, null);

        assertEquals(List.of("beta", "gamma"), names(combo));
    }

    @Test
    void returnsNullWhenNothingMatchesSoCallerFallsBackToFirstRow() {
        JComboBox<ConfigItem> combo = comboOf(BETA);
        ConfigItem pick = ConfigPanelController.mergeModelItems(combo, List.of(ALPHA), null, "missing");
        assertEquals(null, pick);
        assertEquals(List.of("alpha", "beta"), names(combo));
    }

    // -- cacheModelList write decision --

    private static AvailableModel model(String id) {
        return new AvailableModel(id, id, "");
    }

    @Test
    void cacheCoversIncomingWhenEveryIdIsPresent() {
        assertTrue(ConfigPanelController.coversAllIds(
                List.of(model("a"), model("b"), model("c")),
                List.of(model("a"), model("b"))));
    }

    @Test
    void sameSizeSwapWithUnseenIdIsNotCovered() {
        assertFalse(ConfigPanelController.coversAllIds(
                List.of(model("a"), model("b")),
                List.of(model("a"), model("c"))));
    }

    @Test
    void smallerIncomingWithUnseenIdIsNotCovered() {
        assertFalse(ConfigPanelController.coversAllIds(
                List.of(model("a"), model("b"), model("c"), model("d")),
                List.of(model("a"), model("e"))));
    }

    @Test
    void emptyCacheCoversNothing() {
        assertFalse(ConfigPanelController.coversAllIds(List.of(), List.of(model("a"))));
    }
}
