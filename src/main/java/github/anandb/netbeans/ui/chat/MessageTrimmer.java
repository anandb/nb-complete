package github.anandb.netbeans.ui.chat;

import java.awt.Component;
import javax.swing.Box;
import javax.swing.JPanel;
import github.anandb.netbeans.ui.permission.PermissionBubble;

/**
 * Trims the message container to a maximum number of user/assistant messages,
 * preserving pinned bubbles and their layout struts. Tool, thought and permission
 * rows do not count toward the cap, but they are removed when they fall before the
 * cut so a trimmed turn leaves no orphaned activity rows.
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

        // Only user/assistant bubbles consume the cap; pinned ones never do.
        int count = 0;
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble mb && !mb.isPinned() && countsTowardLimit(mb)) {
                count++;
            }
        }

        int excess = count - max;
        if (excess <= 0) return;

        // Approximate is fine: drop oldest unpinned rows until enough counted messages
        // are gone. Activity rows met on the way go too; a boundary row may remain.
        for (int i = 0; i < messagesContainer.getComponentCount() && excess > 0; ) {
            Component c = messagesContainer.getComponent(i);
            if (c instanceof MessageBubble mb) {
                if (mb.isPinned()) {
                    i++;
                    continue;
                }
                if (countsTowardLimit(mb)) {
                    excess--;
                }
                removeBubbleWithStrut(i, c);
            } else if (c instanceof PermissionBubble) {
                removeBubbleWithStrut(i, c);
            } else {
                i++;
            }
        }
    }

    private static boolean countsTowardLimit(MessageBubble bubble) {
        String role = bubble.getRole();
        return "user".equals(role) || "assistant".equals(role);
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
