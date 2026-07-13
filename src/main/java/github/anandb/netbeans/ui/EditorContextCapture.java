package github.anandb.netbeans.ui;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;

import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.text.NbDocument;

import github.anandb.netbeans.support.Logger;

public final class EditorContextCapture {

    private static final Logger LOG = Logger.from(EditorContextCapture.class);

    private EditorContextCapture() {}

    /**
     * Captures editor context (file path, selection, cursor position) as
     * supplementary metadata for the AI. This is reference-only information
     * to ground the AI in the user's current editor state — it is NOT the
     * primary prompt content. The actual user message is always the main input.
     * @return Map of editor context
     */
    public static Map<String, Object> capture() {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            return null;
        }

        Document doc = editor.getDocument();
        if (!(doc instanceof StyledDocument styledDoc)) {
            return null;
        }

        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo == null) {
            // No actual file open (e.g. empty editor, temp document) — sending
            // a phantom path like /tmp/xxx would confuse the AI. Skip context.
            return null;
        }

        Map<String, Object> context = new HashMap<>();
        File file = FileUtil.toFile(fo);
        String path = (file != null) ? file.getAbsolutePath() : fo.getPath();
        context.put("filePath", path);

        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        int docLen = doc.getLength();

        // Re-validate bounds to avoid an IllegalArgumentException when offset+length exceeds document length.
        if (selStart >= 0 && selEnd >= 0 && selStart <= selEnd && selEnd <= docLen) {
            String selection = editor.getSelectedText();
            if (isNotBlank(selection)) {
                context.put("selectionContent", selection);
                int startLine = NbDocument.findLineNumber(styledDoc, selStart) + 1;
                int endLine = NbDocument.findLineNumber(styledDoc, selEnd) + 1;
                context.put("selection", startLine + ":" + endLine);
            }
        }

        int caretPos = editor.getCaretPosition();
        int cursorLine = NbDocument.findLineNumber(styledDoc, caretPos) + 1;
        context.put("cursor", String.valueOf(cursorLine));

        return context;
    }
}
