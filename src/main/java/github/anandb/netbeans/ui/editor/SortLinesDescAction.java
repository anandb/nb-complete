package github.anandb.netbeans.ui.editor;

import java.util.Comparator;
import org.openide.util.NbBundle;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import github.anandb.netbeans.ui.UIBundle;

@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SortLinesDescAction")
@ActionRegistration(displayName = "#CTL_SortLinesDescAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 301)
public class SortLinesDescAction extends BaseSortLinesAction {

    @Override
    protected Comparator<String> getComparator() {
        return Comparator.reverseOrder();
    }

    @Override
    protected String getDisplayName() {
        return NbBundle.getMessage(UIBundle.class, "CTL_SortLinesDescAction");
    }
}
