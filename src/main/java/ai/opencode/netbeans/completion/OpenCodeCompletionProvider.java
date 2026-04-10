package ai.opencode.netbeans.completion;

import ai.opencode.netbeans.manager.OpenCodeManager;
import com.fasterxml.jackson.databind.JsonNode;
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

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != CompletionProvider.COMPLETION_QUERY_TYPE) {
            return null;
        }

        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override
            protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
                try {
                    String text = doc.getText(0, doc.getLength());
                    
                    // We need a session ID. In this simple implementation, we assume 
                    // OpenCodeManager already has an active session or we create one.
                    // For now, we'll use a placeholder or the first active session.
                    
                    // Note: In a real plugin, we'd associate sessions with projects.
                    OpenCodeManager manager = OpenCodeManager.getInstance();
                    
                    // This is synchronous in the query thread (which is fine for AsyncCompletionTask)
                    // but we should ideally use join() on the future.
                    JsonNode result = manager.getCompletions("default", text, 0, caretOffset).get();
                    
                    if (result != null && result.has("suggestions")) {
                        for (JsonNode sug : result.get("suggestions")) {
                            resultSet.addItem(new OpenCodeCompletionItem(sug.get("text").asText()));
                        }
                    }
                } catch (Exception ex) {
                    // Log or handle error
                } finally {
                    resultSet.finish();
                }
            }
        }, component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0; // Don't auto-query for now to avoid lag, or return COMPLETION_QUERY_TYPE
    }
}
