package github.anandb.netbeans.ui.chat;

import github.anandb.netbeans.model.MessageType;
import org.openide.util.NbBundle;
import github.anandb.netbeans.ui.UIBundle;

/**
 * Factory for creating MessageBubble instances.
 * Extracted from ChatThreadPanel.addSingleBubble().
 */
// DSL-LEAF: not a controller — produces MessageBubble instances per MessageType.
// Migration target: BubbleFactory delegates to BubbleShellSpec; avatar position +
// streaming flag plumbing stays imperative.
final class BubbleFactory {

    private BubbleFactory() {}

    /** The avatar keeps one column; alternating sides made consecutive user messages jitter. */
    static MessageBubble.AvatarPosition resolveAvatarPosition(String role) {
        return "user".equals(role) ? MessageBubble.AvatarPosition.LEFT : MessageBubble.AvatarPosition.NONE;
    }

    /** Create a bubble for tool/thought messages (no avatar, optional toolTitle). */
    static MessageBubble createToolThoughtBubble(MessageType type, String text,
            String messageId, String toolTitle, boolean streaming) {
        if (type.isThought() && (toolTitle == null || toolTitle.isEmpty())) {
            toolTitle = NbBundle.getMessage(UIBundle.class, "LBL_ThinkingProcess");
        }
        return new MessageBubble(type, text, messageId, toolTitle,
                MessageBubble.AvatarPosition.NONE, streaming, null);
    }

    /** Create a bubble for user/assistant messages with avatar positioning. */
    static MessageBubble createRoleBubble(MessageType type, String text,
            String messageId, String toolTitle, boolean streaming, String sessionId) {
        MessageBubble.AvatarPosition avatarPos = resolveAvatarPosition(type.roleName());
        return new MessageBubble(type, text, messageId, toolTitle, avatarPos, streaming, sessionId);
    }
}
