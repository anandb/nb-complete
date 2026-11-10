package github.anandb.netbeans.ui.rendering;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.openide.util.NbBundle;

import github.anandb.netbeans.ui.UIBundle;

/** Loads a package-relative CSS template from the classpath. */
public final class CssTemplates {

    private CssTemplates() {
    }

    /**
     * Reads {@code resource} relative to {@code anchor}'s package as UTF-8.
     * A missing resource or an I/O failure becomes an {@link IllegalStateException}
     * with the supplied localized bundle keys.
     */
    public static String load(Class<?> anchor, String resource,
            String notFoundKey, String loadFailedKey) {
        try (InputStream in = anchor.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(NbBundle.getMessage(UIBundle.class, notFoundKey));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(NbBundle.getMessage(UIBundle.class, loadFailedKey), e);
        }
    }
}
