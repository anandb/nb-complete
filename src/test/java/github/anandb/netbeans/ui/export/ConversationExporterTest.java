package github.anandb.netbeans.ui.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.ui.chat.CollapsibleToolPane;
import github.anandb.netbeans.support.PluginSettings;
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
        assertTrue(markdown.contains("# A \\\"title\\\"\n\n## Transcript\n\n### User\n\nquestion"));
        assertTrue(markdown.contains("### Assistant\n\nanswer"));
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

        assertTrue(markdown.contains("# Untitled Session\n\n## Transcript\n\n### Thought\n\n"
                + "> **Thinking**\n>\n> thinking"));
        assertTrue(markdown.contains("**Tool: Read**\n\n```text\nresult\n```"));
        assertFalse(markdown.contains("Empty"));
    }

    @Test
    void capturedTranscriptExportsRolesAndDropsNoise() {
        List<ProcessedMessage> messages = List.of(
                pm(MessageType.user_message_chunk, "question", null),
                pm(MessageType.agent_message_chunk, "answer", null),
                pm(MessageType.agent_thought_chunk, "line one\nline two", null),
                pm(MessageType.tool_call, "output", "Search"),
                ProcessedMessage.createInfo("notice"),
                pm(MessageType.agent_message_chunk, "  ", null));

        String markdown = ConversationExporter.generateMarkdown(messages, "Session", false);

        assertTrue(markdown.contains("# Session\n\n## Transcript\n\n### User\n\nquestion"));
        assertTrue(markdown.contains("### Assistant\n\nanswer"));
        assertTrue(markdown.contains("> **Thinking**\n>\n> line one\n> line two"));
        assertTrue(markdown.contains("**Tool: Search**\n\n```text\noutput\n```"));
        assertFalse(markdown.contains("notice"), "info notices are not transcript content");
        assertFalse(markdown.contains("\n  \n"));
    }

    @Test
    void capturedUserTextIsStrippedLikeTheDisplayedBubbles() {
        List<ProcessedMessage> messages = List.of(
                pm(MessageType.user_message_chunk, "<metadata>hidden</metadata>\nreal prompt", null));

        String markdown = ConversationExporter.generateMarkdown(messages, "Session", false);

        assertTrue(markdown.contains("real prompt"));
        assertFalse(markdown.contains("hidden"), "metadata blocks are stripped from user text");
    }

    @Test
    void nullCapturedTranscriptStillWritesTheHeader() {
        String markdown = ConversationExporter.generateMarkdown(null, "Session", false);

        assertTrue(markdown.contains("# Session\n\n## Transcript"));
    }

    @Test
    void capturedStreamingChunksMergeIntoOneAssistantEntry() {
        List<ProcessedMessage> messages = List.of(
                streamingPm(MessageType.agent_message_chunk, "hel", null),
                streamingPm(MessageType.agent_message_chunk, "lo", null));

        String markdown = ConversationExporter.generateMarkdown(messages, "Session", false);

        assertTrue(markdown.contains("### Assistant\n\nhello"));
        assertFalse(markdown.contains("hel\n\nlo"));
    }

    @Test
    void preambleConfiguredPromptIsDroppedLikeTheDisplay() {
        String originalPreamble = PluginSettings.getPreamble();
        try {
            // Rules are always bundled; the configured user preamble appends to them.
            PluginSettings.setPreamble("custom preamble text");
            PluginSettings.setPreambleEnabled(true);

            List<ProcessedMessage> messages = List.of(
                    pm(MessageType.user_message_chunk,
                            PluginSettings.getCriticalRules().trim() + "\n\ncustom preamble text", null),
                    pm(MessageType.user_message_chunk, "real question", null));

            String markdown = ConversationExporter.generateMarkdown(messages, "Session", false);

            assertFalse(markdown.contains("custom preamble text"),
                    "the configured preamble prompt is plugin noise and must not export");
            assertTrue(markdown.contains("real question"));
        } finally {
            PluginSettings.setPreamble(originalPreamble);
            PluginSettings.setPreambleEnabled(true);
        }
    }

    @Test
    void blankFirstUserMessageDoesNotHideThePreambleDrop() {
        String originalPreamble = PluginSettings.getPreamble();
        try {
            PluginSettings.setPreamble("");
            PluginSettings.setPreambleEnabled(true);

            List<ProcessedMessage> messages = List.of(
                    pm(MessageType.user_message_chunk, "<metadata>noise</metadata>", null),
                    pm(MessageType.user_message_chunk, PluginSettings.getCriticalRules().trim(), null),
                    pm(MessageType.user_message_chunk, "real question", null));

            String markdown = ConversationExporter.generateMarkdown(messages, "Session", false);

            assertFalse(markdown.contains(PluginSettings.getCriticalRules().trim().substring(0, 40)),
                    "the preamble prompt is still the first non-blank user message");
            assertTrue(markdown.contains("real question"));
            assertFalse(markdown.contains("noise"));
        } finally {
            PluginSettings.setPreamble(originalPreamble);
            PluginSettings.setPreambleEnabled(true);
        }
    }

    @Test
    void titleControlsStayInYamlAndMarkdownPunctuationIsLiteral() {
        String title = "A # [title]\r\nnext\u0001";

        String markdown = ConversationExporter.generateMarkdown(new JPanel(), title);

        assertTrue(markdown.startsWith("---\ntitle: \"A # [title]\\r\\nnext\\u0001\"\ndate: "));
        assertTrue(markdown.contains("# A \\# \\[title\\]  next\\\\u0001\n\n## Transcript"));
    }

    @Test
    void toolTitleControlsStayInsideTheBoldLabel() {
        JPanel messages = new JPanel();
        messages.add(bubble(MessageType.tool_call, "output", "Lookup **bad**\nnext"));

        String markdown = ConversationExporter.generateMarkdown(messages, "Session");

        assertTrue(markdown.contains("**Tool: Lookup \\*\\*bad\\*\\* next**"));
    }

    @Test
    void toolOutputWithTripleBackticksUsesALongerFence() {
        JPanel messages = new JPanel();
        messages.add(bubble(MessageType.tool_call, "before\n```java\ncode\n```\nafter", "Read"));

        String markdown = ConversationExporter.generateMarkdown(messages, "Session");

        assertTrue(markdown.contains("````text\nbefore\n```java\ncode\n```\nafter\n````"));
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

    private static ProcessedMessage pm(MessageType type, String text, String toolTitle) {
        return new ProcessedMessage.Builder()
                .messageType(type)
                .text(text)
                .rawText(text)
                .toolTitle(toolTitle)
                .build();
    }

    private static ProcessedMessage streamingPm(MessageType type, String text, String toolTitle) {
        return new ProcessedMessage.Builder()
                .messageType(type)
                .text(text)
                .rawText(text)
                .toolTitle(toolTitle)
                .streaming(true)
                .build();
    }
}
