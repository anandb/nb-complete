package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

// DSL-LEAF: not a controller — renders bubble content (segment → component).
// Migration target: BubbleContentSpec; the per-MessageType render dispatch
// stays imperative until the DSL can express it declaratively.
class BubbleContentRenderer {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```([\\w\\-\\+\\#\\.]*)\\R?(.*?)(?:```(?=\\R|$)|$)", Pattern.DOTALL
    );

    private final JPanel segments;
    private final StringBuilder text;
    private final String role;
    private final ArrayList<CollapsibleState> codeStates;
    private final BubbleStreamer streamer;
    /** Last text length we rendered. Used to short-circuit re-renders when text
     *  is unchanged (e.g. toggleAllBlocks calls updateContent without text delta). */
    private int lastRenderedTextLength = -1;
    private int lastRenderedTextHash = 0;
    /** Segments set via {@link #setSegmentedToolContent} before the initial
     *  render (which is deferred to addNotify() to avoid redundant work).
     *  Held here so the activity pane can be created with the segmented
     *  content on the first render. */
    private List<CollapsibleToolPane.ToolSegment> pendingSegmentedContent;

    public record CollapsibleState(boolean expanded) {}

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
            // If setSegmentedToolContent was called before the initial render
            // (e.g. ToolThoughtCombiner constructs the bubble, calls
            // setSegmentedToolContent, then adds it to the tree — only at
            // addNotify() does the initial render run), the segments panel is
            // empty and the segmented content was captured in the pending
            // field. Create the activity pane with segmented content; if no
            // segments are pending, fall back to the raw text.
            CollapsibleActivityPane pane;
            if (pendingSegmentedContent != null) {
                pane = new CollapsibleActivityPane(title, expanded);
                pane.setSegmentedContent(pendingSegmentedContent);
                pendingSegmentedContent = null;
                // The pane now owns its own content; track length 0 since
                // the text StringBuilder is empty for the combined bubble.
                streamer.setLastDisplayedLength(0);
            } else {
                pane = new CollapsibleActivityPane(title, displayContent, expanded);
                streamer.setLastDisplayedLength(displayContent.length());
            }
            segments.add(pane);
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
            int userLen = text.length();
            int userHash = text.hashCode();
            if (userLen == lastRenderedTextLength && userHash == lastRenderedTextHash) {
                return; // no change
            }
            lastRenderedTextLength = userLen;
            lastRenderedTextHash = userHash;
            String userText = text.toString();
            updateOrAddTextSegment(userText, theme, 0, false);
            while (segments.getComponentCount() > 1) {
                segments.remove(segments.getComponentCount() - 1);
            }
            segments.revalidate();
            return;
        }

        // Use StringBuilder directly to avoid one full-text copy. Matcher works on
        // any CharSequence, and the helper methods we delegate to only need the
        // substring when actually needed.
        int currentLen = text.length();
        int currentHash = text.hashCode();
        if (currentLen == lastRenderedTextLength && currentHash == lastRenderedTextHash) {
            return; // no change
        }
        lastRenderedTextLength = currentLen;
        lastRenderedTextHash = currentHash;

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);

        int currentCompIdx = 0;

        int lastEnd = 0;
        int codeIdx = 0;
        while (matcher.find()) {
            int beforeStart = matcher.start();
            if (beforeStart > lastEnd) {
                String textBefore = text.substring(lastEnd, beforeStart);
                currentCompIdx = addTextAndTableSegments(textBefore, theme, currentCompIdx, false);
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

        if (lastEnd < currentLen) {
            String remaining = text.substring(lastEnd);
            if (!remaining.isEmpty()) {
                currentCompIdx = addTextAndTableSegments(remaining, theme, currentCompIdx, false);
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
        // No activity pane yet (initial render is deferred to addNotify()).
        // Stash the segments so they can be applied when the pane is created.
        pendingSegmentedContent = blocks;
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
        // No pane yet — stash both.
        pendingSegmentedContent = blocks;
        // Title is taken from the constructor's toolTitle arg; if needed,
        // callers should set it explicitly via setTitle after the pane is created.
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
