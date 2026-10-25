package github.anandb.netbeans.ui;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;

import javax.swing.JPanel;

import org.openide.util.NbBundle;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import static org.apache.commons.lang3.StringUtils.isBlank;

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

    // ---- Page setup helpers ---------------------------------------------------

    private record PageSetup(ColorTheme theme, boolean dark, String pageTitle,
                             String timestamp, StringBuilder body) {}

    private static PageSetup initPage(String sessionTitle) {
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
        return new PageSetup(theme, dark, pageTitle, timestamp, body);
    }

    private static String finishPage(PageSetup setup) {
        setup.body().append("</div>\n");
        return buildPageHtml(setup.pageTitle(), setup.timestamp(),
                setup.body().toString(), setup.theme());
    }

    // ---- Export stylesheet template -------------------------------------------

    private static volatile String cachedCssTemplate;

    private static String loadCssTemplate() {
        String template = cachedCssTemplate;
        if (template != null) {
            return template;
        }
        try (InputStream in = HtmlConversationExporter.class
                .getResourceAsStream("export-style.css.template")) {
            if (in == null) {
                throw new IllegalStateException(NbBundle.getMessage(
                        HtmlConversationExporter.class, "ERR_ExportCssTemplateNotFound"));
            }
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            cachedCssTemplate = template;
            return template;
        } catch (IOException e) {
            throw new IllegalStateException(NbBundle.getMessage(
                    HtmlConversationExporter.class, "ERR_LoadExportCssTemplateFailed"), e);
        }
    }

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
        // Check for custom user icon first (same preference as sidebar)
        String customPath = PluginSettings.getCustomUserIcon();
        if (customPath != null && !customPath.isEmpty()) {
            File file = new File(customPath);
            if (file.exists()) {
                try {
                    String ext = customPath.toLowerCase();
                    String mime = ext.endsWith(".png") ? "image/png"
                            : ext.endsWith(".jpg") || ext.endsWith(".jpeg") ? "image/jpeg"
                            : ext.endsWith(".gif") ? "image/gif" : null;
                    if (mime != null) {
                        byte[] data = Files.readAllBytes(file.toPath());
                        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
                    }
                } catch (Exception e) {
                    // Fall through to default
                }
            }
        }
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

    // ---- Main entry points ----------------------------------------------------

    /**
     * Build a self-contained HTML page from the model message list (all messages,
     * not just currently-rendered bubbles). Falls back to the UI-based export
     * when messages is null.
     */
    static String generateHtml(List<Message> messages, String sessionTitle) {
        if (messages == null || messages.isEmpty()) return "";
        PageSetup setup = initPage(sessionTitle);

        for (Message m : messages) {
            String role = m.extractRole();
            String text = m.extractText();
            if (text.isBlank()) continue;

            switch (role) {
                case "user" -> setup.body().append(buildUserHtml(text, setup.dark()));
                case "thought" -> setup.body().append(buildThoughtHtml(text, setup.theme()));
                case "tool" -> setup.body().append(buildToolHtml(text, "Tool", setup.dark()));
                default -> setup.body().append(buildAssistantHtml(text, setup.theme()));
            }
        }
        return finishPage(setup);
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
        PageSetup setup = initPage(sessionTitle);

        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble bubble) {
                List<CollapsibleToolPane.ToolSegment> segments =
                        (List<CollapsibleToolPane.ToolSegment>)
                                bubble.getClientProperty("nb-complete.segments");
                if (segments != null && !segments.isEmpty()) {
                    setup.body().append(buildActivityHtml(segments, setup.theme()));
                    continue;
                }

                String role = bubble.getRole();
                String text = bubble.getRawText();
                if (isBlank(text)) continue;

                switch (role != null ? role : "assistant") {
                    case "user" -> setup.body().append(buildUserHtml(text, setup.dark()));
                    case "thought" -> setup.body().append(buildThoughtHtml(text, setup.theme()));
                    case "tool" -> setup.body().append(buildToolHtml(text, bubble.getToolTitle(), setup.dark()));
                    default -> setup.body().append(buildAssistantHtml(text, setup.theme()));
                }
            }
        }

        return finishPage(setup);
    }

    // ---- Bubble renderers ----------------------------------------------------

    private static String buildUserHtml(String text, boolean dark) {
        String iconUrl = userIcon(dark);
        String escaped = escHtml(text).replace("\n", "<br/>\n");
        return "  <div class=\"message user\">\n"
             + "    <div class=\"bubble user-bubble\">\n"
             + "      <div class=\"bubble-header\">\n"
             + "        <img class=\"avatar\" src=\"" + iconUrl + "\" alt=\"User\"/>\n"
             + "      </div>\n"
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

        String css = loadCssTemplate()
                .replace("$fontFamily", fontFamily)
                .replace("$fontSize", fontSize + "px")
                .replace("$fg", fg)
                .replace("$bg", bg)
                .replace("$borderColor", borderColor)
                .replace("$muted", muted)
                .replace("$userBg", userBg)
                .replace("$assistantBg", assistantBg)
                .replace("$tableBg", tableBg)
                .replace("$tableHeaderBg", tableHeaderBg)
                .replace("$tableAlternate", tableAlternate)
                .replace("$linkColor", linkColor)
                .replace("$codeBg", codeBg)
                .replace("$codeFg", codeFg)
                .replace("$monoFamily", monoFamily)
                .replace("$base2", base2)
                .replace("$yellow", yellow)
                .replace("$accent", accent);

        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
             + "<meta charset=\"UTF-8\">\n"
             + "<meta name=\"viewport\" content=\"width=device-width, "
             + "initial-scale=1.0\">\n"
             + "<title>" + escHtml(title) + " - exported " + timestamp + "</title>\n"
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
        ConversationExporter.export(parent, html, defaultName, false);
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
