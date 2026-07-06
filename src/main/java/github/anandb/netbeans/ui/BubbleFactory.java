package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.MessageType;
import org.openide.util.NbBundle;

/**
 * Factory for creating MessageBubble instances.
 * Extracted from ChatThreadPanel.addSingleBubble().
 */
// DSL-LEAF: not a controller — produces MessageBubble instances per MessageType.
// Migration target: BubbleFactory delegates to BubbleShellSpec; avatar position +
// streaming flag plumbing stays imperative.
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
                MessageBubble.AvatarPosition.NONE, streaming, null);
    }

    /** Create a bubble for user/assistant messages with avatar positioning. */
    static MessageBubble createRoleBubble(MessageType type, String text,
            String messageId, String toolTitle, boolean streaming, int userMessageCount, String sessionId) {
        MessageBubble.AvatarPosition avatarPos = resolveAvatarPosition(type.roleName(), userMessageCount);
        return new MessageBubble(type, text, messageId, toolTitle, avatarPos, streaming, sessionId);
    }

    /** Overload without sessionId for callers that don't track it. */
    static MessageBubble createRoleBubble(MessageType type, String text,
            String messageId, String toolTitle, boolean streaming, int userMessageCount) {
        return createRoleBubble(type, text, messageId, toolTitle, streaming, userMessageCount, null);
    }
}
