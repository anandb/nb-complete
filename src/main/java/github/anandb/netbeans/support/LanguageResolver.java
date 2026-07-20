package github.anandb.netbeans.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves file extensions to language identifiers.
 * Provides O(1) lookup for common programming languages.
 */
public final class LanguageResolver {

    private static final Map<String, String> EXT_TO_LANGUAGE = new ConcurrentHashMap<>();
    private static final Map<String, String> EXT_TO_MIME = new ConcurrentHashMap<>();

    static {
        // Common language keywords
        EXT_TO_LANGUAGE.put("java", "java");
        EXT_TO_LANGUAGE.put("py", "python");
        EXT_TO_LANGUAGE.put("pl", "perl");
        EXT_TO_LANGUAGE.put("awk", "awk");

        // Alias mappings
        EXT_TO_LANGUAGE.put("js", "javascript");
        EXT_TO_LANGUAGE.put("javascript", "javascript");
        EXT_TO_LANGUAGE.put("ts", "typescript");
        EXT_TO_LANGUAGE.put("typescript", "typescript");
        EXT_TO_LANGUAGE.put("html", "html");
        EXT_TO_LANGUAGE.put("css", "css");
        EXT_TO_LANGUAGE.put("xml", "xml");
        EXT_TO_LANGUAGE.put("md", "markdown");
        EXT_TO_LANGUAGE.put("markdown", "markdown");
        EXT_TO_LANGUAGE.put("json", "json");

        // Shell family aliases
        EXT_TO_LANGUAGE.put("sh", "bash");
        EXT_TO_LANGUAGE.put("bash", "bash");
        EXT_TO_LANGUAGE.put("ksh", "bash");
        EXT_TO_LANGUAGE.put("zsh", "bash");

        // Extension → MIME mappings
        EXT_TO_MIME.put("java", "text/x-java");
        EXT_TO_MIME.put("js", "text/javascript");
        EXT_TO_MIME.put("jsx", "text/javascript");
        EXT_TO_MIME.put("ts", "text/typescript");
        EXT_TO_MIME.put("tsx", "text/typescript");
        EXT_TO_MIME.put("py", "text/x-python");
        EXT_TO_MIME.put("rb", "text/x-ruby");
        EXT_TO_MIME.put("php", "text/x-php");
        EXT_TO_MIME.put("c", "text/x-c");
        EXT_TO_MIME.put("h", "text/x-c");
        EXT_TO_MIME.put("cpp", "text/x-c++");
        EXT_TO_MIME.put("cc", "text/x-c++");
        EXT_TO_MIME.put("cxx", "text/x-c++");
        EXT_TO_MIME.put("hpp", "text/x-c++");
        EXT_TO_MIME.put("cs", "text/x-csharp");
        EXT_TO_MIME.put("go", "text/x-go");
        EXT_TO_MIME.put("rs", "text/x-rust");
        EXT_TO_MIME.put("swift", "text/x-swift");
        EXT_TO_MIME.put("kt", "text/x-kotlin");
        EXT_TO_MIME.put("kts", "text/x-kotlin");
        EXT_TO_MIME.put("scala", "text/x-scala");
        EXT_TO_MIME.put("xml", "text/xml");
        EXT_TO_MIME.put("xsd", "text/xml");
        EXT_TO_MIME.put("xsl", "text/xml");
        EXT_TO_MIME.put("xslt", "text/xml");
        EXT_TO_MIME.put("html", "text/html");
        EXT_TO_MIME.put("htm", "text/html");
        EXT_TO_MIME.put("xhtml", "text/html");
        EXT_TO_MIME.put("css", "text/css");
        EXT_TO_MIME.put("scss", "text/x-scss");
        EXT_TO_MIME.put("sass", "text/x-scss");
        EXT_TO_MIME.put("less", "text/x-less");
        EXT_TO_MIME.put("json", "application/json");
        EXT_TO_MIME.put("yaml", "text/x-yaml");
        EXT_TO_MIME.put("yml", "text/x-yaml");
        EXT_TO_MIME.put("toml", "text/x-toml");
        EXT_TO_MIME.put("sh", "text/x-shellscript");
        EXT_TO_MIME.put("bash", "text/x-shellscript");
        EXT_TO_MIME.put("zsh", "text/x-shellscript");
        EXT_TO_MIME.put("sql", "text/x-sql");
        EXT_TO_MIME.put("md", "text/x-markdown");
        EXT_TO_MIME.put("markdown", "text/x-markdown");
        EXT_TO_MIME.put("properties", "text/x-properties");
        EXT_TO_MIME.put("gradle", "text/x-gradle");
        EXT_TO_MIME.put("groovy", "text/x-groovy");
        EXT_TO_MIME.put("gvy", "text/x-groovy");
        EXT_TO_MIME.put("gy", "text/x-groovy");
        EXT_TO_MIME.put("gsh", "text/x-groovy");
        EXT_TO_MIME.put("lua", "text/x-lua");
        EXT_TO_MIME.put("r", "text/x-r");
        EXT_TO_MIME.put("pl", "text/x-perl");
        EXT_TO_MIME.put("pm", "text/x-perl");
        EXT_TO_MIME.put("vim", "text/x-vim");
        EXT_TO_MIME.put("diff", "text/x-diff");
        EXT_TO_MIME.put("patch", "text/x-diff");
        EXT_TO_MIME.put("ini", "text/x-ini");
        EXT_TO_MIME.put("cfg", "text/x-ini");
        EXT_TO_MIME.put("conf", "text/x-ini");
        EXT_TO_MIME.put("svg", "text/xml");
    }

