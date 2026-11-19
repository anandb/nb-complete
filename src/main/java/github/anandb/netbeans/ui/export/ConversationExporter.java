package github.anandb.netbeans.ui.export;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import static org.apache.commons.lang3.StringUtils.isBlank;

import javax.swing.JFileChooser;

import github.anandb.netbeans.pattern.TitleSanitizePattern;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.ToolDataExtractor;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.NotifyDescriptor;
import org.openide.DialogDisplayer;
import github.anandb.netbeans.ui.chat.MessageBubble;
import github.anandb.netbeans.ui.chat.CollapsibleToolPane;
import github.anandb.netbeans.ui.UIBundle;

/**
 * Stateless utility for exporting conversation history to Markdown files.
 * Extracted from {@link github.anandb.netbeans.ui.assistant.AssistantTopComponent} and
 * {@link github.anandb.netbeans.ui.chat.ChatThreadPanel}
 * to keep export logic in one place.
 */
public final class ConversationExporter {

    private static final Logger LOG = Logger.from(ConversationExporter.class);

    /** Export writes run off the EDT on their own processor. */
    private static final RequestProcessor EXPORT_RP = new RequestProcessor("ACP-Export", 1);

    private ConversationExporter() { }

    @SuppressWarnings("unchecked")
    public static String generateMarkdown(JPanel messagesContainer, String sessionTitle) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, sessionTitle);

        String[] lastRole = {null};
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble bubble) {
                // Combined activity bubbles store content in ToolSegment records,
                // not in the text StringBuilder (which is empty for these bubbles).
                List<CollapsibleToolPane.ToolSegment> segments =
                        (List<CollapsibleToolPane.ToolSegment>) bubble.getClientProperty("nb-complete.segments");
                if (segments != null && !segments.isEmpty()) {
                    for (CollapsibleToolPane.ToolSegment seg : segments) {
                        if (isBlank(seg.text())) continue;
                        String segmentTitle = seg.title() != null ? seg.title() : "";
                        if (seg.isThought()) {
                            appendRoleHeading(sb, "Thought", lastRole);
                            sb.append("> **Thinking**\n>\n");
                            for (String line : seg.text().split("\n", -1)) {
                                sb.append("> ").append(line).append("\n");
                            }
                            sb.append("\n");
                        } else {
                            appendRoleHeading(sb, "Tool", lastRole);
                            sb.append("**Tool");
                            if (!segmentTitle.isEmpty() && !"Tool".equals(segmentTitle)) {
                                sb.append(": ").append(escapeMarkdownInline(singleLine(segmentTitle)));
                            }
                            sb.append("**\n\n");
                            appendFencedText(sb, seg.text());
                        }
                    }
                    lastRole[0] = null;
                    continue;
                }
                String role = bubble.getRole();
                String text = bubble.getRawText();
                if (isBlank(text)) continue;
                String toolTitle = bubble.getToolTitle() != null ? bubble.getToolTitle() : "";
                String displayRole = role != null ? role : "assistant";
                appendMarkdownContent(sb, displayRole, text, toolTitle, lastRole);
            }
        }
        return sb.toString();
    }

    /**
     * Builds the Markdown export from a captured transcript (background
     * full-session export; the messages are never rendered into the thread).
     * Content rules mirror the panel version.
     */
    public static String generateMarkdown(List<ProcessedMessage> messages, String sessionTitle,
            boolean stripsSystemReminders) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, sessionTitle);

        String[] lastRole = {null};
        for (ExportEntry entry : toExportEntries(messages, stripsSystemReminders)) {
            appendMarkdownContent(sb, entry.role(), entry.text(), entry.toolTitle(), lastRole);
        }
        return sb.toString();
    }

    /** One exportable block: display role, final text, optional tool title and stream status. */
    record ExportEntry(String role, String text, String toolTitle, boolean streaming) { }

    /**
     * Converts a captured transcript to export blocks: drops ignorable status
     * chatter, info/error notices and the plugin's preamble prompt, and strips
     * user text exactly like the displayed bubbles. Shared by the Markdown and
     * HTML exporters.
     */
    static List<ExportEntry> toExportEntries(List<ProcessedMessage> messages, boolean stripsSystemReminders) {
        List<ExportEntry> entries = new ArrayList<>();
        if (messages == null) {
            return entries;
        }
        // The preamble prompt is what PreambleSender sends: the critical rules
        // followed by the user preamble (either alone when the other is blank).
        // The thread replaces that first user message with a notice; the export
        // drops it — mirrored here so every configuration matches the display.
        String rules = PluginSettings.getCriticalRules().trim();
        String preamble = PluginSettings.getPreamble().trim();
        String preamblePrefix = isBlank(rules) ? preamble : rules;
        boolean preambleConfigured = PluginSettings.isPreambleEnabled()
                && !(isBlank(rules) && isBlank(preamble));
        boolean sawUserMessage = false;
        for (ProcessedMessage pm : messages) {
            if (pm == null || pm.messageType() == null || pm.isIgnorable()) {
                continue;
            }
            String role = pm.messageType().roleName();
            if (role == null || "info".equals(role) || "error".equals(role)) {
                continue;
            }
            String text = isBlank(pm.rawText()) ? pm.text() : pm.rawText();
            if ("user".equals(role)) {
                text = ToolDataExtractor.stripMessageNoise(text, stripsSystemReminders);
                String trimmed = text.trim();
                if (preambleConfigured && !isBlank(trimmed)) {
                    // The preamble prompt is plugin noise, not conversation: the
                    // thread replaces it with a notice, the export drops it. It
                    // is the FIRST non-blank user message when it still starts
                    // with what PreambleSender sent.
                    if (!sawUserMessage && trimmed.startsWith(preamblePrefix)) {
                        continue;
                    }
                    sawUserMessage = true;
                }
            }
            if (isBlank(text)) {
                continue;
            }
            if (isMergeableChunk(role, pm.streaming()) && !entries.isEmpty()) {
                ExportEntry previous = entries.get(entries.size() - 1);
                if (previous.streaming() && previous.role().equals(role)
                        && java.util.Objects.equals(previous.toolTitle(), pm.toolTitle())) {
                    entries.set(entries.size() - 1,
                            new ExportEntry(role, previous.text() + text, pm.toolTitle(), true));
                    continue;
                }
            }
            entries.add(new ExportEntry(role, text, pm.toolTitle(), pm.streaming()));
        }
        return entries;
    }
    private static boolean isMergeableChunk(String role, boolean streaming) {
        return streaming && ("user".equals(role) || "assistant".equals(role) || "thought".equals(role));
    }


    /** YAML frontmatter and page heading shared by both export sources. */
    private static void appendHeader(StringBuilder sb, String sessionTitle) {
        String title = isBlank(sessionTitle) ? "Untitled Session" : sessionTitle;
        sb.append("---\n");
        sb.append("title: \"").append(escYaml(title)).append("\"\n");
        sb.append("date: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n");
        sb.append("---\n\n");
        sb.append("# ").append(escapeMarkdownInline(singleLine(title))).append("\n\n");
        sb.append("## Transcript\n\n");
    }

    /** Escape characters that could break YAML strings. */
    private static String escYaml(String s) {
        StringBuilder escaped = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '"') {
                escaped.append('\\').append(ch);
            } else if (ch == '\n') {
                escaped.append("\\n");
            } else if (ch == '\r') {
                escaped.append("\\r");
            } else if (ch == '\t') {
                escaped.append("\\t");
            } else if (Character.isISOControl(ch)) {
                escaped.append(String.format("\\u%04X", (int) ch));
            } else {
                escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private static String singleLine(String value) {
        StringBuilder singleLine = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n' || ch == '\t') {
                singleLine.append(' ');
            } else if (Character.isISOControl(ch)) {
                singleLine.append(String.format("\\u%04X", (int) ch));
            } else {
                singleLine.append(ch);
            }
        }
        return singleLine.toString();
    }

    private static String escapeMarkdownInline(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '!' && ch <= '~' && !Character.isLetterOrDigit(ch)) {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    /**
     * Append a Markdown section header (when the role changes) and the content body
     * for the given display role.
     *
     * @param sb          target StringBuilder
     * @param displayRole "user", "assistant", "thought", or "tool"
     * @param text        the message text
     * @param toolTitle   optional tool title (for "tool" role), may be null
     * @param lastRole    single-element array tracking the previous role
     */
    private static void appendMarkdownContent(StringBuilder sb, String displayRole,
            String text, String toolTitle, String[] lastRole) {
        String heading = switch (displayRole) {
            case "thought" -> "Thought";
            case "tool" -> "Tool";
            default -> displayRole.substring(0, 1).toUpperCase() + displayRole.substring(1);
        };
        appendRoleHeading(sb, heading, lastRole);
        switch (displayRole) {
            case "user" -> sb.append(text).append("\n\n");
            case "thought" -> {
                sb.append("> **Thinking**\n>\n");
                for (String line : text.split("\n", -1)) {
                    sb.append("> ").append(line).append("\n");
                }
                sb.append("\n");
            }
            case "tool" -> {
                sb.append("**Tool");
                if (toolTitle != null && !toolTitle.isEmpty() && !"Tool".equals(toolTitle))
                    sb.append(": ").append(escapeMarkdownInline(singleLine(toolTitle)));
                sb.append("**\n\n");
                appendFencedText(sb, text);
            }
            default -> sb.append(text).append("\n\n");
        }
    }

    private static void appendRoleHeading(StringBuilder sb, String heading, String[] lastRole) {
        if (!heading.equals(lastRole[0])) {
            sb.append("### ").append(heading).append("\n\n");
            lastRole[0] = heading;
        }
    }

    private static void appendFencedText(StringBuilder sb, String text) {
        int longestRun = 0;
        int currentRun = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '`') {
                currentRun++;
                longestRun = Math.max(longestRun, currentRun);
            } else {
                currentRun = 0;
            }
        }
        int fenceLength = Math.max(3, longestRun + 1);
        String fence = "`".repeat(fenceLength);
        sb.append(fence).append("text\n").append(text).append("\n")
          .append(fence).append("\n\n");
    }

    /** Notified on the EDT about the outcome of the export flow. */
    @FunctionalInterface
    public interface ExportListener {
        void exported(File file);

        /** The user closed the chooser or declined the overwrite. */
        default void cancelled() {
        }

        /** The file could not be written. */
        default void failed(IOException cause) {
        }
    }

    /**
     * Export conversation to a file.
     *
     * @param parent      parent component for the dialog
     * @param markdown    the content to write
     * @param defaultName suggested file name (e.g. "session.md")
     * @param listener    notified on the EDT once the file was written, may be null
     */
    public static void export(Component parent, String markdown, String defaultName, ExportListener listener) {
        export(parent, markdown, defaultName, true, listener);
    }

    /**
     * Export conversation to a file.
     *
     * @param parent      parent component for the dialog
     * @param markdown    the content to write
     * @param defaultName suggested file name (e.g. "session.md")
     * @param openAfter   whether to open the file in the editor after saving
     * @param listener    notified on the EDT once the file was written, may be null
     */
    static void export(Component parent, String markdown, String defaultName, boolean openAfter,
                       ExportListener listener) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(NbBundle.getMessage(UIBundle.class, "TITLE_ExportConv"));
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            if (listener != null) {
                SwingUtilities.invokeLater(listener::cancelled);
            }
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.exists()) {
            Object confirm = DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(
                    NbBundle.getMessage(UIBundle.class, "MSG_OverwriteConfirm", file.getName()),
                    NbBundle.getMessage(UIBundle.class, "TITLE_ExportConv"),
                    NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE));
            if (confirm != NotifyDescriptor.YES_OPTION) {
                if (listener != null) {
                    SwingUtilities.invokeLater(listener::cancelled);
                }
                return;
            }
        }
        EXPORT_RP.post(() -> {
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(markdown);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Failed to export conversation", ex);
                if (listener != null) {
                    SwingUtilities.invokeLater(() -> listener.failed(ex));
                }
                return;
            }
            LOG.log(Level.FINE, "Conversation exported to {0}", file.getAbsolutePath());
            if (listener != null) {
                SwingUtilities.invokeLater(() -> listener.exported(file));
            }
            if (openAfter) {
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
                if (fo != null) {
                    try {
                        DataObject dobj = DataObject.find(fo);
                        EditorCookie ec = dobj.getLookup().lookup(EditorCookie.class);
                        if (ec != null) {
                            SwingUtilities.invokeLater(() -> ec.open());
                        }
                    } catch (IOException ex) {
                        LOG.log(Level.FINE, "Could not open exported file", ex);
                    }
                }
            }
        });
    }

    /**
     * Derive a safe default file name from a session title.
     *
     * @param title session title, may be null
     * @return sanitized file name ending in ".md"
     */
    public static String defaultFileName(String title) {
        if (title != null && !title.startsWith("New Session")) {
            return TitleSanitizePattern.sanitize(title) + ".md";
        }
        return "session.md";
    }
}
