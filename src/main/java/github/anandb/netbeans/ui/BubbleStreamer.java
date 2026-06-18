package github.anandb.netbeans.ui;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.BiConsumer;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.openide.util.NbBundle;

import github.anandb.netbeans.support.TimingConstants;

class BubbleStreamer {

    interface ContentUpdater {
        void update(ColorTheme theme, boolean expanded);
    }

    private static final java.awt.Color TRANSPARENT = new java.awt.Color(0, 0, 0, 0);

    private final ContentUpdater contentUpdater;
    private final BiConsumer<Boolean, Boolean> postFinalizeCallback;
    private final JPanel segments;
    private final StringBuilder text;

    private JTextArea streamingTextArea;
    private int lastDisplayedLength = 0;
    private volatile boolean hasPendingTextUpdate = false;
    private boolean isStreaming = false;
    private boolean isFinalizingDeferred = false;
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
            public java.awt.Dimension getMaximumSize() {
                java.awt.Dimension pref = getPreferredSize();
                return new java.awt.Dimension(Short.MAX_VALUE, pref.height);
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
        ta.setBorder(new EmptyBorder(4, 20, 4, 6));
        ta.setCaretPosition(ta.getDocument().getLength());
        isStreaming = true;
        return ta;
    }

    JTextArea getStreamingTextArea() {
        return streamingTextArea;
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
        if (isFinalizingDeferred && deferredFinalizeTimer != null) {
            deferredFinalizeTimer.restart();
        }
    }

    boolean flushUpdate(boolean force) {
        if (!hasPendingTextUpdate && !force) {
            return false;
        }
        hasPendingTextUpdate = false;

        if (isStreaming || isFinalizingDeferred) {
            int totalLen = text.length();
            if (streamingTextArea != null && totalLen > lastDisplayedLength) {
                // Use subSequence to get a CharSequence view; String.valueOf
                // of a CharSequence creates a String without copying the
                // already-rendered prefix of the StringBuilder's char array.
                CharSequence tail = text.subSequence(lastDisplayedLength, totalLen);
                String delta = tail.toString();
                javax.swing.text.Document doc = streamingTextArea.getDocument();
                try {
                    doc.insertString(doc.getLength(), delta, null);
                } catch (javax.swing.text.BadLocationException ignored) {
                    streamingTextArea.append(delta);
                }
                lastDisplayedLength = totalLen;
                return true;
            }
            return false;
        }

        contentUpdater.update(ThemeManager.getCurrentTheme(), true);
        return true;
    }

    void finalizeStreaming(boolean expanded, boolean immediate) {
        if (!isStreaming && !isFinalizingDeferred) {
            postFinalizeCallback.accept(expanded, false);
            return;
        }

        savedCollapseState = expanded;

        if (immediate) {
            performFinalization();
        } else {
            isFinalizingDeferred = true;
            if (deferredFinalizeTimer == null) {
                deferredFinalizeTimer = new Timer(TimingConstants.STREAM_FLUSH_MS, e -> performFinalization());
                deferredFinalizeTimer.setRepeats(false);
            }
            deferredFinalizeTimer.restart();
        }
    }

    private void performFinalization() {
        isStreaming = false;
        isFinalizingDeferred = false;
        if (deferredFinalizeTimer != null) {
            deferredFinalizeTimer.stop();
        }
        if (streamingTextArea != null) {
            segments.remove(streamingTextArea);
            streamingTextArea = null;
        }
        contentUpdater.update(ThemeManager.getCurrentTheme(), savedCollapseState);
        segments.revalidate();
        segments.repaint();
    }

    boolean isStreaming() {
        return isStreaming || isFinalizingDeferred;
    }

    void stopTimer() {
        if (deferredFinalizeTimer != null && deferredFinalizeTimer.isRunning()) {
            deferredFinalizeTimer.stop();
        }
    }
}
