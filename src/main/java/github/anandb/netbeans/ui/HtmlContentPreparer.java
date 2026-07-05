package github.anandb.netbeans.ui;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TextScanner;
import static github.anandb.netbeans.ui.UIUtils.MONO_STACK;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class HtmlContentPreparer {

    private HtmlContentPreparer() {}

    private static final Logger LOG = Logger.from(HtmlContentPreparer.class);

    private static final Parser FLEXMARK_PARSER;
    private static final HtmlRenderer FLEXMARK_RENDERER;

    /** Bounded LRU cache for markdown→HTML output. Caffeine handles concurrency,
     *  size eviction, and access-order tracking internally. */
    private static final Cache<String, String> MARKDOWN_HTML_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(256)
                    .expireAfterAccess(60, TimeUnit.MINUTES)
                    .build();

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        FLEXMARK_PARSER = Parser.builder(options).build();
        FLEXMARK_RENDERER = HtmlRenderer.builder(options).build();
    }

    /** Cache for the static HTML wrapper (head+style). Keyed on (role, fontSize, theme-identity)
     *  since CSS depends on these. Caffeine handles concurrency and LRU eviction.
     *  Invalidated when the L&amp;F changes (theme switch). */
    private static final Cache<String, String> HTML_WRAPPER_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(32)
                    .build();

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

        // Alternating row highlighting — Swing's HTMLEditorKit doesn't support
        // CSS nth-child, so we inject inline styles on even <tr> elements.
        String alternateBg = theme.toHtmlHex(theme.tableRowAlternate());
        html = highlightAlternateRows(html, alternateBg);

        boolean hasArt = TextScanner.containsAsciiArt(markdown);
        if (!hasArt && !"user".equals(role)) {
            html = html.replace("  ", " &nbsp;");
        } else if (hasArt) {
            LOG.fine("Contains ASCII art, not replacing spaces");
        }

        boolean isAssistant = !"user".equals(role) && !"error".equals(role) && !"tool".equals(role);

        // User messages are typically plain text; preserve explicit line breaks
        // by converting newlines to <br/> outside of <pre> blocks.
        if ("user".equals(role)) {
            html = newlinesToBrOutsidePre(html);
        }

        String wrapper = getCachedWrapper(theme, role, isAssistant);
        String headOpen = wrapper.substring(0, wrapper.indexOf("__BODY__"));
        String headCloseAndBodyOpen = wrapper.substring(wrapper.indexOf("__BODY__") + "__BODY__".length());

        if (hasArt) {
            // Inject .ascii-art rule inside the <style> block (headOpen ends at the
            // <body> opener, so we splice the rule just before </style>).
            String asciiRule = ".ascii-art { font-family: " + MONO_STACK + "; line-height: 1.0; }";
            String asciiCss = headOpen.replace("</style>", asciiRule + "</style>");
            html = html.replace("  ", " &nbsp;");
            html = html.replace("\n", "<br/>");
            html = "<div class='ascii-art'>" + html + "</div>";
            return asciiCss + headCloseAndBodyOpen + html + "</body></html>";
        }

        return headOpen + headCloseAndBodyOpen + html + "</body></html>";
    }

    /**
     * Returns the cached HTML document head (including &lt;style&gt;) for the given
     * (theme, role) combination. The cache sentinel "__BODY__" marks where the
     * variable body content is inserted. Includes the &lt;body style='...'&gt; opener
     * so the body is properly opened after the sentinel.
     */
    private static String getCachedWrapper(ColorTheme theme, String role, boolean isAssistant) {
        int fontSize = ThemeManager.getFont().getSize() - 2;
        String cacheKey = role + "|" + fontSize + "|" + System.identityHashCode(theme);
        return HTML_WRAPPER_CACHE.get(cacheKey, key -> {
            String customCss = theme.toCss(null, isAssistant, fontSize);
            if ("error".equals(role)) {
                customCss += " body { color: #D32F2F; font-weight: bold; }";
            } else if ("user".equals(role)) {
                customCss += " body { font-weight: 300; }";
            }
            String bodyStyle = "margin: 0; padding: 0; text-align: left !important; width: 100%;";
            return "<html><head><style>" + customCss + "</style></head><body style='" + bodyStyle + "'>__BODY__";
        });
    }

    /** Evict all cached HTML (called on theme switch to force reparse). */
    public static void clearCache() {
        MARKDOWN_HTML_CACHE.invalidateAll();
        HTML_WRAPPER_CACHE.invalidateAll();
    }

    public static boolean containsAsciiArt(String markdown) {
        return TextScanner.containsAsciiArt(markdown);
    }

    /**
     * Returns cached HTML for markdown input, or parses + caches on miss.
     * The cache is bounded to 256 entries.  Text longer than 32 KB is not
     * cached (avoid holding large strings for rare long messages).
     */
    private static String computeOrGetCachedHtml(String markdown) {
        if (markdown.length() > 32768) {
            return FLEXMARK_RENDERER.render(FLEXMARK_PARSER.parse(markdown));
        }
        // Caffeine's get(key, loader) is atomic — computes on miss,
        // no external synchronization needed.
        return MARKDOWN_HTML_CACHE.get(markdown,
                md -> FLEXMARK_RENDERER.render(FLEXMARK_PARSER.parse(md)));
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

    /**
     * Adds inline {@code background-color} to every even (2nd, 4th, …)
     * {@code <tr>} element for alternating row highlighting.
     * Swing's {@code HTMLEditorKit} doesn't support CSS {@code nth-child},
     * so this must be done via HTML post-processing.
     */
    private static String highlightAlternateRows(String html, String alternateBg) {
        StringBuilder sb = new StringBuilder(html.length() + 128);
        int trCount = 0;
        int i = 0;
        while (i < html.length()) {
            int trStart = html.indexOf("<tr", i);
            if (trStart < 0) {
                sb.append(html.substring(i));
                break;
            }
            sb.append(html, i, trStart);
            trCount++;
            int tagEnd = html.indexOf('>', trStart);
            if (tagEnd < 0) {
                sb.append(html.substring(trStart));
                break;
            }
            sb.append(html, trStart, tagEnd + 1);
            if (trCount % 2 == 0) {
                // Insert style attribute before the closing '>'
                sb.insert(sb.length() - 1,
                        " style='background-color: " + alternateBg + "'");
            }
            i = tagEnd + 1;
        }
        return sb.toString();
    }

    /**
     * Converts plain '\n' characters to '&lt;br/&gt;' only outside of &lt;pre&gt; blocks.
     * Used for user messages so typed line breaks survive HTML whitespace collapsing.
     */
    static String newlinesToBrOutsidePre(String html) {
        StringBuilder out = new StringBuilder(html.length() + 64);
        int i = 0;
        while (i < html.length()) {
            int preStart = html.indexOf("<pre", i);
            if (preStart < 0) {
                out.append(html.substring(i).replace("\n", "<br/>"));
                break;
            }
            // Replace newlines in the plain-text segment before <pre>.
            out.append(html.substring(i, preStart).replace("\n", "<br/>"));
            int preEnd = html.indexOf("</pre>", preStart);
            if (preEnd < 0) {
                // Malformed HTML: append the rest unchanged.
                out.append(html.substring(preStart));
                break;
            }
            preEnd += "</pre>".length();
            out.append(html, preStart, preEnd);
            i = preEnd;
        }
        return out.toString();
    }
}
