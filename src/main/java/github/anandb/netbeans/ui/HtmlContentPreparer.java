package github.anandb.netbeans.ui;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TextScanner;
import static github.anandb.netbeans.ui.UIUtils.MONO_STACK;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HtmlContentPreparer {

    private HtmlContentPreparer() {}

    private static final Logger LOG = Logger.from(HtmlContentPreparer.class);

    private static final Parser FLEXMARK_PARSER;
    private static final HtmlRenderer FLEXMARK_RENDERER;

    /** LRU cache for markdown→HTML output. Bounded to 256 entries to cap memory. */
    private static final Map<String, String> MARKDOWN_HTML_CACHE = new LinkedHashMap<>() {
        private static final int MAX_ENTRIES = 256;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        FLEXMARK_PARSER = Parser.builder(options).build();
        FLEXMARK_RENDERER = HtmlRenderer.builder(options).build();
    }

    public static String prepareHtml(String markdown, ColorTheme theme, String role, boolean incremental) {
        String html = computeOrGetCachedHtml(markdown);

        if (incremental) {
            return html;
        }

        String tableBg = theme.toHtmlHex(theme.tableBackground());
        String headerBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String borderColor = theme.toHtmlHex(theme.tableBorder());

        String tableTag = "<table align='left' border='1' bordercolor='" + borderColor
                + "' cellspacing='0' cellpadding='8' style='border-collapse: collapse; width: 100%; margin: 8px 0; background-color: "
                + tableBg + ";'>";
        String thTag = "<th align='left' bgcolor='" + headerBg
                + "' style='padding: 8px; border: 1px solid " + borderColor + "; text-align: left;'><b>";
        String tdTag = "<td align='left' style='padding: 8px; border: 1px solid " + borderColor + "; vertical-align: top;'>";
        String pTag = "<p align='left' style='text-align: left !important;'>";
        String divTag = "<div align='left' style='text-align: left !important;'>";

        html = batchReplace(html,
                "</th>", "</b></th>",
                "<table>", tableTag,
                "<th>", thTag,
                "<td>", tdTag,
                "<p>", pTag,
                "<div>", divTag);

        boolean hasArt = TextScanner.containsAsciiArt(markdown);
        if (!hasArt && !"user".equals(role)) {
            html = html.replace("  ", " &nbsp;");
        } else if (hasArt) {
            LOG.fine("Contains ASCII art, not replacing spaces");
        }

        boolean isAssistant = !"user".equals(role) && !"error".equals(role) && !"tool".equals(role);
        String customCss = theme.toCss(null, isAssistant, ThemeManager.getFont().getSize() - 2);
        if ("error".equals(role)) {
            customCss += " body { color: #D32F2F; font-weight: bold; }";
        } else if ("user".equals(role)) {
            customCss += " body { font-weight: 300; }";
        }

        String bodyStyle = "margin: 0; padding: 0; text-align: left !important; width: 100%;";
        if (hasArt) {
            String monoStack = MONO_STACK;
            customCss += " .ascii-art { font-family: " + monoStack + "; line-height: 1.0; }";
            html = html.replace("  ", " &nbsp;");
            html = html.replace("\n", "<br/>");
            html = "<div class='ascii-art'>" + html + "</div>";
        }

        return "<html><head><style>" + customCss + "</style></head><body style='" + bodyStyle + "'>"
                + html
                + "</body></html>";
    }

    public static boolean containsAsciiArt(String markdown) {
        return TextScanner.containsAsciiArt(markdown);
    }

    /**
     * Returns cached HTML for markdown input, or parses + caches on miss.
     * The cache is bounded to 256 entries.  Text longer than 32 KB is not
     * cached (avoid holding large strings for rare long messages).
     */
    private static String computeOrGetCachedHtml(String markdown) {
        if (markdown.length() > 32768) {
            return FLEXMARK_RENDERER.render(FLEXMARK_PARSER.parse(markdown));
        }
        synchronized (MARKDOWN_HTML_CACHE) {
            return MARKDOWN_HTML_CACHE.computeIfAbsent(markdown,
                    md -> FLEXMARK_RENDERER.render(FLEXMARK_PARSER.parse(md)));
        }
    }

    /** Evict all cached HTML (called on theme switch to force reparse). */
    public static void clearCache() {
        synchronized (MARKDOWN_HTML_CACHE) {
            MARKDOWN_HTML_CACHE.clear();
        }
    }

    /**
     * Single-pass replacement of multiple literal strings.
     * Faster than chaining {@link String#replace} for large HTML because
     * each target is scanned once instead of re-scanning the entire
     * result string after every replacement.
     */
    private static String batchReplace(String source, String... pairs) {
        if (pairs.length == 0 || pairs.length % 2 != 0) {
            return source;
        }
        StringBuilder sb = new StringBuilder(source.length() + 256);
        outer:
        for (int i = 0; i < source.length(); ) {
            for (int p = 0; p < pairs.length; p += 2) {
                String target = pairs[p];
                if (source.startsWith(target, i)) {
                    sb.append(pairs[p + 1]);
                    i += target.length();
                    continue outer;
                }
            }
            sb.append(source.charAt(i));
            i++;
        }
        return sb.toString();
    }
}