    private LanguageResolver() {
        // Utility class - prevent instantiation
    }

    /**
     * Resolves a MIME type to a language identifier suitable for markdown
     * code-fence labels.
     *
     * @param mime the MIME type (e.g. "text/x-java", "application/json")
     * @return the language identifier, or "text" if unknown
     */
    public static String fromMime(String mime) {
        if (mime == null) return "text";
        return switch (mime) {
            case "text/x-java", "text/x-java-source" -> "java";
            case "text/javascript" -> "javascript";
            case "text/typescript" -> "typescript";
            case "text/x-python" -> "python";
            case "text/xml", "application/xml" -> "xml";
            case "text/html" -> "html";
            case "text/css" -> "css";
            case "application/json" -> "json";
            case "text/x-yaml" -> "yaml";
            case "text/x-shellscript" -> "bash";
            case "text/x-diff" -> "diff";
            case "image/png", "image/jpeg", "image/gif", "image/svg+xml" -> "";
            default -> "text";
        };
    }

    /**
     * Resolves a file path to its language identifier.
     *
     * @param path the file path
     * @return the language identifier, or empty string if path is null/empty,
     *         or the extension itself if not mapped
     */
    public static String fromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1 || lastDot == path.length() - 1) {
            return "";
        }

        String ext = path.substring(lastDot + 1).toLowerCase();

        // O(1) lookup from static map, fallback to extension itself
        return EXT_TO_LANGUAGE.getOrDefault(ext, ext);
    }

    /**
     * Resolves a file path to its MIME type for syntax highlighting.
     *
     * @param path the file path
     * @return the MIME type, or "text/plain" if unknown
     */
    public static String fromPathToMime(String path) {
        if (path == null || path.isEmpty()) {
            return "text/plain";
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1 || lastDot == path.length() - 1) {
            return "text/plain";
        }
        return EXT_TO_MIME.getOrDefault(path.substring(lastDot + 1).toLowerCase(), "text/plain");
    }

    /**
     * Registers a custom extension to language mapping.
     *
     * @param extension the file extension (without dot)
     * @param language the language identifier
     */
    public static void registerExtension(String extension, String language) {
        if (extension != null && !extension.isEmpty() && language != null) {
            EXT_TO_LANGUAGE.put(extension.toLowerCase(), language);
        }
    }
}
