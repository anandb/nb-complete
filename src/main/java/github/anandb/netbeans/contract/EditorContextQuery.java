package github.anandb.netbeans.contract;

import java.util.Map;

/**
 * Port for capturing editor context (file path, selection, cursor position).
 * Used by MCP tools to provide context to the AI without depending on ui/ layer.
 */
public interface EditorContextQuery {

    /**
     * Captures editor context as supplementary metadata for the AI.
     * @return Map of editor context, or null if no editor is active
     */
    Map<String, Object> capture();
}
