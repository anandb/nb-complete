package github.anandb.netbeans.ui.options;

import org.netbeans.spi.options.OptionsPanelController;


@OptionsPanelController.TopLevelRegistration(
        id = ACPOptionsPanelController.OPTIONS_CATEGORY_ID,
        categoryName = "#OptionsCategory_Name_Assistant",
        iconBase = "github/anandb/netbeans/ui/icons/logo.svg",
        keywords = "#OptionsCategory_Keywords_Assistant",
        keywordsCategory = "BeanBot",
        position = 1100
)
public final class ACPOptionsPanelController extends AbstractOptionsPanelController {

    /**
     * Top-level options category id, passed to {@code OptionsDisplayer.open}. The
     * registration id is optional, so naming it here (and passing it to the annotation)
     * keeps the id and the registration in one place; a derived name drifts when the
     * class moves package.
     */
    public static final String OPTIONS_CATEGORY_ID = "github-anandb-netbeans-ui-options-ACPOptionsPanelController";

    private transient ACPOptionsPanel panel;

    @Override
    OptionsPanel getPanel() {
        if (panel == null) {
            panel = new ACPOptionsPanel(this);
        }
        return panel;
    }
}
