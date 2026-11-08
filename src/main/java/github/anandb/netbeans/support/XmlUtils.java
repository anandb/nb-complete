package github.anandb.netbeans.support;

import java.io.CharConversionException;

import org.openide.xml.XMLUtil;

/** Escape utilities for XML and HTML payloads. */
public final class XmlUtils {

    private XmlUtils() { }

    /** Escape XML element content: {@code <}, {@code &}, and {@code >} in {@code ]]>}. */
    public static String escapeXml(String text) {
        return escape(text);
    }

    /** Escape HTML element content for safe display in Swing components. */
    public static String escapeHtml(String text) {
        return escape(text);
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        try {
            return XMLUtil.toElementContent(text);
        } catch (CharConversionException ex) {
            // A C0 control became an invalid XML character; keep the legacy permissive output.
            return text.replace("&", "&amp;")
                       .replace("<", "&lt;")
                       .replace(">", "&gt;")
                       .replace("\"", "&quot;");
        }
    }
}
