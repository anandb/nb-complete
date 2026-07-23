package github.anandb.netbeans.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.model.Message;

/**
 * Applies message-type visibility filters to the chat thread panel.
 * Extracted from ChatThreadPanel to reduce class size.
 */
public final class TypeFilterApplier {

    private TypeFilterApplier() {}

    /**
     * Re-renders messages or toggles bubble visibility based on current filter state.
     *
     * @param cachedMessages the cached full message list (nullable)
     * @param setMessages    callback to re-render from the full list
     * @param messagesContainer the messages container panel
     */
    public static void apply(List<Message> cachedMessages, Consumer<List<Message>> setMessages, JPanel messagesContainer) {
        if (cachedMessages != null && !cachedMessages.isEmpty()) {
            setMessages.accept(cachedMessages);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            boolean toolHidden = MessageFilterManager.isTypeHidden("tool");
            boolean thoughtHidden = MessageFilterManager.isTypeHidden("thought");

            Component[] comps = messagesContainer.getComponents();
            for (int i = 0; i < comps.length; i++) {
                if (comps[i] instanceof MessageBubble bubble) {
                    if (Boolean.TRUE.equals(bubble.getClientProperty("nb-complete.combined"))) {
                        @SuppressWarnings("unchecked")
                        List<CollapsibleToolPane.ToolSegment> allSegments =
                                (List<CollapsibleToolPane.ToolSegment>) bubble.getClientProperty("nb-complete.segments");
                        if (allSegments != null) {
                            List<CollapsibleToolPane.ToolSegment> visibleSegments = new ArrayList<>();
                            for (CollapsibleToolPane.ToolSegment seg : allSegments) {
                                if ((seg.isThought() && !thoughtHidden)
                                        || (!seg.isThought() && !toolHidden)) {
                                    visibleSegments.add(seg);
                                }
                            }
                            if (visibleSegments.isEmpty()) {
                                bubble.setVisible(false);
                                if (i + 1 < comps.length) comps[i + 1].setVisible(false);
                            } else {
                                bubble.setVisible(true);
                                if (i + 1 < comps.length) comps[i + 1].setVisible(true);
                                bubble.updateCombinedContent(visibleSegments, "Execution Steps (" + visibleSegments.size() + ")");
                                bubble.revalidate();
                            }
                            continue;
                        }
                    }
                    boolean visible = !MessageFilterManager.isTypeHidden(bubble.getRole());
                    boolean wasHidden = !comps[i].isVisible();
                    comps[i].setVisible(visible);
                    if (i + 1 < comps.length) {
                        comps[i + 1].setVisible(visible);
                    }
                    if (wasHidden && visible) {
                        comps[i].revalidate();
                    }
                }
            }
            messagesContainer.revalidate();
        });
    }
}
