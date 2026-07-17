package github.anandb.netbeans.ui;

import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;

@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SortLinesAction")
@ActionRegistration(displayName = "#CTL_SortLinesAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 300)
public class SortLinesAction extends BaseSortLinesAction {

    @Override
    protected java.util.Comparator<String> getComparator() {
        return null;
    }

    @Override
    protected String getDisplayName() {
        return org.openide.util.NbBundle.getMessage(SortLinesAction.class, "CTL_SortLinesAction");
    }
}
