package github.anandb.netbeans.ui.chat;

import github.anandb.netbeans.model.MessageType;
import java.awt.Component;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolThoughtCombinerTest {

    private static MessageBubble toolBubble(String text, String title) {
        return new MessageBubble(MessageType.tool_call, text, null,
                title, MessageBubble.AvatarPosition.NONE);
    }

    private static MessageBubble thoughtBubble(String text) {
        return new MessageBubble(MessageType.agent_thought_chunk, text, null,
                null, MessageBubble.AvatarPosition.NONE);
    }

    @Test
    void compositeAdoptsLivePanesInsteadOfRebuilding() {
        JPanel container = new JPanel();
        MessageBubble tool = toolBubble("output", "Search");
        MessageBubble thought = thoughtBubble("thinking");
        container.add(tool);
        container.add(Box.createVerticalStrut(4));
        container.add(thought);
        container.add(Box.createVerticalStrut(4));

        BaseCollapsiblePane toolPane = tool.childPanes().get(0);
        BaseCollapsiblePane thoughtPane = thought.childPanes().get(0);

        ToolThoughtCombiner.combine(container, true, null);

        assertEquals(2, container.getComponentCount());
        assertTrue(container.getComponent(0) instanceof MessageBubble);
        MessageBubble combined = (MessageBubble) container.getComponent(0);
        assertEquals(Boolean.TRUE, combined.getClientProperty("nb-complete.combined"));
        // Same pane instances survive; bodies are never re-parsed.
        assertEquals(List.of(toolPane, thoughtPane), combined.childPanes());
        assertSame(toolPane, combined.childPanes().get(0));
        @SuppressWarnings("unchecked")
        List<CollapsibleToolPane.ToolSegment> segments =
                (List<CollapsibleToolPane.ToolSegment>) combined.getClientProperty("nb-complete.segments");
        assertEquals(2, segments.size());
        assertEquals(2, combined.applyCompositeFilter());
    }

    @Test
    void readToolPaneKeepsHeaderOnlyBody() {
        JPanel container = new JPanel();
        MessageBubble read = toolBubble("file bytes", "read /tmp/x.txt");
        container.add(read);
        container.add(Box.createVerticalStrut(4));

        ToolThoughtCombiner.combine(container, true, null);

        assertTrue(container.getComponent(0) instanceof MessageBubble);
        MessageBubble combined = (MessageBubble) container.getComponent(0);
        assertEquals(1, combined.childPanes().size());
        assertEquals("", combined.childPanes().get(0).getContentToCopy().strip());
    }

    @Test
    void noToolThoughtLeavesContainerAlone() {
        JPanel container = new JPanel();
        MessageBubble user = new MessageBubble(MessageType.user_message_chunk, "hi", null,
                null, MessageBubble.AvatarPosition.NONE);
        container.add(user);

        ToolThoughtCombiner.combine(container, true, null);

        assertEquals(1, container.getComponentCount());
    }

    @Test
    void compositeHeaderHidesPanesWithoutRemoving() {
        JPanel container = new JPanel();
        container.add(toolBubble("output", "Search"));
        container.add(Box.createVerticalStrut(4));
        container.add(thoughtBubble("thinking"));
        container.add(Box.createVerticalStrut(4));

        ToolThoughtCombiner.combine(container, true, null);

        MessageBubble combined = (MessageBubble) container.getComponent(0);
        assertEquals(2, combined.childPanes().size());

        combined.setExpanded(false);
        assertEquals(2, combined.childPanes().size());
        assertTrue(combined.childPanes().stream().noneMatch(Component::isVisible));

        combined.setExpanded(true);
        assertTrue(combined.childPanes().stream().allMatch(Component::isVisible));
    }
}
