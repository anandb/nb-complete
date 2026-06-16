package github.anandb.netbeans.ui;

import java.awt.Component;
import javax.swing.JPanel;

class BubbleAccordionManager {

    private final JPanel segments;

    BubbleAccordionManager(JPanel segments) {
        this.segments = segments;
    }

    public void registerWithAccordionGroup(AccordionGroup group) {
        if (segments.getComponentCount() > 0) {
            Component first = segments.getComponent(0);
            if (first instanceof CollapsibleToolPane toolPane) {
                toolPane.setAccordionGroup(group);
            } else if (first instanceof CollapsibleActivityPane activityPane) {
                activityPane.setAccordionGroup(group);
            }
        }
    }

    public void setExpanded(boolean expanded) {
        if (segments.getComponentCount() > 0) {
            Component first = segments.getComponent(0);
            if (first instanceof CollapsibleToolPane pane) {
                pane.setExpanded(expanded);
            } else if (first instanceof CollapsibleActivityPane pane) {
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
