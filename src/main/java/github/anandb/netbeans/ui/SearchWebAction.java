package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.HtmlBrowser;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import javax.swing.JMenuItem;
import javax.swing.AbstractAction;
import static org.apache.commons.lang3.StringUtils.isBlank;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;

/**
 * Searches the web for the selected text in the editor.
 * Detects the NetBeans-configured browser from the {@code WWWBrowser}
 * preference. If it is Firefox, runs {@code firefox --search "<query>"}.
 * Otherwise opens a Google search URL via {@link HtmlBrowser.URLDisplayer}.
 */
@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SearchWebAction")
@ActionRegistration(displayName = "#CTL_SearchWebAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 250)
public final class SearchWebAction extends AbstractAction implements Presenter.Popup {

    private static final Logger LOG = Logger.from(SearchWebAction.class);

    @Override
    public JMenuItem getPopupPresenter() {
        if (!PluginSettings.isSortLinesEnabled()) {
            JMenuItem item = new JMenuItem();
            item.setVisible(false);
            return item;
        }
        JMenuItem item = new JMenuItem(NbBundle.getMessage(SearchWebAction.class, "CTL_SearchWebAction"));
        item.addActionListener(this);
        return item;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!PluginSettings.isSortLinesEnabled()) return;
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) return;

        String query = editor.getSelectedText();
        if (isBlank(query)) {
            query = getWordAtCursor(editor);
        }
        if (isBlank(query)) return;
        query = query.trim();
        String urlStr = "https://www.google.com/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);

        try {
            String browserId = NbPreferences.root()
                    .node("org/netbeans/core").get("WWWBrowser", null);
            if (browserId != null && !browserId.isEmpty()) {
                Lookup.Item<HtmlBrowser.Factory> item = Lookup.getDefault().lookupItem(
                        new Lookup.Template<>(HtmlBrowser.Factory.class, browserId, null));
                if (item != null) {
                    HtmlBrowser.Factory factory = item.getInstance();
                    if (factory != null) {
                        if (factory.getClass().getName().contains("Firefox")) {
                            launchFirefox(factory, query, urlStr);
                            return;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOG.fine("Browser detection failed: {0}", ExceptionUtils.getMessage(ex));
        }

        showUrl(urlStr);
    }

    private static void launchFirefox(HtmlBrowser.Factory factory, String query, String fallbackUrl) {
        String exec = extractExecutable(factory);
        try {
            new ProcessBuilder(exec, "--search", query).start();
        } catch (IOException ex) {
            LOG.warn("Failed to launch Firefox ({0}): {1}", exec, ExceptionUtils.getMessage(ex));
            showUrl(fallbackUrl);
        }
    }

    /**
     * Extracts the word (identifier/token) under the editor's cursor.
     * Searches backward/forward from the caret position for word
     * boundaries (non-identifier characters). Returns null when the
     * caret is on whitespace or the document has no word to grab.
     */
    private static String getWordAtCursor(JTextComponent editor) {
        Document doc = editor.getDocument();
        if (doc == null) return null;
        int pos = editor.getCaretPosition();
        if (pos < 0 || pos > doc.getLength()) return null;
        try {
            int len = doc.getLength();
            // If caret is on whitespace/non-identifier, try the char before
            if (pos < len) {
                char c = doc.getText(pos, 1).charAt(0);
                if (Character.isWhitespace(c) || !Character.isJavaIdentifierPart(c)) {
                    // Only fallback to char-before if not at start
                    if (pos == 0) return null;
                    c = doc.getText(pos - 1, 1).charAt(0);
                    if (Character.isWhitespace(c) || !Character.isJavaIdentifierPart(c)) {
                        return null;
                    }
                    pos--;
                }
            }
            int start = pos;
            while (start > 0) {
                char c = doc.getText(start - 1, 1).charAt(0);
                if (Character.isWhitespace(c) || !Character.isJavaIdentifierPart(c)) break;
                start--;
            }
            int end = pos;
            while (end < len) {
                char c = doc.getText(end, 1).charAt(0);
                if (Character.isWhitespace(c) || !Character.isJavaIdentifierPart(c)) break;
                end++;
            }
            if (start < end) {
                return doc.getText(start, end - start);
            }
        } catch (BadLocationException ex) {
            LOG.fine("Cannot extract word at cursor: {0}", ExceptionUtils.getMessage(ex));
        }
        return null;
    }

    private static String extractExecutable(HtmlBrowser.Factory factory) {
        try {
            Class<?> extClass = Class.forName("org.netbeans.modules.extbrowser.ExtWebBrowser");
            if (extClass.isInstance(factory)) {
                Object desc = extClass.getMethod("getBrowserExecutable").invoke(factory);
                String proc = (String) desc.getClass()
                        .getMethod("getProcessName").invoke(desc);
                if (proc != null && !proc.isEmpty()) return proc;
            }
        } catch (Exception ex) {
            LOG.fine("Cannot extract executable: {0}", ExceptionUtils.getMessage(ex));
        }
        return "firefox";
    }

    private static void showUrl(String urlStr) {
        try {
            HtmlBrowser.URLDisplayer.getDefault().showURL(new URL(urlStr));
        } catch (MalformedURLException ex) {
            LOG.warn("Malformed URL: {0}", urlStr);
        }
    }
}
