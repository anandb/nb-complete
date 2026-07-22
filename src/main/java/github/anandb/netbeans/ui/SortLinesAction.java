package github.anandb.netbeans.ui;

import java.util.Comparator;
import org.openide.util.NbBundle;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;

@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SortLinesAction")
@ActionRegistration(displayName = "#CTL_SortLinesAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 300)
public class SortLinesAction extends BaseSortLinesAction {

    @Override
    protected Comparator<String> getComparator() {
        return null;
    }

    @Override
    protected String getDisplayName() {
        return NbBundle.getMessage(SortLinesAction.class, "CTL_SortLinesAction");
    }
}
