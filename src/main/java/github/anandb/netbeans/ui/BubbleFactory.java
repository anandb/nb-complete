package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.MessageType;
import org.openide.util.NbBundle;

/**
 * Factory for creating MessageBubble instances.
 * Extracted from ChatThreadPanel.addSingleBubble().
 */
final class BubbleFactory {

    private BubbleFactory() {}

    /** Resolve avatar position for a user message based on alternating count. */
    static MessageBubble.AvatarPosition resolveAvatarPosition(String role, int userMessageCount) {
        if ("user".equals(role)) {
            return userMessageCount % 2 == 1 ? MessageBubble.AvatarPosition.LEFT : MessageBubble.AvatarPosition.RIGHT;
        }
        return MessageBubble.AvatarPosition.NONE;
    }

    /** Create a bubble for tool/thought messages (no avatar, optional toolTitle). */
    static MessageBubble createToolThoughtBubble(MessageType type, String text,
            String messageId, String toolTitle, boolean streaming) {
        if (type.isThought() && (toolTitle == null || toolTitle.isEmpty())) {
            toolTitle = NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ThinkingProcess");
        }
        return new MessageBubble(type, text, messageId, toolTitle,
                MessageBubble.AvatarPosition.NONE, streaming);
    }

    /** Create a bubble for user/assistant messages with avatar positioning. */
    static MessageBubble createRoleBubble(MessageType type, String text,
            String messageId, String toolTitle, boolean streaming, int userMessageCount) {
        MessageBubble.AvatarPosition avatarPos = resolveAvatarPosition(type.roleName(), userMessageCount);
        return new MessageBubble(type, text, messageId, toolTitle, avatarPos, streaming);
    }
}
