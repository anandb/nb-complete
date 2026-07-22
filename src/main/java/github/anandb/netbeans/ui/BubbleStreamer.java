package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.BiConsumer;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.openide.util.NbBundle;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TimingConstants;

// DSL-CONTROLLER: not a view — deferredFinalizeTimer + streaming-text accumulation state. Stays imperative; the bubble shell it drives is a DSL-LEAF.
class BubbleStreamer {

    private static final Logger LOG = Logger.from(BubbleStreamer.class);

    interface ContentUpdater {
        void update(ColorTheme theme, boolean expanded);
    }

    /** Single source of truth for the streaming lifecycle. Replaces the old
     *  pair of boolean flags that could drift out of sync with the component tree. */
    private enum StreamingState {
        /** Bubble is finalized and shows rendered HTML. */
        FINALIZED,
        /** Bubble has a streaming JTextArea and accepts new text. */
        STREAMING,
        /** Bubble is waiting for the deferred finalization cooldown. */
        DEFERRED_FINALIZING
    }

    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final ContentUpdater contentUpdater;
    private final BiConsumer<Boolean, Boolean> postFinalizeCallback;
    private final JPanel segments;
    private final StringBuilder text;

    private JTextArea streamingTextArea;
    private int lastDisplayedLength = 0;
    private volatile boolean hasPendingTextUpdate = false;
    private StreamingState state = StreamingState.FINALIZED;
    private Timer deferredFinalizeTimer;
    private boolean savedCollapseState;

    BubbleStreamer(ContentUpdater contentUpdater, BiConsumer<Boolean, Boolean> postFinalizeCallback, JPanel segments, StringBuilder text) {
        this.contentUpdater = contentUpdater;
        this.postFinalizeCallback = postFinalizeCallback;
        this.segments = segments;
        this.text = text;
    }

