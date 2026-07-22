package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.JProgressBar;
import java.awt.Dimension;

import org.openide.windows.WindowManager;

import github.anandb.netbeans.model.MessageType;

public class MiniAssistantDialog extends JDialog {

    private static MiniAssistantDialog instance;
    private PlaceholderTextArea inputArea;
    private JPanel responsePane;
    
    // Drag variables
    private Point initialClick;
    
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
        setSize(500, 300);
        setLocationRelativeTo(getParent());
        setAlwaysOnTop(true);
        
        setLayout(new BorderLayout());
        
        responsePane = new JPanel(new BorderLayout());
        inputArea = new PlaceholderTextArea("");
        
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new java.awt.KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
                if (!isVisible()) return false;
                java.awt.Component focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focusOwner != MiniAssistantDialog.this && !isAncestorOf(focusOwner)) return false;
                
                if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_PAGE_UP) {
                        navigateAssistantBubble(-1);
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_PAGE_DOWN) {
                        navigateAssistantBubble(1);
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                        dispose();
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && !e.isShiftDown()) {
                        if (inputArea.isFocusOwner()) {
                            sendMessage();
                            return true; // only consume Enter if in inputArea
                        }
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_C && e.isControlDown()) {
                        copyContent();
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_L && e.isControlDown() && !e.isAltDown()) {
                        AssistantTopComponent tc = AssistantTopComponent.findInstance();
                        if (tc != null) {
                            tc.toggleVisibility();
                        }
                        return true;
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_L && e.isControlDown() && e.isAltDown()) {
                        toggleVisibility();
                        return true;
                    }
                }
                return false;
            }
        });
        
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
        
        splitPane.setTopComponent(scrollPane);
        splitPane.setDividerSize(2);
        inputArea.setOverlayText("Esc: close | Ctrl+L: Main Assistant Panel | Ctrl+Alt+L: focus toggle | "
            + "PgUp/PgDn: scroll | Ctrl+C: copy | Enter: send");
        
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
        responsePane.removeAll();
        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setPreferredSize(new Dimension(100, 4));
        spinner.setBorderPainted(false);
        
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(spinner, BorderLayout.NORTH);
        
        responsePane.add(p, BorderLayout.CENTER);
        responsePane.revalidate();
        responsePane.repaint();
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
            if (!processing && isVisible()) {
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
        });
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
        inputArea.setFont(f.deriveFont(Math.max(10f, f.getSize() - 1f)));
        
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
