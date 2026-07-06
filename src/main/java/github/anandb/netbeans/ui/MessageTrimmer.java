package github.anandb.netbeans.ui;

import java.awt.Component;
import javax.swing.Box;
import javax.swing.JPanel;

/**
 * Trims the message container to a maximum number of unpinned bubbles,
 * preserving pinned bubbles and their layout struts.
 */
final class MessageTrimmer {

    private final JPanel messagesContainer;
    private final ScrollController scrollController;

    MessageTrimmer(JPanel messagesContainer, ScrollController scrollController) {
        this.messagesContainer = messagesContainer;
        this.scrollController = scrollController;
    }

    void trim(int max, boolean keepOlderMessages) {
        if (max <= 0 || keepOlderMessages) return;

        // Count only unpinned bubbles — pinned never count toward the cap.
        int count = 0;
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble mb) {
                if (!mb.isPinned()) {
                    count++;
                }
            } else if (c instanceof PermissionBubble) {
                count++;
            }
        }

        int excess = count - max;
        if (excess <= 0) return;

        int removed = 0;
        for (int i = 0; i < messagesContainer.getComponentCount() && removed < excess; ) {
            Component c = messagesContainer.getComponent(i);
            if (c instanceof MessageBubble mb) {
                if (mb.isPinned()) {
                    i++;
                    continue;
                }
                removeBubbleWithStrut(i, c);
                removed++;
            } else if (c instanceof PermissionBubble) {
                removeBubbleWithStrut(i, c);
                removed++;
            } else {
                i++;
            }
        }
    }

    private void removeBubbleWithStrut(int index, Component bubble) {
        messagesContainer.remove(index);
        scrollController.unfixMouseWheel(bubble);
        if (index < messagesContainer.getComponentCount()
                && messagesContainer.getComponent(index) instanceof Box.Filler) {
            messagesContainer.remove(index);
        }
    }
}
