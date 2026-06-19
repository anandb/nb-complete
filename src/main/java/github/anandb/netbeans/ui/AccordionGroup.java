package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.List;
import github.anandb.netbeans.support.Logger;

/**
 * Manages a group of collapsible panes so that at most one is expanded at a time
 * (accordion behavior). Only user-initiated clicks trigger the accordion;
 * programmatic calls to {@link BaseCollapsiblePane#setExpanded(boolean)} bypass it.
 */
public class AccordionGroup {

    private static final Logger LOG = Logger.from(AccordionGroup.class);
    private final List<BaseCollapsiblePane> panes = new ArrayList<>();

    /**
     * Registers a pane with this group. Panes can be registered at any time.
     * Duplicate registrations are ignored.
     */
    public void register(BaseCollapsiblePane pane) {
        if (pane != null && !panes.contains(pane)) {
            panes.add(pane);
            LOG.info("Accordion: registered pane {0}, group now has {1} panes", new Object[]{System.identityHashCode(pane), panes.size()});
        }
    }

    /**
     * Returns true if all panes in this group are currently expanded.
     */
    public boolean allExpanded() {
        return !panes.isEmpty() && panes.stream().allMatch(BaseCollapsiblePane::isExpanded);
    }

    /**
     * Toggles the expanded state of all panes in this group.
     * If all panes are expanded, collapses them all and vice versa.
     */
    public void toggleAll() {
        setAllExpanded(!allExpanded());
    }

    /**
     * Sets the expanded state of all panes in this group.
     */
    public void setAllExpanded(boolean expanded) {
        BaseCollapsiblePane.setBatchMode(true);
        try {
            for (BaseCollapsiblePane p : panes) {
                p.setExpanded(expanded);
            }
        } finally {
            BaseCollapsiblePane.setBatchMode(false);
        }
        if (!panes.isEmpty()) {
            panes.get(0).updateParentLayout();
        }
    }

    /**
     * Expands the given pane and collapses all other registered panes.
     * Uses {@link BaseCollapsiblePane#setBatchMode(boolean)} to avoid
     * repeated layout revalidation during the collapse cascade.
     */
    public void expandPane(BaseCollapsiblePane target) {
        if (!panes.contains(target)) {
            LOG.info("Accordion: target {0} not in group (size={1}), aborting", new Object[]{System.identityHashCode(target), panes.size()});
            return;
        }

        LOG.info("Accordion: expanding pane {0}, collapsing {1} siblings", new Object[]{System.identityHashCode(target), panes.size() - 1});

        BaseCollapsiblePane.setBatchMode(true);
        try {
            for (BaseCollapsiblePane p : panes) {
                if (p != target) {
                    p.setExpanded(false);
                }
            }
            target.setExpanded(true);
        } finally {
            BaseCollapsiblePane.setBatchMode(false);
        }
        // Walk up parent chain invalidating all ancestors
        target.updateParentLayout();
    }
}
