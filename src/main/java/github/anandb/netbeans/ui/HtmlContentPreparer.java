package github.anandb.netbeans.ui;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TextScanner;

import java.util.List;

public class HtmlContentPreparer {

    private static final Logger LOG = new Logger(HtmlContentPreparer.class);

    private static final Parser FLEXMARK_PARSER;
    private static final HtmlRenderer FLEXMARK_RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        FLEXMARK_PARSER = Parser.builder(options).build();
        FLEXMARK_RENDERER = HtmlRenderer.builder(options).build();
    }

    public static String prepareHtml(String markdown, ColorTheme theme, String role, boolean incremental) {
        String html = FLEXMARK_RENDERER.render(FLEXMARK_PARSER.parse(markdown));

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

        html = html.replace("</th>", "</b></th>")
                   .replace("<table>", tableTag)
                   .replace("<th>", thTag)
                   .replace("<td>", tdTag)
                   .replace("<p>", pTag)
                   .replace("<div>", divTag);

        boolean hasArt = TextScanner.containsAsciiArt(markdown);
        if (!hasArt && !"user".equals(role)) {
            html = html.replace("  ", " &nbsp;");
        } else if (hasArt) {
            LOG.fine("Contains ASCII art, not replacing spaces");
        }

        boolean isAssistant = !"user".equals(role) && !"error".equals(role) && !"tool".equals(role);
        String customCss = theme.toCss(null, isAssistant);
        if ("error".equals(role)) {
            customCss += " body { color: #D32F2F; font-weight: bold; }";
        } else if ("user".equals(role)) {
            customCss += " body { font-weight: 300; }";
        }

        String bodyStyle = "margin: 0; padding: 0; text-align: left !important; width: 100%;";
        if (hasArt) {
            String monoStack = FontStacks.MONO_STACK;
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
}
