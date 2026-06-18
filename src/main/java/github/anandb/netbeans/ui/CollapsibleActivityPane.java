package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * A collapsible pane for displaying AI activity (tool calls, thoughts, etc.)
 * using a {@link JTextArea} with word wrapping instead of a complex HTML
 * renderer. Provides reliable text wrapping for streamed tool/thought
 * content in the activity pane.
 * <p>
 * Usage:
 * <ul>
 *   <li>For a single text block: use
 *       {@link #CollapsibleActivityPane(String, String, boolean)} or
 *       {@link #setContent(String)}.</li>
 *   <li>For segmented (combined thought + tool) content: use
 *       {@link #setSegmentedContent(List)}.</li>
 * </ul>
 */
public class CollapsibleActivityPane extends BaseCollapsiblePane {

    private static final long serialVersionUID = 1L;

    public CollapsibleActivityPane(String title, String content, boolean expandedAtStart) {
        super(12, title, 
              ThemeManager.getCurrentTheme().activityAccent(), 
              expandedAtStart);

        combinedPlainText = content != null ? content : "";
        JTextArea textArea = createActivityTextArea(combinedPlainText);
        contentPanel.add(textArea, BorderLayout.CENTER);

        updateAppearance();
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    public CollapsibleActivityPane(String title, boolean expandedAtStart) {
        this(title, "", expandedAtStart);
    }

    @Override
    protected void onToggle(boolean expanded) {
        super.onToggle(expanded);
        if (isSegmented) {
            header.setVisible(!expanded);
        }
    }

    @Override
    public void setContent(String content) {
        combinedPlainText = content != null ? content : "";

        if (contentPanel.getComponentCount() > 0
                && contentPanel.getComponent(0) instanceof JTextArea existing) {
            existing.setText(combinedPlainText);
            return;
        }

        contentPanel.removeAll();
        JTextArea textArea = createActivityTextArea(combinedPlainText);
        contentPanel.add(textArea, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    @Override
    public void appendContent(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        combinedPlainText += text;
        if (contentPanel.getComponentCount() > 0
                && contentPanel.getComponent(0) instanceof JTextArea existing) {
            existing.append(text);
            contentPanel.revalidate();
            contentPanel.repaint();
            return;
        }
        contentPanel.removeAll();
        JTextArea textArea = createActivityTextArea(combinedPlainText);
        contentPanel.add(textArea, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    public void setSegmentedContent(List<CollapsibleToolPane.ToolSegment> blocks) {
        isSegmented = true;
        contentPanel.removeAll();

        StringBuilder plainText = new StringBuilder();
        JPanel multiPanel = new JPanel();
        multiPanel.setLayout(new BoxLayout(multiPanel, BoxLayout.Y_AXIS));
        multiPanel.setOpaque(false);
        multiPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        ColorTheme theme = ThemeManager.getCurrentTheme();

        for (int i = 0; i < blocks.size(); i++) {
            CollapsibleToolPane.ToolSegment block = blocks.get(i);
            if (i > 0) {
                plainText.append("\n\n");
            }
            Component bodyComp = (block.text() != null && !block.text().trim().isEmpty())
                    ? MarkdownStyledRenderer.render(block.text(), theme)
                    : null;
            multiPanel.add(createSegmentPane(block.text(), block.isThought(), block.title(), theme, bodyComp, this::toggle));
            plainText.append(block.text());
        }
        combinedPlainText = plainText.toString();
        contentPanel.add(multiPanel, BorderLayout.CENTER);

        // When expanded and segmented, hide the main header so segment headers
        // act as the toggle surface. Restored on collapse.
        header.setVisible(!(expanded && isSegmented));
    }

    @Override
    protected void updateAppearance() {
        super.updateAppearance();
        ColorTheme theme = ThemeManager.getCurrentTheme();
        if (expanded) {
            Color accentColor = getAccordionGroup() != null ? theme.base1() : defaultAccent;
            header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                BorderFactory.createEmptyBorder(5, 4, 5, 10)));
        } else {
            header.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 10));
        }
    }

    @Override
    protected Color getDefaultHeaderBackground() {
        return expanded ? ThemeManager.getCurrentTheme().base2() : ThemeManager.getCurrentTheme().sunkenBackground();
    }

    @Override
    protected Icon getDefaultIcon() {
        return ThemeManager.getIcon("go.svg", 24);
    }

    @Override
    protected String getContentToCopy() {
        return combinedPlainText;
    }
}
