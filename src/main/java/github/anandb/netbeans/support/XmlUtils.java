package github.anandb.netbeans.support;

/** Escape utilities for XML and HTML payloads. */
public final class XmlUtils {

    private XmlUtils() { }

    /** Escape XML special characters to prevent injection in metadata payloads. */
    public static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /** Escape HTML special characters for safe display in Swing components. */
    public static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
