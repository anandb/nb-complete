package github.anandb.netbeans.ui;

import java.awt.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.support.Logger;
import static org.apache.commons.lang3.StringUtils.isBlank;

import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Stateless utility for exporting conversation history to self-contained
 * HTML files that mimic the sidebar chat view.
 */
final class HtmlConversationExporter {

    private static final Logger LOG = Logger.from(HtmlConversationExporter.class);

    // ---- Flexmark markdown renderer (same setup as HtmlContentPreparer) ----

    private static final Parser FLEXMARK_PARSER;
    private static final HtmlRenderer FLEXMARK_RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        FLEXMARK_PARSER = Parser.builder(options).build();
        FLEXMARK_RENDERER = HtmlRenderer.builder(options).build();
    }

    private HtmlConversationExporter() {}

    // ---- SVG icon cache (loaded once from classpath) --------------------------

    private static String cachedUserIconLight;
    private static String cachedUserIconDark;
    private static String cachedToolIconLight;
    private static String cachedToolIconDark;
    private static String cachedBrainIconLight;
    private static String cachedBrainIconDark;
    private static String cachedGoIconLight;
    private static String cachedGoIconDark;

    private static String userIcon(boolean dark) {
        if (dark) {
            if (cachedUserIconDark == null)
                cachedUserIconDark = svgToDataUrl("icons/user_dark.svg");
            return cachedUserIconDark;
        }
        if (cachedUserIconLight == null)
            cachedUserIconLight = svgToDataUrl("icons/user.svg");
        return cachedUserIconLight;
    }

    private static String toolIcon(boolean dark) {
        if (dark) {
            if (cachedToolIconDark == null)
                cachedToolIconDark = svgToDataUrl("icons/tool_dark.svg");
            return cachedToolIconDark;
        }
        if (cachedToolIconLight == null)
            cachedToolIconLight = svgToDataUrl("icons/tool.svg");
        return cachedToolIconLight;
    }

    private static String brainIcon(boolean dark) {
        if (dark) {
            if (cachedBrainIconDark == null)
                cachedBrainIconDark = svgToDataUrl("icons/brain_dark.svg");
            return cachedBrainIconDark;
        }
        if (cachedBrainIconLight == null)
            cachedBrainIconLight = svgToDataUrl("icons/brain.svg");
        return cachedBrainIconLight;
    }

    private static String goIcon(boolean dark) {
        if (dark) {
            if (cachedGoIconDark == null)
                cachedGoIconDark = svgToDataUrl("icons/go_dark.svg");
            return cachedGoIconDark;
        }
        if (cachedGoIconLight == null)
            cachedGoIconLight = svgToDataUrl("icons/go.svg");
        return cachedGoIconLight;
    }

    /** Loads an SVG file from the classpath and returns a base64 data URL. */
    private static String svgToDataUrl(String resourcePath) {
        try (InputStream in = HtmlConversationExporter.class
                .getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOG.log(Level.WARNING, "SVG icon not found: {0}", resourcePath);
                return "";
            }
            byte[] bytes = in.readAllBytes();
            String b64 = Base64.getEncoder().encodeToString(bytes);
            return "data:image/svg+xml;base64," + b64;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load SVG icon: " + resourcePath, e);
            return "";
        }
    }

    // ---- Main entry point -----------------------------------------------------

    // ---- Text extraction helpers (model-based export) -------------------------

    /** Extract the display role from a Message model. */
    private static String extractRole(Message m) {
        if ("user".equals(m.type())) return "user";
        if ("thinking".equals(m.state())) return "thought";
        return "assistant";
    }

    /** Extract the display text from a Message model. */
    static String extractText(Message m) {
        StringBuilder sb = new StringBuilder();
        if ("user".equals(m.type())) {
            if (m.prompt() != null) {
                if (m.prompt().text() != null) sb.append(m.prompt().text());
                if (m.prompt().parts() != null) {
                    for (Message.ContentPart part : m.prompt().parts()) {
                        String pt = part.getDisplayText();
                        if (pt != null && !pt.isEmpty()) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(pt);
                        }
                    }
                }
            }
        } else {
            if (m.completion() != null) {
                if (m.completion().text() != null) sb.append(m.completion().text());
                if (m.completion().parts() != null) {
                    for (Message.ContentPart part : m.completion().parts()) {
                        String pt = part.getDisplayText();
                        if (pt != null && !pt.isEmpty()) {
                            if (sb.length() > 0) sb.append("\n\n");
                            sb.append(pt);
                        }
                    }
                }
            }
        }
        return sb.toString().strip();
    }

    // ---- Main entry points ----------------------------------------------------

    /**
     * Build a self-contained HTML page from the model message list (all messages,
     * not just currently-rendered bubbles). Falls back to the UI-based export
     * when messages is null.
     */
    static String generateHtml(List<Message> messages, String sessionTitle) {
        if (messages == null || messages.isEmpty()) return "";
        ColorTheme theme = ThemeManager.getCurrentTheme();
        boolean dark = theme.isDark();
        String pageTitle = sessionTitle != null ? sessionTitle : "Conversation";
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"chat-container\">\n");

        body.append("  <div class=\"chat-header\">\n")
            .append("    <h1>").append(escHtml(pageTitle)).append("</h1>\n")
            .append("    <span class=\"header-date\">Exported ")
            .append(timestamp).append("</span>\n")
            .append("  </div>\n");

        for (Message m : messages) {
            String role = extractRole(m);
            String text = extractText(m);
            if (isBlank(text)) continue;

            switch (role) {
                case "user" -> body.append(buildUserHtml(text, dark));
                case "thought" -> body.append(buildThoughtHtml(text, theme));
                case "tool" -> {
                    // Tool from model doesn't have toolTitle; use default
                    body.append(buildToolHtml(text, "Tool", dark));
                }
                default -> body.append(buildAssistantHtml(text, theme));
            }
        }
        body.append("</div>\n");
        return buildPageHtml(pageTitle, timestamp, body.toString(), theme);
    }

    /**
     * Build a self-contained HTML page from all visible bubbles in the chat panel.
     *
     * @param messagesContainer the container holding {@link MessageBubble} instances
     * @param sessionTitle      optional session title
     * @return HTML string, never null
     */
    @SuppressWarnings("unchecked")
    static String generateHtml(JPanel messagesContainer, String sessionTitle) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        boolean dark = theme.isDark();
        String pageTitle = sessionTitle != null ? sessionTitle : "Conversation";
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"chat-container\">\n");

        body.append("  <div class=\"chat-header\">\n")
            .append("    <h1>").append(escHtml(pageTitle)).append("</h1>\n")
            .append("    <span class=\"header-date\">Exported ")
            .append(timestamp).append("</span>\n")
            .append("  </div>\n");

        String lastRole = null;
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble bubble) {
                List<CollapsibleToolPane.ToolSegment> segments =
                        (List<CollapsibleToolPane.ToolSegment>)
                                bubble.getClientProperty("nb-complete.segments");
                if (segments != null && !segments.isEmpty()) {
                    body.append(buildActivityHtml(segments, theme));
                    lastRole = null;
                    continue;
                }

                String role = bubble.getRole();
                String text = bubble.getRawText();
                if (isBlank(text)) continue;

                switch (role != null ? role : "assistant") {
                    case "user" -> body.append(buildUserHtml(text, dark));
                    case "thought" -> body.append(buildThoughtHtml(text, theme));
                    case "tool" -> body.append(buildToolHtml(text, bubble.getToolTitle(), dark));
                    default -> body.append(buildAssistantHtml(text, theme));
                }
            }
        }

        body.append("</div>\n");
        return buildPageHtml(pageTitle, timestamp, body.toString(), theme);
    }

    // ---- Bubble renderers ----------------------------------------------------

    private static String buildUserHtml(String text, boolean dark) {
        String iconUrl = userIcon(dark);
        String escaped = escHtml(text).replace("\n", "<br/>\n");
        return "  <div class=\"message user\">\n"
             + "    <img class=\"avatar\" src=\"" + iconUrl + "\" alt=\"User\"/>\n"
             + "    <div class=\"bubble user-bubble\">\n"
             + "      <div class=\"content\">" + escaped + "</div>\n"
             + "    </div>\n"
             + "  </div>\n";
    }

    private static String buildAssistantHtml(String text, ColorTheme theme) {
        String html = renderMarkdown(text);
        html = postProcessHtml(html, theme);
        return "  <div class=\"message assistant\">\n"
             + "    <div class=\"bubble assistant-bubble\">\n"
             + "      <div class=\"content\">" + html + "</div>\n"
             + "    </div>\n"
             + "  </div>\n";
    }

    private static String buildThoughtHtml(String text, ColorTheme theme) {
        String html = renderMarkdown(text);
        html = postProcessHtml(html, theme);
        return "  <div class=\"message thought\">\n"
             + "    <div class=\"activity-segment thought\">\n"
             + "      <div class=\"segment-header\">\n"
             + "        <img src=\"" + brainIcon(theme.isDark())
             + "\" alt=\"\" width=\"16\" height=\"16\"/> Thinking\n"
             + "      </div>\n"
             + "      <div class=\"segment-body\">" + html + "</div>\n"
             + "    </div>\n"
             + "  </div>\n";
    }

    private static String buildToolHtml(String text, String toolTitle, boolean dark) {
        String title = toolTitle != null && !toolTitle.isEmpty() ? toolTitle : "Tool";
        String escaped = escHtml(text);
        return "  <div class=\"message tool\">\n"
             + "    <div class=\"activity-segment tool\">\n"
             + "      <div class=\"segment-header\">\n"
             + "        <img src=\"" + toolIcon(dark)
             + "\" alt=\"\" width=\"16\" height=\"16\"/> " + escHtml(title) + "\n"
             + "      </div>\n"
             + "      <div class=\"segment-body\"><pre>" + escaped + "</pre></div>\n"
             + "    </div>\n"
             + "  </div>\n";
    }

    private static String buildActivityHtml(
            List<CollapsibleToolPane.ToolSegment> segments, ColorTheme theme) {
        boolean dark = theme.isDark();
        StringBuilder sb = new StringBuilder();
        sb.append("  <div class=\"activity\">\n")
          .append("    <details class=\"activity-pane\">\n")
          .append("      <summary>\n")
          .append("        <img src=\"").append(goIcon(dark))
          .append("\" alt=\"\" width=\"16\" height=\"16\"/> Activity\n")
          .append("      </summary>\n");

        for (CollapsibleToolPane.ToolSegment seg : segments) {
            if (isBlank(seg.text())) continue;
            String segTitle = seg.title() != null ? seg.title() : "";
            sb.append("      <details class=\"segment ")
              .append(seg.isThought() ? "thought" : "tool").append("\">\n")
              .append("        <summary>\n")
              .append("          <img src=\"")
              .append(seg.isThought() ? brainIcon(dark) : toolIcon(dark))
              .append("\" alt=\"\" width=\"16\" height=\"16\"/> ")
              .append(escHtml(segTitle)).append("\n")
              .append("        </summary>\n")
              .append("        <div class=\"segment-body\">");
            if (seg.isThought()) {
                String html = renderMarkdown(seg.text());
                html = postProcessHtml(html, theme);
                sb.append(html);
            } else {
                sb.append("<pre>").append(escHtml(seg.text())).append("</pre>");
            }
            sb.append("</div>\n")
              .append("      </details>\n");
        }

        sb.append("    </details>\n")
          .append("  </div>\n");
        return sb.toString();
    }

    // ---- HTML page assembly ---------------------------------------------------

    private static String buildPageHtml(String title, String timestamp,
                                        String body, ColorTheme theme) {
        boolean dark = theme.isDark();
        String fg = dark ? "#e0e0e0" : "#1f1f1f";
        String bg = dark ? "#1e1f22" : "#ffffff";
        String userBg = theme.toHtmlHex(theme.bubbleUser());
        String assistantBg = theme.toHtmlHex(theme.bubbleAssistant());
        String muted = theme.toHtmlHex(theme.mutedForeground());
        String base2 = dark ? "#2b2d31" : "#f0f0f0";
        String yellow = theme.toHtmlHex(theme.yellow());
        String accent = theme.toHtmlHex(theme.accent());
        String codeBg = theme.toHtmlHex(theme.codeBackground());
        String codeFg = theme.toHtmlHex(theme.codeForeground());
        String linkColor = dark ? "#589DF6" : "#268BD2";
        String borderColor = dark ? "#3b3f44" : "#d0d0d0";
        String tableBg = theme.toHtmlHex(theme.tableBackground());
        String tableHeaderBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String tableAlternate = theme.toHtmlHex(theme.tableRowAlternate());
        String fontFamily = UIUtils.fontStackWithActual();
        String monoFamily = UIUtils.MONO_STACK;
        int fontSize = ThemeManager.getFont().getSize() - 2;

        String css = """
body {
    font-family: %s;
    font-size: %dpx;
    color: %s;
    background: %s;
    margin: 0;
    padding: 16px;
    line-height: 1.4;
}
.chat-container {
    max-width: 800px;
    margin: 0 auto;
}
.chat-header {
    text-align: center;
    padding: 8px 0 16px;
    border-bottom: 1px solid %s;
    margin-bottom: 16px;
}
.chat-header h1 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
}
.header-date {
    font-size: 11px;
    color: %s;
}
.message {
    display: flex;
    margin-bottom: 8px;
    gap: 8px;
    align-items: flex-start;
}
.message.user {
    flex-direction: row-reverse;
}
.message.assistant {
    flex-direction: row;
}
.message.thought,
.message.tool {
    flex-direction: row;
}
.avatar {
    width: 44px;
    height: 44px;
    flex-shrink: 0;
    border-radius: 50%%;
}
.message.user .avatar {
    display: block;
}
.message.assistant .avatar,
.message.thought .avatar,
.message.tool .avatar {
    display: none;
}
.bubble {
    max-width: 75%%;
    padding: 8px 12px;
    word-wrap: break-word;
    overflow-wrap: break-word;
}
.user-bubble {
    background: %s;
    border-radius: 16px 16px 4px 16px;
}
.assistant-bubble {
    background: %s;
    border-radius: 16px 16px 16px 4px;
}
.content p,
.content div,
.content h1,
.content h2,
.content h3,
.content h4,
.content h5,
.content h6 {
    margin: 2px 0;
    text-align: left;
}
.content ul,
.content ol {
    padding-left: 20px;
    margin: 4px 0;
}
.content li {
    margin: 2px 0;
}
.content table {
    width: 100%%;
    border-collapse: collapse;
    margin: 8px 0;
    background: %s;
}
.content th {
    background: %s;
    padding: 8px;
    border: 1px solid %s;
    text-align: left;
    font-weight: bold;
}
.content td {
    padding: 8px;
    border: 1px solid %s;
    vertical-align: top;
}
.content tr:nth-child(even) td {
    background: %s;
}
.content a {
    color: %s;
    text-decoration: none;
    font-weight: 500;
}
.content a:hover {
    text-decoration: underline;
}
.content pre {
    background: %s;
    color: %s;
    padding: 10px;
    border-radius: 4px;
    font-family: %s;
    font-size: 13px;
    overflow-x: auto;
    margin: 6px 0;
    white-space: pre-wrap;
    word-wrap: break-word;
}
.content code {
    background: rgba(255,255,255,0.1);
    padding: 2px 4px;
    border-radius: 3px;
    font-family: %s;
    font-size: 12px;
}
.activity {
    margin: 4px 0;
    border-radius: 12px;
    overflow: hidden;
    border: 1px solid %s;
}
.activity-pane > summary {
    padding: 6px 10px;
    background: %s;
    cursor: pointer;
    font-weight: bold;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 6px;
    user-select: none;
}
.activity-pane > summary img {
    width: 16px;
    height: 16px;
}
.activity-pane > summary::-webkit-details-marker {
    display: none;
}
.activity-pane > summary::before {
    content: '▶';
    margin-right: 4px;
    font-size: 10px;
}
.activity-pane[open] > summary::before {
    content: '▼';
}
.segment {
    margin: 0;
    border-top: 1px solid %s;
}
.segment summary {
    padding: 4px 8px 4px 24px;
    font-size: 12px;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 4px;
    user-select: none;
    border-left: 4px solid transparent;
    background: %s;
}
.segment.thought summary {
    border-left-color: %s;
}
.segment.tool summary {
    border-left-color: %s;
}
.segment summary img {
    width: 16px;
    height: 16px;
}
.segment summary::-webkit-details-marker {
    display: none;
}
.segment summary::before {
    content: '▶';
    margin-right: 4px;
    font-size: 10px;
    color: %s;
}
.segment[open] > summary::before {
    content: '▼';
}
.segment-body {
    padding: 8px 12px 8px 28px;
    font-size: 13px;
    line-height: 1.3;
    border-left: 4px solid transparent;
    border-left-color: inherit;
}
.segment-body pre {
    background: %s;
    color: %s;
    padding: 8px;
    border-radius: 4px;
    font-family: %s;
    font-size: 12px;
    margin: 0;
    white-space: pre-wrap;
    word-wrap: break-word;
}
.segment.thought .segment-body pre {
    background: %s;
}
.activity-segment {
    margin: 2px 0;
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid %s;
    width: 100%%;
}
.segment-header {
    padding: 4px 8px;
    font-size: 12px;
    font-weight: bold;
    display: flex;
    align-items: center;
    gap: 4px;
    background: %s;
}
.segment-header img {
    width: 16px;
    height: 16px;
}
.thought .segment-header {
    border-left: 4px solid %s;
}
.tool .segment-header {
    border-left: 4px solid %s;
}
.thought .segment-body {
    border-left: 4px solid %s;
}
.tool .segment-body {
    border-left: 4px solid %s;
}
""";
        css = css.formatted(
            fontFamily, fontSize, fg, bg, borderColor, muted,
            userBg, assistantBg,
            tableBg, tableHeaderBg, borderColor, borderColor, tableAlternate, linkColor,
            codeBg, codeFg, monoFamily, monoFamily,
            borderColor, base2, borderColor,
            base2, yellow, accent, muted,
            codeBg, codeFg, monoFamily, codeBg,
            borderColor, base2, yellow, accent, yellow, accent
        );

        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
             + "<meta charset=\"UTF-8\">\n"
             + "<meta name=\"viewport\" content=\"width=device-width, "
             + "initial-scale=1.0\">\n"
             + "<title>" + escHtml(title) + "</title>\n"
             + "<style>\n" + css + "</style>\n"
             + "</head>\n<body>\n"
             + body
             + "</body>\n</html>\n";
    }

    // ---- Markdown rendering ---------------------------------------------------

    private static String renderMarkdown(String markdown) {
        if (isBlank(markdown)) return "";
        return FLEXMARK_RENDERER.render(FLEXMARK_PARSER.parse(markdown));
    }

    /** Minimal post-processing: double-space suppression and table wrapping. */
    private static String postProcessHtml(String html, ColorTheme theme) {
        // Replace multiple spaces with &nbsp; for code-like content,
        // but not inside <pre> blocks or ASCII art
        String result = html.replace("<table>",
                "<table align='left' style='border-collapse: collapse; width: 100%; margin: 8px 0;'>");
        result = result.replace("<th>",
                "<th align='left' style='padding: 8px; text-align: left;'><b>");
        result = result.replace("<td>",
                "<td align='left' style='padding: 8px; vertical-align: top;'>");
        result = result.replace("</th>", "</b></th>");
        return result;
    }

    // ---- HTML escaping --------------------------------------------------------

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ---- Export dialog --------------------------------------------------------

    /**
     * Show a Save dialog, write the HTML to the chosen file, and open it
     * in the IDE editor.
     */
    static void export(Component parent, String html, String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(NbBundle.getMessage(
                HtmlConversationExporter.class, "TITLE_ExportConv"));
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.exists()) {
                int confirm = JOptionPane.showConfirmDialog(parent,
                        NbBundle.getMessage(HtmlConversationExporter.class,
                                "MSG_OverwriteConfirm", file.getName()),
                        NbBundle.getMessage(HtmlConversationExporter.class,
                                "TITLE_ExportConv"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            RequestProcessor.getDefault().post(() -> {
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    writer.write(html);
                    LOG.log(Level.FINE, "Conversation exported to {0}",
                            file.getAbsolutePath());
                    FileObject fo = FileUtil.toFileObject(
                            FileUtil.normalizeFile(file));
                    if (fo != null) {
                        DataObject dobj = DataObject.find(fo);
                        EditorCookie ec = dobj.getLookup()
                                .lookup(EditorCookie.class);
                        if (ec != null) {
                            SwingUtilities.invokeLater(() -> ec.open());
                        }
                    }
                } catch (IOException ex) {
                    LOG.log(Level.WARNING,
                            "Failed to export conversation", ex);
                }
            });
        }
    }

    /**
     * Derive a safe default file name from a session title.
     *
     * @param title session title, may be null
     * @return sanitized file name ending in ".html"
     */
    static String defaultFileName(String title) {
        if (title != null && !title.startsWith("New Session")) {
            return title.replaceAll("[^a-zA-Z0-9._-]", "_") + ".html";
        }
        return "session.html";
    }
}
