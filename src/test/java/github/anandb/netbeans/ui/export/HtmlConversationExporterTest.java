package github.anandb.netbeans.ui.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JPanel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.ui.TestUiUtils;
import github.anandb.netbeans.ui.chat.CollapsibleToolPane;
import github.anandb.netbeans.ui.chat.MessageBubble;

class HtmlConversationExporterTest {

    @BeforeEach
    void setUp() {
        TestUiUtils.setupTestUIManager();
    }

    @Test
    void generatesSelfContainedHtmlForMessageRoles() {
        JPanel messages = new JPanel();
        messages.add(bubble(MessageType.user_message_chunk, "<hello>\nworld", null));
        messages.add(bubble(MessageType.agent_message_chunk, "**answer**", null));
        messages.add(bubble(MessageType.agent_thought_chunk, "thinking", null));
        messages.add(bubble(MessageType.tool_call, "result <raw>", "Run & check"));
        messages.add(bubble(MessageType.agent_message_chunk, "  ", null));

        String html = HtmlConversationExporter.generateHtml(messages, "A <title>");

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<title>A &lt;title> - exported "));
        assertTrue(html.contains("<h1>A &lt;title></h1>"));
        assertTrue(html.contains("class=\"message user\""));
        assertTrue(html.contains("&lt;hello><br/>\nworld"));
        assertTrue(html.contains("<strong>answer</strong>"));
        assertTrue(html.contains("class=\"message thought\""));
        assertTrue(html.contains("class=\"message tool\""));
        assertTrue(html.contains("Run &amp; check"));
        assertTrue(html.contains("result &lt;raw>"));
        assertTrue(html.contains("data:image/svg+xml;base64,"));
        assertFalse(html.contains(">  </div>"));
    }

    @Test
    void rendersCombinedActivityAndTables() {
        JPanel messages = new JPanel();
        MessageBubble combined = bubble(MessageType.tool_call, "", null);
        combined.putClientProperty("nb-complete.segments", List.of(
                new CollapsibleToolPane.ToolSegment("**thought**", true, "Plan"),
                new CollapsibleToolPane.ToolSegment("<file>", false, "Read & inspect")));
        messages.add(combined);
        messages.add(bubble(MessageType.agent_message_chunk, "| A | B |\n|---|---|\n|1|2|", null));

        String html = HtmlConversationExporter.generateHtml(messages, null);

        assertTrue(html.contains("class=\"activity\""));
        assertTrue(html.contains("class=\"segment thought\""));
        assertTrue(html.contains("<strong>thought</strong>"));
        assertTrue(html.contains("Read &amp; inspect"));
        assertTrue(html.contains("&lt;file>"));
        assertTrue(html.contains("<table align='left'"));
        assertTrue(html.contains("<th align='left'"));
        assertTrue(html.contains("<td align='left'"));
    }

    @Test
    void choosesSafeHtmlFileNames() {
        assertEquals("My_Project_.html", HtmlConversationExporter.defaultFileName("My Project?"));
        assertEquals("session.html", HtmlConversationExporter.defaultFileName(null));
        assertEquals("session.html", HtmlConversationExporter.defaultFileName("New Session 3"));
    }

    private static MessageBubble bubble(MessageType type, String text, String toolTitle) {
        return new MessageBubble(type, text, null, toolTitle, MessageBubble.AvatarPosition.NONE);
    }
}
