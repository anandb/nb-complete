package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.JProgressBar;
import java.awt.Dimension;
import java.util.prefs.Preferences;

import org.openide.util.NbPreferences;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.PreferenceKeys;

public class MiniAssistantDialog extends JDialog {

    private static MiniAssistantDialog instance;
    private PlaceholderTextArea inputArea;
    private JPanel responsePane;
    private javax.swing.JLabel tokenOverlay;
    private javax.swing.Timer tokenTimer;
    private java.awt.KeyEventDispatcher keyDispatcher;

    // Navigation state
    private int currentBubbleIndex = -1;
    
    public static synchronized MiniAssistantDialog getInstance() {
        if (instance == null) {
            instance = new MiniAssistantDialog();
        }
        return instance;
    }

    private MiniAssistantDialog() {
        super(WindowManager.getDefault().getMainWindow(), false);
        restoreBounds();
        
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                saveBounds();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                saveBounds();
            }
        });
        
        setLayout(new BorderLayout());
        
        responsePane = new JPanel(new BorderLayout());
        inputArea = new PlaceholderTextArea("");
        
        keyDispatcher = new java.awt.KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
                if (!isVisible()) return false;
                java.awt.Component focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focusOwner != MiniAssistantDialog.this && !isAncestorOf(focusOwner)) return false;

                if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                    boolean isMac = org.openide.util.Utilities.isMac();
                    boolean isCmdOrCtrl = isMac ? e.isMetaDown() : e.isControlDown();
                    boolean isPrev = e.getKeyCode() == java.awt.event.KeyEvent.VK_PAGE_UP ||
                                     (isMac && e.getKeyCode() == java.awt.event.KeyEvent.VK_LEFT && e.isMetaDown());
                    boolean isNext = e.getKeyCode() == java.awt.event.KeyEvent.VK_PAGE_DOWN ||
                                     (isMac && e.getKeyCode() == java.awt.event.KeyEvent.VK_RIGHT && e.isMetaDown());

                    if (isPrev) {
                        navigateAssistantBubble(-1);
                        return true;
                    } else if (isNext) {
                        navigateAssistantBubble(1);
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                        setVisible(false);
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && !e.isShiftDown()) {
                        if (inputArea.isFocusOwner()) {
                            sendMessage();
                            return true; // only consume Enter if in inputArea
                        }
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_C && isCmdOrCtrl) {
                        copyContent();
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_L && isCmdOrCtrl && !e.isAltDown()) {
                        AssistantTopComponent tc = AssistantTopComponent.findInstance();
                        if (tc != null) {
                            tc.toggleVisibility();
                        }
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_L && isCmdOrCtrl && e.isAltDown()) {
                        toggleVisibility();
                        return true;
                    }
                }
                return false;
            }
        };
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, null, inputArea);
        splitPane.setResizeWeight(0.85); // give more space to response
        responsePane = new ScrollablePanel(new BorderLayout());
        responsePane.setOpaque(true);
        responsePane.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(responsePane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Token count overlay — top-right corner of the scroll pane
        tokenOverlay = new javax.swing.JLabel();
        tokenOverlay.setOpaque(true);
        tokenOverlay.setFont(ThemeManager.getMonospaceFont().deriveFont(10f));
        tokenOverlay.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        tokenOverlay.setVisible(false);

        // Wrapper so overlay floats above the scroll pane
        JPanel responseWrapper = new JPanel(new java.awt.BorderLayout());
        responseWrapper.setOpaque(false);
        responseWrapper.add(scrollPane, java.awt.BorderLayout.CENTER);
        responseWrapper.add(tokenOverlay, java.awt.BorderLayout.NORTH);
        // Anchor overlay to the right
        ((java.awt.BorderLayout) responseWrapper.getLayout()).setHgap(0);
        tokenOverlay.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);

        splitPane.setTopComponent(responseWrapper);
        splitPane.setDividerSize(2);
        
        boolean isMac = org.openide.util.Utilities.isMac();
        String toggleAction = github.anandb.netbeans.support.ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleAssistantAction");
        if (toggleAction == null || toggleAction.isEmpty()) toggleAction = isMac ? "Cmd+L" : "Ctrl+L";
        
        String miniToggleAction = github.anandb.netbeans.support.ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleMiniAssistantAction");
        if (miniToggleAction == null || miniToggleAction.isEmpty()) miniToggleAction = isMac ? "Cmd+Alt+L" : "Ctrl+Alt+L";
        
        String scrollAction = isMac ? "Cmd+Left/Right: scroll" : "PgUp/PgDn: scroll";
        String copyAction = isMac ? "Cmd+C: copy" : "Ctrl+C: copy";
        
        inputArea.setOverlayText("Esc: close | " + toggleAction + ": Main Assistant Panel | " + miniToggleAction + ": focus toggle | "
            + scrollAction + " | " + copyAction + " | Enter: send");
        
        add(splitPane, BorderLayout.CENTER);
        
        applyTheme();
    }

    public void toggleVisibility() {
        if (isVisible()) {
            java.awt.Component focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            boolean hasFocus = inputArea.isFocusOwner() || isAncestorOf(focusOwner);
            if (hasFocus) {
                org.openide.windows.Mode editorMode = org.openide.windows.WindowManager.getDefault().findMode("editor");
                if (editorMode != null && editorMode.getSelectedTopComponent() != null) {
                    editorMode.getSelectedTopComponent().requestActive();
                }
            } else {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    toFront();
                    inputArea.requestFocus();
                });
            }
        } else {
            updateResponsePane();
            setVisible(true);
            inputArea.requestFocusInWindow();
        }
    }
    
    private void sendMessage() {
        String text = inputArea.getText();
        if (text.trim().isEmpty()) return;
        
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            tc.setInputText(text);
            tc.sendMessage();
            
            showSpinner();
        }
    }
    
    private void showSpinner() {
        // Add a thin spinner bar at the top of responsePane, keep existing content
        if (responsePane.getComponentCount() == 0 ||
            !(responsePane.getComponent(0) instanceof JProgressBar)) {
            JProgressBar spinner = new JProgressBar();
            spinner.setIndeterminate(true);
            spinner.setPreferredSize(new Dimension(Integer.MAX_VALUE, 3));
            spinner.setBorderPainted(false);
            responsePane.add(spinner, BorderLayout.NORTH);
            responsePane.revalidate();
            responsePane.repaint();
        }
        updateTokenOverlay();
        startTokenPolling();
    }

    private void updateTokenOverlay() {
        github.anandb.netbeans.contract.SessionControl sc =
                org.openide.util.Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class);
        if (sc == null) return;
        String sessionId = sc.getCurrentSessionId();
        if (sessionId == null) return;
        String usage = sc.getContextUsage(sessionId);
        if (usage != null && !usage.isEmpty()) {
            String[] parts = usage.split(",");
            if (parts.length == 2) {
                tokenOverlay.setText(parts[0].trim() + " / " + parts[1].trim());
                tokenOverlay.setVisible(true);
            }
        }
    }

    private void startTokenPolling() {
        stopTokenPolling();
        tokenTimer = new javax.swing.Timer(500, e -> updateTokenOverlay());
        tokenTimer.start();
    }

    private void stopTokenPolling() {
        if (tokenTimer != null) {
            tokenTimer.stop();
            tokenTimer = null;
        }
    }
    
    private void navigateAssistantBubble(int direction) {
        List<MessageBubble> bubbles = getAssistantBubbles();
        if (bubbles.isEmpty()) return;
        
        if (currentBubbleIndex == -1) {
            currentBubbleIndex = bubbles.size() - 1;
        }
        
        currentBubbleIndex += direction;
        currentBubbleIndex = Math.max(0, Math.min(bubbles.size() - 1, currentBubbleIndex));
        
        displayBubble(bubbles.get(currentBubbleIndex));
    }
    
    private List<MessageBubble> getAssistantBubbles() {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                List<MessageBubble>[] result = new List[]{List.of()};
                SwingUtilities.invokeAndWait(() -> result[0] = getAssistantBubbles());
                return result[0];
            } catch (InterruptedException | java.lang.reflect.InvocationTargetException ex) {
                return List.of();
            }
        }
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc == null || tc.getChatThreadPanel() == null) return List.of();

        List<MessageBubble> bubbles = new ArrayList<>();
        Component[] comps = tc.getChatThreadPanel().getMessagesContainer().getComponents();
        for (Component c : comps) {
            if (c instanceof MessageBubble mb && "assistant".equals(mb.getRole())) {
                bubbles.add(mb);
            }
        }
        return bubbles;
    }
    
    private void displayBubble(MessageBubble realBubble) {
        responsePane.removeAll();
        MessageBubble localBubble = new MessageBubble(MessageType.agent_message_chunk, 
            realBubble.getRawText(), realBubble.getMessageId(), null, 
            MessageBubble.AvatarPosition.NONE, false, null);
        
        styleAsMiniBubble(localBubble);
        reduceFontSize(localBubble);
        responsePane.add(localBubble, BorderLayout.CENTER);
        
        localBubble.setFontSizeOverride(Math.max(9, ThemeManager.getFont().getSize() - 2));
        localBubble.finalizeStreaming(true);
        
        responsePane.revalidate();
        responsePane.repaint();
    }
    
    private void reduceFontSize(Component comp) {
        if (comp.getFont() != null) {
            java.awt.Font f = comp.getFont();
            comp.setFont(f.deriveFont(Math.max(10f, f.getSize() - 1f)));
        }
        if (comp instanceof javax.swing.JEditorPane) {
            javax.swing.JEditorPane pane = (javax.swing.JEditorPane) comp;
            pane.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        }
        if (comp instanceof java.awt.Container) {
            for (Component c : ((java.awt.Container) comp).getComponents()) {
                reduceFontSize(c);
            }
        }
    }
    
    public void onProcessingChanged(boolean processing) {
        SwingUtilities.invokeLater(() -> {
            inputArea.setEnabled(!processing);
            if (processing) {
                showSpinner();
            } else {
                stopTokenPolling();
                // Remove spinner bar if present
                for (Component c : responsePane.getComponents()) {
                    if (c instanceof JProgressBar) {
                        responsePane.remove(c);
                        break;
                    }
                }
                tokenOverlay.setVisible(false);
                if (isVisible()) {
                    AssistantTopComponent tc = AssistantTopComponent.findInstance();
                    if (tc != null) {
                        List<MessageBubble> bubbles = getAssistantBubbles();
                        if (!bubbles.isEmpty()) {
                            currentBubbleIndex = bubbles.size() - 1;
                            MessageBubble realBubble = bubbles.get(currentBubbleIndex);
                            inputArea.setText("");
                            displayBubble(realBubble);
                        }
                    }
                }
            }
        });
    }

    public void onStreamUpdate(github.anandb.netbeans.model.ProcessedMessage msg) {
        SwingUtilities.invokeLater(this::updateTokenOverlay);
    }

    private void styleAsMiniBubble(MessageBubble mb) {
        mb.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        mb.setOpaque(false); // Let responsePane background show through margins
        if (mb.getLayout() instanceof java.awt.GridBagLayout gb) {
            for (Component c : mb.getComponents()) {
                java.awt.GridBagConstraints gbc = gb.getConstraints(c);
                gbc.insets = new java.awt.Insets(0, 0, 0, 0);
                gbc.weightx = 1.0;
                gbc.weighty = 1.0;
                gbc.fill = java.awt.GridBagConstraints.BOTH;
                gb.setConstraints(c, gbc);
                if (c instanceof RoundedPanel) {
                    ((JPanel) c).setBorder(BorderFactory.createEmptyBorder());
                }
            }
        }
    }
    
    private void copyContent() {
        if (inputArea.getSelectedText() != null) {
            inputArea.copy();
            return;
        }
        
        Component[] comps = responsePane.getComponents();
        if (comps.length > 0 && comps[0] instanceof MessageBubble) {
            MessageBubble bubble = (MessageBubble) comps[0];
            String text = bubble.getRawText();
            int start = text.indexOf("```");
            if (start != -1) {
                int firstNewline = text.indexOf('\n', start);
                if (firstNewline != -1) {
                    int end = text.indexOf("```", firstNewline);
                    if (end != -1) {
                        String code = text.substring(firstNewline + 1, end).trim();
                        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(code);
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                        return;
                    }
                }
            }
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        }
    }
    
    private void updateResponsePane() {
        String sessionId = org.openide.util.Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class).getCurrentSessionId();
        
        List<MessageBubble> bubbles = getAssistantBubbles();
        if (!bubbles.isEmpty()) {
            currentBubbleIndex = bubbles.size() - 1;
            displayBubble(bubbles.get(currentBubbleIndex));
            inputArea.setEnabled(true);
        } else {
            currentBubbleIndex = -1;
            responsePane.removeAll();
            
            String msg = (sessionId == null) ? "Start a chat from the main sidebar to begin." : "Ready to help.";
            inputArea.setEnabled(sessionId != null);
            
            MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, 
                msg, "mini_0", null, 
                MessageBubble.AvatarPosition.NONE, false, null);
                
            styleAsMiniBubble(bubble);
            reduceFontSize(bubble);
                
            responsePane.add(bubble, BorderLayout.CENTER);
            
            bubble.setFontSizeOverride(Math.max(9, ThemeManager.getFont().getSize() - 2));
            bubble.finalizeStreaming(true);
            
            responsePane.revalidate();
            responsePane.repaint();
        }
    }

    private void restoreBounds() {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        int w = prefs.getInt(PreferenceKeys.MINI_ASSISTANT_WIDTH, 500);
        int h = prefs.getInt(PreferenceKeys.MINI_ASSISTANT_HEIGHT, 300);
        int x = prefs.getInt(PreferenceKeys.MINI_ASSISTANT_X, Integer.MIN_VALUE);
        int y = prefs.getInt(PreferenceKeys.MINI_ASSISTANT_Y, Integer.MIN_VALUE);

        w = Math.max(250, w);
        h = Math.max(150, h);
        setSize(w, h);

        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && isPositionOnScreen(x, y, w, h)) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(getParent());
        }
    }

    private boolean isPositionOnScreen(int x, int y, int w, int h) {
        java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        java.awt.GraphicsDevice[] screens = ge.getScreenDevices();
        java.awt.Rectangle dialogRect = new java.awt.Rectangle(x, y, w, h);
        for (java.awt.GraphicsDevice screen : screens) {
            java.awt.Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
            if (screenBounds.intersects(dialogRect)) {
                return true;
            }
        }
        return false;
    }

    private void saveBounds() {
        if (!isVisible()) return;
        java.awt.Rectangle bounds = getBounds();
        if (bounds.width > 0 && bounds.height > 0) {
            Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
            prefs.putInt(PreferenceKeys.MINI_ASSISTANT_X, bounds.x);
            prefs.putInt(PreferenceKeys.MINI_ASSISTANT_Y, bounds.y);
            prefs.putInt(PreferenceKeys.MINI_ASSISTANT_WIDTH, bounds.width);
            prefs.putInt(PreferenceKeys.MINI_ASSISTANT_HEIGHT, bounds.height);
        }
    }

    @Override
    public void setVisible(boolean b) {
        if (!b && isVisible()) {
            saveBounds();
        }
        super.setVisible(b);
    }

    @Override
    public void dispose() {
        if (isVisible()) {
            saveBounds();
        }
        if (keyDispatcher != null) {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
        super.dispose();
        instance = null;
    }

    private void applyTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        getContentPane().setBackground(theme.background());
        responsePane.setBackground(theme.background());
        if (responsePane.getParent() instanceof javax.swing.JViewport vp) {
            vp.setBackground(theme.background());
            vp.setOpaque(true);
            if (vp.getParent() instanceof javax.swing.JScrollPane sp) {
                sp.setBackground(theme.background());
                sp.setOpaque(true);
            }
        }
        inputArea.setBackground(theme.background());
        inputArea.setForeground(theme.foreground());
        inputArea.setCaretColor(theme.foreground());
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 4, 2, 4),
                BorderFactory.createSoftBevelBorder(javax.swing.border.BevelBorder.LOWERED)
            ),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));

        java.awt.Font f = ThemeManager.getFont();
        inputArea.setFont(f);

        tokenOverlay.setBackground(theme.background().brighter());
        tokenOverlay.setForeground(theme.foreground());

        SwingUtilities.updateComponentTreeUI(this);
    }
    
    private static class ScrollablePanel extends JPanel implements javax.swing.Scrollable {
        ScrollablePanel(java.awt.LayoutManager layout) { super(layout); }
        @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { 
            if (getParent() instanceof javax.swing.JViewport vp) {
                return getPreferredSize().height < vp.getHeight();
            }
            return false;
        }
    }
}
