package github.anandb.netbeans.ui.chat;

import java.awt.Component;
import javax.swing.JPanel;
import github.anandb.netbeans.ui.components.AccordionGroup;

// DSL-LEAF: not a controller — manages accordion grouping of activity/tool
// sub-panes inside a bubble. Migration target: BubbleAccordionSpec; group
// toggle/bookmark logic stays imperative.
class BubbleAccordionManager {

    private final JPanel segments;

    BubbleAccordionManager(JPanel segments) {
        this.segments = segments;
    }

    public void registerWithAccordionGroup(AccordionGroup group) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleToolPane toolPane) {
                toolPane.setAccordionGroup(group);
            } else if (c instanceof CollapsibleActivityPane activityPane) {
                activityPane.setAccordionGroup(group);
            }
        }
    }

    public void setExpanded(boolean expanded) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleToolPane pane) {
                pane.setExpanded(expanded);
            } else if (c instanceof CollapsibleActivityPane pane) {
                pane.setExpanded(expanded);
            }
        }
    }

    public void toggleAllBlocks(boolean expanded) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleCodePane codePane) {
                codePane.setExpanded(expanded);
            } else if (c instanceof CollapsibleToolPane toolPane) {
                toolPane.setExpanded(expanded);
            } else if (c instanceof CollapsibleActivityPane activityPane) {
                activityPane.setExpanded(expanded);
            }
        }
    }
}
