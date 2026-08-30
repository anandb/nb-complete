package github.anandb.netbeans.ui;

import org.netbeans.spi.options.OptionsPanelController;


@OptionsPanelController.TopLevelRegistration(
        categoryName = "#OptionsCategory_Name_Assistant",
        iconBase = "github/anandb/netbeans/ui/icons/logo.svg",
        keywords = "#OptionsCategory_Keywords_Assistant",
        keywordsCategory = "Assistant",
        position = 1100
)
public final class ACPOptionsPanelController extends AbstractOptionsPanelController {

    private transient ACPOptionsPanel panel;

    @Override
    OptionsPanel getPanel() {
        if (panel == null) {
            panel = new ACPOptionsPanel(this);
        }
        return panel;
    }
}
