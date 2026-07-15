package github.anandb.netbeans.ui;

import java.awt.Component;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.logging.Level;

import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Stateless utility for exporting conversation history to Markdown files.
 * Extracted from {@link AssistantTopComponent} and {@link ChatThreadPanel}
 * to keep export logic in one place.
 */
final class ConversationExporter {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(ConversationExporter.class.getName());
    private static final Pattern INVALID_TITLE_PATTERN =
            Pattern.compile("[^a-zA-Z0-9._-]");

    private ConversationExporter() { }

    /**
     * Build a Markdown representation of all visible bubbles in the chat panel.
     *
     * @param messagesContainer the container holding {@link MessageBubble} instances
     * @param sessionTitle      optional session title for the frontmatter header
     * @return Markdown string, never null
     */
    @SuppressWarnings("unchecked")
    static String generateMarkdown(JPanel messagesContainer, String sessionTitle) {
        StringBuilder sb = new StringBuilder();
        // YAML frontmatter
        sb.append("---\n");
        sb.append("title: \"").append(escYaml(sessionTitle != null ? sessionTitle : "Conversation")).append("\"\n");
        sb.append("date: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n");
        sb.append("---\n\n");
        sb.append("# Conversation\n\n");

        String lastRole = null;
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble bubble) {
                // Combined activity bubbles store content in ToolSegment records,
                // not in the text StringBuilder (which is empty for these bubbles).
                List<CollapsibleToolPane.ToolSegment> segments =
                        (List<CollapsibleToolPane.ToolSegment>) bubble.getClientProperty("nb-complete.segments");
                if (segments != null && !segments.isEmpty()) {
                    for (CollapsibleToolPane.ToolSegment seg : segments) {
                        if (seg.text() == null || seg.text().trim().isEmpty()) continue;
                        String title = seg.title() != null ? seg.title() : "";
                        if (seg.isThought()) {
                            sb.append("> **Thinking**\n>\n");
                            for (String line : seg.text().split("\n", -1)) {
                                sb.append("> ").append(line).append("\n");
                            }
                            sb.append("\n");
                        } else {
                            sb.append("**Tool");
                            if (!title.isEmpty() && !"Tool".equals(title)) sb.append(": ").append(title);
                            sb.append("**\n\n");
                            sb.append("```text\n").append(seg.text()).append("\n```\n\n");
                        }
                    }
                    lastRole = null;
                    continue;
                }
                String role = bubble.getRole();
                String text = bubble.getRawText();
                if (text == null || text.trim().isEmpty()) continue;
                String title = bubble.getToolTitle() != null ? bubble.getToolTitle() : "";
                String displayRole = role != null ? role : "assistant";
                // Add section header only when role changes
                if (!displayRole.equals(lastRole)) {
                    sb.append("## ").append(displayRole.substring(0, 1).toUpperCase())
                      .append(displayRole.substring(1)).append("\n\n");
                    lastRole = displayRole;
                }
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
                        if (!title.isEmpty() && !"Tool".equals(title)) sb.append(": ").append(title);
                        sb.append("**\n\n");
                        sb.append("```text\n").append(text).append("\n```\n\n");
                    }
                    default -> sb.append(text).append("\n\n");
                }
            }
        }
        return sb.toString();
    }

    /** Escape characters that could break YAML strings. */
    private static String escYaml(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Show a Save dialog, write the markdown to the chosen file, and open it
     * in the IDE editor.
     *
     * @param parent      parent component for the dialog
     * @param markdown    the content to write
     * @param defaultName suggested file name (e.g. "session.md")
     */
    static void export(Component parent, String markdown, String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(NbBundle.getMessage(ConversationExporter.class, "TITLE_ExportConv"));
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            RequestProcessor.getDefault().post(() -> {
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(markdown);
                    LOG.log(Level.FINE, "Conversation exported to {0}", file.getAbsolutePath());
                    FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
                    if (fo != null) {
                        DataObject dobj = DataObject.find(fo);
                        EditorCookie ec = dobj.getLookup().lookup(EditorCookie.class);
                        if (ec != null) {
                            SwingUtilities.invokeLater(() -> ec.open());
                        }
                    }
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, "Failed to export conversation", ex);
                }
            });
        }
    }

    /**
     * Derive a safe default file name from a session title.
     *
     * @param title session title, may be null
     * @return sanitized file name ending in ".md"
     */
    static String defaultFileName(String title) {
        if (title != null && !title.startsWith("New Session")) {
            return INVALID_TITLE_PATTERN.matcher(title).replaceAll("_") + ".md";
        }
        return "session.md";
    }
}
