package github.anandb.netbeans.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

public record AttachedFile(
    String filename,
    String mimeType,
    String base64Data,
    long size
) {
    public AttachedFile(File f) throws IOException {
        this(f.getName(), guessMimeType(f.getName()),
             Base64.getEncoder().encodeToString(Files.readAllBytes(f.toPath())),
             f.length());
    }

    private static String guessMimeType(String name) {
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "webp" -> "image/webp";
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
