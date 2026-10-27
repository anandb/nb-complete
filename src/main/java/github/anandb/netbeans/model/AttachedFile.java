package github.anandb.netbeans.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

/**
 * One file attached to an outgoing prompt: the display name, the MIME type,
 * the raw bytes and the source URI. Bytes (not a base64 string) are kept so the
 * encoder only runs when a block actually needs base64, and so textual files can
 * be inlined without decoding.
 */
public record AttachedFile(
    String filename,
    String mimeType,
    byte[] data,
    long size,
    String uri
) {
    public AttachedFile(File f) throws IOException {
        this(f.getName(), guessMimeType(f.getName()), Files.readAllBytes(f.toPath()), f.length(),
             f.toURI().toString());
    }

    /** True when the agent should receive this as an ACP image block. */
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /** Base64 of the raw bytes, encoded on demand. */
    public String base64Data() {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * The file decoded as text (UTF-8, leading BOM dropped), or {@code null}
     * when the MIME type is not textual. Used to inline files for agents that
     * do not support embedded resources and to build embedded text resources.
     */
    public String textContent() {
        if (!isTextual()) {
            return null;
        }
        int offset = data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF ? 3 : 0;
        return new String(data, offset, data.length - offset, StandardCharsets.UTF_8);
    }

    private boolean isTextual() {
        return mimeType != null && (mimeType.startsWith("text/") || "application/json".equals(mimeType)
                || "application/xml".equals(mimeType));
    }

    private static String guessMimeType(String name) {
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "webp" -> "image/webp";
            case "csv" -> "text/csv";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "json" -> "application/json";
            case "py" -> "text/x-python";
            case "java" -> "text/x-java";
            case "md" -> "text/markdown";
            case "xml", "html", "htm" -> "text/html";
            case "yaml", "yml" -> "text/yaml";
            case "toml" -> "text/toml";
            default -> "application/octet-stream";
        };
    }
}
