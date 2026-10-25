package github.anandb.netbeans.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openide.util.NbBundle;

/**
 * Loads the model-facing tool descriptions from {@code descriptions/<tool>.md}.
 * The resource is returned verbatim: the text blocks it replaced ended with a newline
 * (except {@code apply_patch}), and that byte-level detail is part of the tool payload.
 */
final class ToolDescriptions {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private ToolDescriptions() {}

    static String get(String toolName) {
        return CACHE.computeIfAbsent(toolName, ToolDescriptions::load);
    }

    private static String load(String toolName) {
        String path = "descriptions/" + toolName + ".md";
        try (InputStream in = ToolDescriptions.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException(
                        NbBundle.getMessage(ToolDescriptions.class, "ERR_DescriptionNotFound", path));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    NbBundle.getMessage(ToolDescriptions.class, "ERR_DescriptionLoadFailed", path), e);
        }
    }
}
