package github.anandb.netbeans.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;
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
// DSL-LEAF: not a controller — combines adjacent tool-call + thought activity
// panes into one collapsible (group header). Migration target:
// CombinedActivitySpec; the combination merge logic stays imperative.
public final class ToolThoughtCombiner {

    private static volatile boolean combineEnabled = true;
    private static final Pattern TOOL_PREFIX = Pattern.compile("(?i)TOOL:?\\s*");

    static {
        NbPreferences.forModule(ACPOptionsPanel.class)
            .addPreferenceChangeListener(e -> {
                if ("combineToolThought".equals(e.getKey())) {
                    combineEnabled = Boolean.parseBoolean(e.getNewValue());
                }
            });
        combineEnabled = NbPreferences.forModule(ACPOptionsPanel.class)
            .getBoolean("combineToolThought", true);
    }

    private ToolThoughtCombiner() {
    }

    /**
     * Walks the container backwards and returns true if any individual (non-combined)
     * tool/thought bubble exists after the last user/assistant/combined bubble.
     * User and assistant bubbles act as turn separators — tool/thoughts before them
     * belong to a previous turn and must not be included.
     * Used as a fast-path early exit before the full combine scan.
     */
    private static boolean hasPendingIndividualToolThought(JPanel messagesContainer) {
        Component[] comps = messagesContainer.getComponents();
        for (int i = comps.length - 1; i >= 0; i--) {
            Component c = comps[i];
            if (c instanceof MessageBubble mb) {
                String role = mb.getRole();
                // User/assistant bubbles act as turn separators — stop here
                if ("user".equals(role) || "assistant".equals(role)) {
                    return false;
                }
                // Already-combined bubble also acts as a boundary
                if (Boolean.TRUE.equals(mb.getClientProperty("nb-complete.combined"))) {
                    return false;
                }
                if ("tool".equals(role) || "thought".equals(role)) {
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
     * @param scrollController   used to release wheel-redirect listeners on
     *                           removed bubbles so they are not retained by the
     *                           controller's strong-keyed map (memory leak)
     */
    public static void combine(JPanel messagesContainer, boolean allBlocksExpanded,
                               ScrollController scrollController) {
        // Respect user preference: if unchecked, skip combining
        if (!combineEnabled) {
            return;
        }

        // Fast path: scan the tail of the container to detect whether there is
        // actually a run of individual tool/thought bubbles to combine. If the
        // last components are already a combined bubble, a non-tool/thought
        // bubble, or empty, skip the full scan.
        if (!hasPendingIndividualToolThought(messagesContainer)) {
            return;
        }

        // Find all individual tool/thought bubbles after the last
        // user/assistant/combined boundary. Scan only the tail region
        // to avoid O(N) per-call on the full container.
        List<Component> toRemove = new ArrayList<>();
        List<CollapsibleToolPane.ToolSegment> allSegments = new ArrayList<>();
        int insertIndex = -1;

        Component[] comps = messagesContainer.getComponents();

        // Find scan start: after the last user, assistant, or combined bubble
        int scanStart = 0;
        for (int i = comps.length - 1; i >= 0; i--) {
            Component c = comps[i];
            if (c instanceof MessageBubble mb) {
                String role = mb.getRole();
                if ("user".equals(role) || "assistant".equals(role)
                        || Boolean.TRUE.equals(mb.getClientProperty("nb-complete.combined"))) {
                    scanStart = i + 1;
                    break;
                }
            }
        }

        List<Component> compList = Arrays.asList(comps);
        ListIterator<Component> it = compList.listIterator(scanStart);
        while (it.hasNext()) {
            Component c = it.next();
            if (c instanceof MessageBubble mb) {
                String role = mb.getRole();
                // Safety guard: stop if we cross a user/assistant boundary
                if ("user".equals(role) || "assistant".equals(role)) {
                    break;
                }
                if (("tool".equals(role) || "thought".equals(role))
                        && !Boolean.TRUE.equals(mb.getClientProperty("nb-complete.combined"))) {
                    if (insertIndex < 0) {
                        insertIndex = it.previousIndex();
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
                            String stripped = TOOL_PREFIX.matcher(segTitle).replaceFirst("").trim();
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
                    if (it.hasNext() && compList.get(it.nextIndex()) instanceof Box.Filler) {
                        toRemove.add(it.next());
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
            for (int ri = toRemove.size() - 1; ri >= 0; ri--) {
                Component removed = toRemove.get(ri);
                messagesContainer.remove(removed);
                if (scrollController != null) {
                    scrollController.unfixMouseWheel(removed);
                }
            }
            messagesContainer.revalidate();
            return;
        }

        // Remove from back to front to keep indices valid
        for (int ri = toRemove.size() - 1; ri >= 0; ri--) {
            Component removed = toRemove.get(ri);
            messagesContainer.remove(removed);
            if (scrollController != null) {
                scrollController.unfixMouseWheel(removed);
            }
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
