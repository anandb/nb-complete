package ai.opencode.netbeans.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;

public class OpenCodeCompletionItem implements CompletionItem {

    private final String text;
    private static final ImageIcon icon = null; // Placeholder for AI icon

    public OpenCodeCompletionItem(String text) {
        this.text = text;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        // Replace or insert text
        component.replaceSelection(text);
        Completion.get().hideAll();
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
    }

    @Override
    public int getPreferredWidth(Graphics g, Font f) {
        return CompletionUtilities.getPreferredWidth(text, null, g, f);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height,
            boolean selected) {
        CompletionUtilities.renderHtml(icon, "<b>OpenCode</b>: " + text, null, g, defaultFont, defaultColor, width,
                height, selected);
    }

    @Override
    public CompletionTask createDocumentationTask() {
        return null;
    }

    @Override
    public CompletionTask createToolTipTask() {
        return null;
    }

    @Override
    public boolean instantSubstitution(JTextComponent component) {
        return false;
    }

    @Override
    public int getSortPriority() {
        return 0; // High priority for AI suggestions
    }

    @Override
    public CharSequence getSortText() {
        return text;
    }

    @Override
    public CharSequence getInsertPrefix() {
        return text;
    }
}
