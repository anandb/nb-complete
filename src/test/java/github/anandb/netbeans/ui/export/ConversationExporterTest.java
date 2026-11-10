package github.anandb.netbeans.ui.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.ui.chat.CollapsibleToolPane;
import github.anandb.netbeans.ui.chat.MessageBubble;

class ConversationExporterTest {

    @Test
    void generatesMarkdownForVisibleMessagesAndEscapesYamlTitle() {
        JPanel messages = new JPanel();
        messages.add(bubble(MessageType.user_message_chunk, "question", null));
        messages.add(bubble(MessageType.agent_message_chunk, "answer", null));
        messages.add(bubble(MessageType.agent_thought_chunk, "line one\nline two", null));
        messages.add(bubble(MessageType.tool_call, "output", "Search"));
        messages.add(bubble(MessageType.agent_message_chunk, "  ", null));

        String markdown = ConversationExporter.generateMarkdown(messages, "A \"title\"");

        assertTrue(markdown.startsWith("---\ntitle: \"A \\\"title\\\"\"\ndate: "));
        assertTrue(markdown.contains("## User\n\nquestion\n\n## Assistant\n\nanswer"));
        assertTrue(markdown.contains("> **Thinking**\n>\n> line one\n> line two"));
        assertTrue(markdown.contains("**Tool: Search**\n\n```text\noutput\n```"));
        assertFalse(markdown.contains("\n  \n"));
    }

    @Test
    void exportsCombinedActivitySegmentsAndSkipsBlankSegments() {
        JPanel messages = new JPanel();
        MessageBubble combined = bubble(MessageType.tool_call, "", null);
        combined.putClientProperty("nb-complete.segments", java.util.List.of(
                new CollapsibleToolPane.ToolSegment("thinking", true, null),
                new CollapsibleToolPane.ToolSegment("  ", false, "Empty"),
                new CollapsibleToolPane.ToolSegment("result", false, "Read")));
        messages.add(combined);

        String markdown = ConversationExporter.generateMarkdown(messages, null);

        assertTrue(markdown.contains("> **Thinking**\n>\n> thinking"));
        assertTrue(markdown.contains("**Tool: Read**\n\n```text\nresult\n```"));
        assertFalse(markdown.contains("Empty"));
    }

    @Test
    void choosesSafeMarkdownFileNames() {
        assertEquals("My_Project_.md", ConversationExporter.defaultFileName("My Project?"));
        assertEquals("session.md", ConversationExporter.defaultFileName(null));
        assertEquals("session.md", ConversationExporter.defaultFileName("New Session 3"));
    }

    private static MessageBubble bubble(MessageType type, String text, String toolTitle) {
        return new MessageBubble(type, text, null, toolTitle, MessageBubble.AvatarPosition.NONE);
    }
}
