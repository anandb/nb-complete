package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

class BubbleContentRenderer {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```([\\w\\-\\+\\#\\.]*)\\R?(.*?)(?:```(?=\\R|$)|$)", Pattern.DOTALL
    );

    private final JPanel segments;
    private final StringBuilder text;
    private final String role;
    private final ArrayList<CollapsibleState> codeStates;
    private final BubbleStreamer streamer;

    static class CollapsibleState {
        boolean expanded;

        CollapsibleState(boolean expanded) {
            this.expanded = expanded;
        }
    }

    BubbleContentRenderer(JPanel segments, StringBuilder text, String role,
                          ArrayList<CollapsibleState> codeStates, BubbleStreamer streamer) {
        this.segments = segments;
        this.text = text;
        this.role = role;
        this.codeStates = codeStates;
        this.streamer = streamer;
    }

    private void handleToolThoughtContent(ColorTheme theme, boolean expanded, String toolTitle) {
        String displayContent = text.toString();
        String title = toolTitle != null ? toolTitle : "Execution Steps";
        if (segments.getComponentCount() > 0) {
            Component first = segments.getComponent(0);
            if (first instanceof BaseCollapsiblePane pane) {
                updatePaneContent(pane, title, displayContent, expanded);
            }
        } else {
            segments.removeAll();
            segments.add(new CollapsibleActivityPane(title, displayContent, expanded));
            streamer.setLastDisplayedLength(displayContent.length());
        }
        segments.revalidate();
    }

    private void updatePaneContent(BaseCollapsiblePane pane, String title, String content, boolean expanded) {
        pane.setTitle(title);
        int lastLen = streamer.getLastDisplayedLength();
        if (content.length() > lastLen) {
            pane.appendContent(content.substring(lastLen));
        } else if (content.length() < lastLen) {
            pane.setContent(content);
        }
        streamer.setLastDisplayedLength(content.length());
        pane.setExpanded(expanded);
    }

    void updateContent(ColorTheme theme, boolean expanded, String toolTitle) {
        if ("tool".equals(role) || "thought".equals(role)) {
            handleToolThoughtContent(theme, expanded, toolTitle);
            return;
        }

        if ("user".equals(role)) {
            updateOrAddTextSegment(text.toString(), theme, 0, false);
            while (segments.getComponentCount() > 1) {
                segments.remove(segments.getComponentCount() - 1);
            }
            segments.revalidate();
            return;
        }

        String rawText = text.toString();

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(rawText);

        int currentCompIdx = 0;

        int lastEnd = 0;
        int codeIdx = 0;
        while (matcher.find()) {
            String textBefore = rawText.substring(lastEnd, matcher.start());
            if (!textBefore.isEmpty()) {
                currentCompIdx = addTextAndTableSegments(textBefore, theme, currentCompIdx, expanded);
            }

            String lang = matcher.group(1);
            String code = matcher.group(2);

            boolean defaultExpanded = false;

            if (codeIdx < codeStates.size()) {
                defaultExpanded = codeStates.get(codeIdx).expanded;
            } else {
                codeStates.add(new CollapsibleState(defaultExpanded));
            }

            updateOrAddCodeSegment(lang, code, defaultExpanded, codeIdx, currentCompIdx++);

            lastEnd = matcher.end();
            codeIdx++;
        }

        if (lastEnd < rawText.length()) {
            String remaining = rawText.substring(lastEnd);
            if (!remaining.isEmpty()) {
                currentCompIdx = addTextAndTableSegments(remaining, theme, currentCompIdx, expanded);
            }
        }

        while (segments.getComponentCount() > currentCompIdx) {
            segments.remove(segments.getComponentCount() - 1);
        }

        segments.revalidate();
    }

    void setSegmentedToolContent(List<CollapsibleToolPane.ToolSegment> blocks) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleActivityPane pane) {
                pane.setSegmentedContent(blocks);
                return;
            } else if (c instanceof CollapsibleToolPane pane) {
                pane.setSegmentedContent(blocks);
                return;
            }
        }
    }

    void updateCombinedContent(List<CollapsibleToolPane.ToolSegment> blocks, String title) {
        for (Component c : segments.getComponents()) {
            if (c instanceof CollapsibleActivityPane pane) {
                pane.setTitle(title);
                pane.setSegmentedContent(blocks);
                return;
            } else if (c instanceof CollapsibleToolPane pane) {
                pane.setTitle(title);
                pane.setSegmentedContent(blocks);
                return;
            }
        }
    }

    private void updateOrAddCodeSegment(String lang, String code, boolean expanded, int codeIdx, int compIdx) {
        if (compIdx < segments.getComponentCount()) {
            Component c = segments.getComponent(compIdx);
            if (c instanceof CollapsibleCodePane pane) {
                pane.updateContent(lang, code);
                pane.setVisible(code != null && !code.trim().isEmpty());
                return;
            }
        }

        CollapsibleCodePane codePane = new CollapsibleCodePane(lang, code, expanded);
        codePane.setVisible(code != null && !code.trim().isEmpty());
        if (compIdx < segments.getComponentCount()) {
            segments.remove(compIdx);
            segments.add(codePane, compIdx);
        } else {
            segments.add(codePane);
        }
    }

    private void updateOrAddTextSegment(String markdown, ColorTheme theme, int compIdx, boolean incremental) {
        String styledHtml = HtmlContentPreparer.prepareHtml(markdown, theme, role, incremental);
        java.awt.Color bg = UIUtils.getBubbleBackground(theme, role);

        if (compIdx < segments.getComponentCount()) {
            Component c = segments.getComponent(compIdx);
            if (c instanceof FitEditorPane pane) {
                pane.setOpaque(false);
                pane.setText(styledHtml);
                return;
            }
        }

        FitEditorPane pane = FitEditorPane.createHtmlPane(styledHtml, bg, role, true);
        if (compIdx < segments.getComponentCount()) {
            segments.remove(compIdx);
            segments.add(pane, compIdx);
        } else {
            segments.add(pane);
        }
    }

    private int addTextAndTableSegments(String text, ColorTheme theme, int compIdx, boolean incremental) {
        TableDetector.TableResult result = TableDetector.detectTables(text, incremental);
        int currentIdx = compIdx;

        for (TableDetector.Segment seg : result.segments()) {
            if (seg instanceof TableDetector.TextSegment ts) {
                updateOrAddTextSegment(ts.text(), theme, currentIdx++, false);
            } else if (seg instanceof TableDetector.TableSegment tbl) {
                updateOrAddTableSegment(tbl.markdown(), theme, currentIdx++);
            }
        }

        return currentIdx;
    }

    private void updateOrAddTableSegment(String tableMarkdown, ColorTheme theme, int compIdx) {
        String styledHtml = HtmlContentPreparer.prepareHtml(tableMarkdown, theme, role, false);

        if (compIdx < segments.getComponentCount()) {
            Component c = segments.getComponent(compIdx);
            if (c instanceof RoundedPanel rp && rp.getComponentCount() > 0 && rp.getComponent(0) instanceof FitEditorPane pane) {
                rp.setBaseColor(theme.tableBackground());
                rp.setBorderColor(theme.tableBorder());
                pane.setText(styledHtml);
                return;
            }
        }

        RoundedPanel rp = new RoundedPanel(12);
        rp.setBaseColor(theme.tableBackground());
        rp.setBorderColor(theme.tableBorder());
        rp.setLayout(new BorderLayout());
        rp.setBorder(new EmptyBorder(1, 1, 1, 1));

        FitEditorPane pane = FitEditorPane.createHtmlPane(styledHtml, theme.tableBackground(), role, false);
        pane.setOpaque(false);
        pane.setBorder(new EmptyBorder(8, 8, 8, 8));
        rp.add(pane, BorderLayout.CENTER);

        if (compIdx < segments.getComponentCount()) {
            segments.remove(compIdx);
            segments.add(rp, compIdx);
        } else {
            segments.add(rp);
        }
    }
}
