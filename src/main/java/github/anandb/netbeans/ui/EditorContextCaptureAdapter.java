package github.anandb.netbeans.ui;

import java.util.Map;

import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.contract.EditorContextQuery;

/**
 * Adapter that implements {@link EditorContextQuery} by delegating to
 * {@link EditorContextCapture}. This keeps the ui/ → contract/ dependency
 * direction correct while allowing mcp/ to use the port.
 */
@ServiceProvider(service = EditorContextQuery.class)
public final class EditorContextCaptureAdapter implements EditorContextQuery {

    @Override
    public Map<String, Object> capture() {
        return EditorContextCapture.capture();
    }
}
