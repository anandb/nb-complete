package github.anandb.netbeans.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import javax.swing.Box;
import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.MessageType;

/**
 * Trimming contract behind the Max Displayed Messages and Keep Older Messages
 * settings: pinned bubbles never count or get removed, the cap removes the
 * oldest unpinned bubbles (and their layout struts), and 0 / keep-older means
 * no trimming.
 */
class MessageTrimmerTest {

    private final JPanel container = new JPanel();
    private final ScrollController scroll = mock(ScrollController.class);
    private final MessageTrimmer trimmer = new MessageTrimmer(container, scroll);

    private MessageBubble bubble() {
        return bubble(MessageType.agent_message_chunk);
    }

    private MessageBubble bubble(MessageType type) {
        return new MessageBubble(type, "x", null, null, MessageBubble.AvatarPosition.NONE);
    }

    private int bubbleCount() {
        int count = 0;
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof MessageBubble) {
                count++;
            }
        }
        return count;
    }

    @Test
    void removesOldestUnpinnedBubblesDownToTheCap() {
        MessageBubble oldest = bubble();
        MessageBubble second = bubble();
        container.add(oldest);
        container.add(Box.createVerticalStrut(4));
        container.add(second);
        container.add(bubble());
        container.add(bubble());
        container.add(bubble());

        trimmer.trim(2, false);

        assertEquals(2, bubbleCount());
        assertTrue(java.util.Arrays.stream(container.getComponents()).noneMatch(c -> c == oldest),
                "the oldest bubble must be the one removed");
        verify(scroll).unfixMouseWheel(oldest);
        assertEquals(2, container.getComponentCount(),
                "the removed bubbles' strut must go with them; only the two kept bubbles remain");
    }

    @Test
    void pinnedBubblesNeitherCountNorGetRemoved() {
        MessageBubble pinned = bubble();
        pinned.setPinned(true);
        container.add(pinned);
        for (int i = 0; i < 3; i++) {
            container.add(bubble());
        }

        trimmer.trim(2, false);

        assertTrue(java.util.Arrays.stream(container.getComponents()).anyMatch(c -> c == pinned));
        assertEquals(3, bubbleCount(), "the cap counts only the two unpinned bubbles after one removal");
    }

    @Test
    void toolAndThoughtBubblesDoNotConsumeTheCap() {
        MessageBubble user = bubble(MessageType.user_message_chunk);
        MessageBubble assistant = bubble();
        container.add(user);
        for (int i = 0; i < 5; i++) {
            container.add(bubble(MessageType.tool_call));
        }
        container.add(assistant);

        trimmer.trim(2, false);

        assertEquals(7, bubbleCount(),
                "five activity rows plus the two counted messages must all stay");
    }

    @Test
    void activityRowsAreNotCountedAndMayRemainAtTheCut() {
        MessageBubble oldAssistant = bubble();
        MessageBubble oldTool = bubble(MessageType.tool_call);
        MessageBubble user = bubble(MessageType.user_message_chunk);
        MessageBubble newAssistant = bubble();
        container.add(oldAssistant);
        container.add(oldTool);
        container.add(user);
        container.add(newAssistant);

        trimmer.trim(2, false);

        assertTrue(java.util.Arrays.stream(container.getComponents()).noneMatch(
                c -> c == oldAssistant), "the oldest counted message must go");
        assertTrue(java.util.Arrays.stream(container.getComponents()).anyMatch(
                c -> c == user || c == newAssistant),
                "the retained messages must stay");
        // Approximation is deliberate: the tool row at the boundary may remain.
    }

    @Test
    void keepOlderMessagesDisablesTrimming() {
        for (int i = 0; i < 5; i++) {
            container.add(bubble());
        }

        trimmer.trim(2, true);

        assertEquals(5, bubbleCount());
    }

    @Test
    void nonPositiveCapDisablesTrimming() {
        for (int i = 0; i < 5; i++) {
            container.add(bubble());
        }

        trimmer.trim(0, false);

        assertEquals(5, bubbleCount());
    }
}
