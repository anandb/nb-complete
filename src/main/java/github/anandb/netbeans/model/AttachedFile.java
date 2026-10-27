package github.anandb.netbeans.model;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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

    /** Bytes inspected when deciding whether an unknown MIME type is text. */
    private static final int TEXT_SNIFF_BYTES = 8 * 1024;

    /**
     * True when the file is text and may be inlined. A declared text type is
     * trusted; an unknown one ({@code application/octet-stream}, which is also
     * what an extension the table does not know produces) is sniffed, so a
     * {@code .log} or an extension-less Makefile still reaches the agent inline.
     */
    private boolean isTextual() {
        if (isTextualMime(mimeType)) {
            return true;
        }
        return mimeType == null || "application/octet-stream".equals(mimeType)
                ? looksTextual() : false;
    }

    private static boolean isTextualMime(String mime) {
        return mime != null && (mime.startsWith("text/") || "application/json".equals(mime)
                || "application/xml".equals(mime) || "application/javascript".equals(mime)
                || "application/sql".equals(mime) || "application/graphql".equals(mime)
                || mime.endsWith("+json") || mime.endsWith("+xml"));
    }

    /**
     * Content sniff for an unknown type: no NUL byte in the first
     * {@link #TEXT_SNIFF_BYTES} and a clean UTF-8 decode of that prefix. A prefix
     * can end mid-character, so the last few bytes are trimmed before giving up.
     */
    private boolean looksTextual() {
        int limit = Math.min(data.length, TEXT_SNIFF_BYTES);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return false;
            }
        }
        for (int trim = 0; trim <= 3 && limit - trim >= 0; trim++) {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                decoder.decode(ByteBuffer.wrap(data, 0, limit - trim));
                return true;
            } catch (CharacterCodingException e) {
                // Likely a multi-byte character split by the sniff boundary.
            }
        }
        return false;
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
            case "log", "ini", "cfg", "conf", "properties", "env" -> "text/plain";
            case "sh", "bash", "zsh", "fish" -> "text/x-sh";
            case "js", "mjs", "cjs", "jsx" -> "text/javascript";
            case "ts" -> "text/x-typescript";
            case "css" -> "text/css";
            case "scss", "sass", "less" -> "text/x-scss";
            case "sql" -> "text/x-sql";
            case "c", "h" -> "text/x-c";
            case "cpp", "cc", "cxx", "hpp", "hh" -> "text/x-c++";
            case "rs" -> "text/x-rust";
            case "go" -> "text/x-go";
            case "kt", "kts" -> "text/x-kotlin";
            case "rb" -> "text/x-ruby";
            case "php" -> "text/x-php";
            case "cs" -> "text/x-csharp";
            case "swift" -> "text/x-swift";
            case "groovy", "gradle" -> "text/x-groovy";
            case "diff", "patch" -> "text/x-diff";
            default -> "application/octet-stream";
        };
    }
}
