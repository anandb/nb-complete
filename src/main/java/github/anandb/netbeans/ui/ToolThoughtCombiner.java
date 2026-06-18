package github.anandb.netbeans.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.model.MessageType;

/**
 * Combines individual tool/thought bubbles into a single "Execution Steps"
 * activity panel. Called before a user/assistant message is added or when
 * streaming ends. Individual bubbles are removed to free memory.
 */
public final class ToolThoughtCombiner {

    private ToolThoughtCombiner() {
    }

    /**
     * Walks the container backwards and returns true if any individual (non-combined)
     * tool/thought bubble exists. Used as a fast-path early exit before the full
     * combine scan, which would otherwise touch every component in the container
     * even when there's nothing to combine.
     */
    private static boolean hasPendingIndividualToolThought(JPanel messagesContainer) {
        Component[] comps = messagesContainer.getComponents();
        for (int i = comps.length - 1; i >= 0; i--) {
            Component c = comps[i];
            if (c instanceof MessageBubble mb) {
                String role = mb.getRole();
                if (("tool".equals(role) || "thought".equals(role))
                        && !Boolean.TRUE.equals(mb.getClientProperty("nb-complete.combined"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Scans the container for individual tool/thought bubbles, merges them
     * into one combined bubble, and updates the container.
     *
     * @param messagesContainer the container holding message bubbles
     * @param allBlocksExpanded whether combined blocks should start expanded
     */
    public static void combine(JPanel messagesContainer, boolean allBlocksExpanded) {
        // Respect user preference: if unchecked, skip combining
        if (!NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("combineToolThought", true)) {
            return;
        }

        // Fast path: scan the tail of the container to detect whether there is
        // actually a run of individual tool/thought bubbles to combine. If the
        // last components are already a combined bubble, a non-tool/thought
        // bubble, or empty, skip the full scan.
        if (!hasPendingIndividualToolThought(messagesContainer)) {
            return;
        }

        // Find all individual tool/thought bubbles (skip already-combined ones)
        List<Component> toRemove = new ArrayList<>();
        List<CollapsibleToolPane.ToolSegment> allSegments = new ArrayList<>();
        int insertIndex = -1;

        Component[] comps = messagesContainer.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c instanceof MessageBubble mb) {
                String role = mb.getRole();
                if (("tool".equals(role) || "thought".equals(role))
                        && !Boolean.TRUE.equals(mb.getClientProperty("nb-complete.combined"))) {
                    if (insertIndex < 0) {
                        insertIndex = i;
                    }
                    String text = mb.getRawText();
                    if (text != null && !text.isEmpty()) {
                        String segTitle = mb.getToolTitle();
                        if ("thought".equals(role) && (segTitle == null || segTitle.isEmpty())) {
                            segTitle = NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ThinkingProcess");
                        } else if ("tool".equals(role) && (segTitle == null || segTitle.isEmpty())) {
                            segTitle = NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ToolFallback");
                        }
                        // For "read" tool segments, skip body content — show header only
                        if ("tool".equals(role) && segTitle != null) {
                            String stripped = segTitle.replaceFirst("(?i)TOOL:?\\s*", "").trim();
                            int pos = stripped.indexOf(' ');
                            String firstWord = (pos > 1) ? stripped.substring(0, pos) : stripped;
                            if ("read".equalsIgnoreCase(firstWord)
                                    || "functions.read".equalsIgnoreCase(firstWord)
                                    || "functions.webfetch".equalsIgnoreCase(firstWord)) {
                                text = "";
                            }
                        }
                        allSegments.add(new CollapsibleToolPane.ToolSegment(
                                text, "thought".equals(role), segTitle));
                    }
                    toRemove.add(c);
                    // Remove trailing strut as well
                    if (i + 1 < comps.length && comps[i + 1] instanceof Box.Filler) {
                        toRemove.add(comps[i + 1]);
                        i++; // skip strut in next iteration
                    }
                }
            }
        }

        if (allSegments.isEmpty()) {
            return;
        }

        // Filter segments by current type visibility so the combined pane
        // only contains what the user wants to see.
        boolean toolHidden = MessageFilterManager.isTypeHidden("tool");
        boolean thoughtHidden = MessageFilterManager.isTypeHidden("thought");
        List<CollapsibleToolPane.ToolSegment> visibleSegments = new ArrayList<>();
        for (CollapsibleToolPane.ToolSegment seg : allSegments) {
            if ((seg.isThought() && !thoughtHidden) || (!seg.isThought() && !toolHidden)) {
                visibleSegments.add(seg);
            }
        }

        if (visibleSegments.isEmpty()) {
            // All segments hidden — remove individual bubbles but create no combined pane
            for (int i = toRemove.size() - 1; i >= 0; i--) {
                messagesContainer.remove(toRemove.get(i));
            }
            messagesContainer.revalidate();
            return;
        }

        // Remove from back to front to keep indices valid
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            messagesContainer.remove(toRemove.get(i));
        }

        // Create combined bubble with only the visible segments
        String title = "Execution Steps (" + visibleSegments.size() + ")";
        MessageBubble combined = new MessageBubble(
                MessageType.tool_call, "", null, title, MessageBubble.AvatarPosition.NONE);
        combined.setSegmentedToolContent(visibleSegments);
        combined.setExpanded(allBlocksExpanded);
        combined.putClientProperty("nb-complete.combined", Boolean.TRUE);
        // Store ALL segments (unfiltered) for later re-filtering by applyTypeFilters()
        combined.putClientProperty("nb-complete.segments", allSegments);

        boolean combinedVisible = !(toolHidden && thoughtHidden);
        combined.setVisible(combinedVisible);
        Component strut = Box.createVerticalStrut(4);
        strut.setVisible(combinedVisible);

        int safeIdx = Math.max(0, Math.min(insertIndex, messagesContainer.getComponentCount()));
        messagesContainer.add(combined, safeIdx);
        messagesContainer.add(strut, safeIdx + 1);
        messagesContainer.revalidate();
    }
}