    JTextArea createStreamingTextArea(ColorTheme theme, String initialText) {
        JTextArea ta = new JTextArea(initialText) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Short.MAX_VALUE, pref.height);
            }

            @Override
            public float getAlignmentX() {
                return Component.LEFT_ALIGNMENT;
            }

            @Override
            public JPopupMenu getComponentPopupMenu() {
                JPopupMenu menu = new JPopupMenu();
                JMenuItem copyItem = new JMenuItem(NbBundle.getMessage(MessageBubble.class, "LBL_Copy"));
                copyItem.setEnabled(getSelectedText() != null);
                copyItem.addActionListener(e -> {
                    String t = getSelectedText();
                    if (t != null && !t.isEmpty()) {
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(t), null);
                    }
                });
                menu.add(copyItem);
                return menu;
            }
        };
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setOpaque(false);
        ta.setBackground(TRANSPARENT);
        ta.setForeground(theme.assistantForeground());
        ta.setFont(ThemeManager.getFont());
        ta.setBorder(new EmptyBorder(4, 20, 8, 6));
        ta.setCaretPosition(ta.getDocument().getLength());
        state = StreamingState.STREAMING;
        return ta;
    }

    JTextArea getStreamingTextArea() {
        return streamingTextArea;
    }

    /**
     * Returns true if the streaming JTextArea still exists in the component
     * tree. This is the physical source of truth — unlike {@link #isStreaming()}
     * it cannot lie if the state enum drifts out of sync with the component tree.
     */
    boolean hasStreamingTextArea() {
        return streamingTextArea != null && streamingTextArea.getParent() != null;
    }

    void setStreamingTextArea(JTextArea ta) {
        this.streamingTextArea = ta;
    }

    int getLastDisplayedLength() {
        return lastDisplayedLength;
    }

    void setLastDisplayedLength(int len) {
        this.lastDisplayedLength = len;
    }

    void appendText(String newText) {
        if (newText == null || newText.isEmpty()) {
            return;
        }
        text.append(newText);
        hasPendingTextUpdate = true;
        if (state == StreamingState.DEFERRED_FINALIZING && deferredFinalizeTimer != null) {
            deferredFinalizeTimer.restart();
        }
    }

    boolean flushUpdate(boolean force) {
        if (!hasPendingTextUpdate && !force) {
            return false;
        }
        hasPendingTextUpdate = false;

        if (state != StreamingState.FINALIZED) {
            int totalLen = text.length();
            if (streamingTextArea != null && totalLen > lastDisplayedLength) {
                // Use substring to get a copy of only the new delta.
                String delta = text.substring(lastDisplayedLength, totalLen);
                Document doc = streamingTextArea.getDocument();
                try {
                    doc.insertString(doc.getLength(), delta, null);
                } catch (BadLocationException ignored) {
                    streamingTextArea.append(delta);
                }
                lastDisplayedLength = totalLen;
                return true;
            }
            // Tool/thought streaming: no streaming text area — update via
            // contentUpdater so new text chunks are flushed to the pane.
            if (totalLen > lastDisplayedLength) {
                contentUpdater.update(ThemeManager.getCurrentTheme(), true);
                lastDisplayedLength = totalLen;
                return true;
            }
            return false;
        }

        contentUpdater.update(ThemeManager.getCurrentTheme(), true);
        return true;
    }

    void finalizeStreaming(boolean expanded, boolean immediate) {
        if (state == StreamingState.FINALIZED) {
            postFinalizeCallback.accept(expanded, false);
            return;
        }

        savedCollapseState = expanded;

        if (immediate) {
            // Stop any pending deferred timer to prevent a double
            // finalization when the stale timer eventually fires.
            if (deferredFinalizeTimer != null && deferredFinalizeTimer.isRunning()) {
                deferredFinalizeTimer.stop();
            }
            performFinalization();
        } else {
            state = StreamingState.DEFERRED_FINALIZING;
            if (deferredFinalizeTimer == null) {
                deferredFinalizeTimer = new Timer(TimingConstants.STREAM_FLUSH_MS, e -> performFinalization());
                deferredFinalizeTimer.setRepeats(false);
            }
            deferredFinalizeTimer.restart();
        }
    }

    private void performFinalization() {
        state = StreamingState.FINALIZED;
        if (deferredFinalizeTimer != null) {
            deferredFinalizeTimer.stop();
        }
        if (streamingTextArea != null) {
            try {
                segments.remove(streamingTextArea);
            } catch (Exception ex) {
                LOG.warn("Failed to remove streaming text area during finalization", ex);
            }
            streamingTextArea = null;
        }
        try {
            contentUpdater.update(ThemeManager.getCurrentTheme(), savedCollapseState);
            postFinalizeCallback.accept(savedCollapseState, false);
        } catch (Exception ex) {
            LOG.warn("Failed to build final HTML content during streaming finalization", ex);
        }
        segments.revalidate();
        segments.repaint();
    }

    boolean isStreaming() {
        // Physical check covers state drift: if any text area is still in
        // the component tree, we are still streaming regardless of enum state.
        return streamingTextArea != null || state != StreamingState.FINALIZED;
    }

    /** Returns true unless the state machine is in the FINALIZED state.
     *  Used by the sweep failsafe to decide whether normal finalization will
     *  work or whether force-finalization is needed. */
    boolean streamingFlagsSet() {
        return state != StreamingState.FINALIZED;
    }

    /**
     * Force-finalizes streaming without checking the state enum. Used by the
     * sweep failsafe ({@link ChatThreadPanel#sweepStreamingBubbles()}) when
     * a bubble's streaming JTextArea is still in the component tree but the
     * state was left inconsistent (exception midway through finalization,
     * double-finalize short-circuit, etc.).
     */
    void forceFinalize(boolean expanded) {
        if (streamingTextArea != null) {
            savedCollapseState = expanded;
            performFinalization();
        }
    }

    void stopTimer() {
        if (deferredFinalizeTimer != null && deferredFinalizeTimer.isRunning()) {
            deferredFinalizeTimer.stop();
        }
    }
}
