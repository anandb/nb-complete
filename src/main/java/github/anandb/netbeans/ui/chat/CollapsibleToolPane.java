package github.anandb.netbeans.ui.chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JTextArea;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.ThemeManager;

// DSL-LEAF: keep imperative, wrap via UI.of(...) — extends BaseCollapsiblePane;
// tool-call sub-pane (input params + status row). Migration target: ToolPaneSpec.
public class CollapsibleToolPane extends BaseCollapsiblePane {
    private static final long serialVersionUID = 1L;
    private JTextArea textArea;

    public CollapsibleToolPane(String title, String content, boolean expandedAtStart) {
        super(12, title, ThemeManager.getCurrentTheme().yellow(), expandedAtStart);

        textArea = createActivityTextArea(content);
        textArea.setFont(isThinking
                ? ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() - 1f)
                : ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN, ThemeManager.getMonospaceFont().getSize() - 1f));
        contentPanel.add(textArea, BorderLayout.CENTER);

        updateAppearance();
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /**
     * Creates a tool pane without a text area. Use this for the combined
     * activity bubble where content will be supplied later via
     * {@link #setSegmentedContent(List)}.
     */
    public CollapsibleToolPane(String title, boolean expandedAtStart) {
        super(12, title, ThemeManager.getCurrentTheme().yellow(), expandedAtStart);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    @Override
    protected final void updateAppearance() {
        super.updateBaseAppearance();
        ColorTheme theme = ThemeManager.getCurrentTheme();
        if (textArea != null) {
            textArea.setForeground(expanded ? theme.thinkingHeaderForeground() : theme.foreground());
        }
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));
    }

    @Override
    protected Color getHeaderForeground(ColorTheme theme) {
        return expanded ? theme.foreground() : theme.secondary2();
    }

    @Override
    protected Color getDefaultHeaderBackground() {
        return expanded
                ? ThemeManager.getCurrentTheme().base2()
                : ThemeManager.getCurrentTheme().thinkingHeaderBackground();
    }

    @Override
    protected Icon getDefaultIcon() {
        return ThemeManager.getIcon("tool.svg", 24);
    }

    @Override
    protected String getContentToCopy() {
        return combinedPlainText.isEmpty()
                ? (textArea != null ? textArea.getText() : "")
                : combinedPlainText;
    }

    @Override
    public void setContent(String content) {
        if (textArea == null) {
            return;
        }
        if (content == null) {
            textArea.setText("");
            return;
        }
        if (content.equals(textArea.getText())) {
            return;
        }
        textArea.setText(content);
    }

    @Override
    public void appendContent(String text) {
        if (text == null || text.isEmpty() || textArea == null) {
            return;
        }
        textArea.append(text);
    }

    /** A segment of tool/thought content with an optional title and background indicator. */
    public record ToolSegment(String text, boolean isThought, String title) {}

    /**
     * Replaces the content area with a panel of colored segments, one per
     * consecutive same-type block. Each segment renders its text as markdown
     * via {@link JTextArea}.
     */
    public void setSegmentedContent(List<ToolSegment> blocks) {
        // The caller revalidates (e.g. messagesContainer); the pane may not be
        // in a validated container yet.
        combinedPlainText = buildSegmentedContent(blocks,
                block -> createActivityTextArea(block.text()), false, false);
    }
}
