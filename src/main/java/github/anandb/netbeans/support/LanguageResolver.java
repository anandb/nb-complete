package github.anandb.netbeans.support;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves file extensions to language identifiers.
 * Provides O(1) lookup for common programming languages.
 */
public final class LanguageResolver {

    private static final Map<String, String> EXT_TO_LANGUAGE = new HashMap<>();

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
    }

    private LanguageResolver() {
        // Utility class - prevent instantiation
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
