package ai.opencode.netbeans.completion;

import ai.opencode.netbeans.manager.OpenCodeManager;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;

@MimeRegistration(mimeType = "text/x-java", service = CompletionProvider.class)
public class OpenCodeCompletionProvider implements CompletionProvider {
    private static final Logger LOG = Logger.getLogger(OpenCodeCompletionProvider.class.getName());

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != COMPLETION_QUERY_TYPE) {
            return null;
        }

        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override
            protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
                try {
                    OpenCodeManager manager = OpenCodeManager.getInstance();

                    if (!manager.isInitialized()) {
                        LOG.log(Level.FINE, "OpenCodeManager not initialized yet");
                        resultSet.finish();
                        return;
                    }

                    String text = doc.getText(0, doc.getLength());

                    int prefixStart = Math.max(0, caretOffset - 2048);
                    if (prefixStart > 0) {
                        int lastNewline = text.lastIndexOf('\n', prefixStart - 1);
                        if (lastNewline >= 0) {
                            prefixStart = lastNewline + 1;
                        }
                    }
                    String prefix = text.substring(prefixStart, caretOffset);

                    int suffixEnd = Math.min(text.length(), caretOffset + 2048);
                    int nextNewline = text.indexOf('\n', caretOffset);
                    if (nextNewline > 0 && nextNewline < suffixEnd) {
                        suffixEnd = nextNewline;
                    }
                    String suffix = text.substring(caretOffset, suffixEnd);

                    String focusedText = prefix + suffix;
                    int adjustedColumn = prefix.length() + 1;

                    LOG.log(Level.FINE, "Requesting completions: prefixLen={0}, suffixLen={1}, adjustedColumn={2}", 
                            new Object[]{prefix.length(), suffix.length(), adjustedColumn});

                    JsonNode result = manager.getCompletionsInline(focusedText, 1, adjustedColumn, prefix, suffix).get();

                    if (result != null && result.has("suggestions")) {
                        for (JsonNode sug : result.get("suggestions")) {
                            String insertText = sug.has("insertText") ? sug.get("insertText").asText() : sug.get("text").asText();
                            resultSet.addItem(new OpenCodeCompletionItem(insertText));
                        }
                        LOG.log(Level.FINE, "Added {0} completion items", result.get("suggestions").size());
                    } else {
                        LOG.log(Level.FINE, "No suggestions returned");
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Completion query failed", ex);
                } finally {
                    resultSet.finish();
                }
            }
        }, component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return COMPLETION_QUERY_TYPE;
    }
}