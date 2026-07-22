package github.anandb.netbeans.ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;

import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.Utilities;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.ShortcutUtils;

public class MiniAssistantDialog extends JDialog {

    private static MiniAssistantDialog instance;
    private PlaceholderTextArea inputArea;
    private JPanel responsePane;
    private JSplitPane splitPane;
    private JLabel tokenOverlay;
    private JProgressBar spinnerBar;
    private Timer tokenTimer;
    private KeyEventDispatcher keyDispatcher;

    private static final Set<String> DISALLOWED_MINI_COMMANDS = Set.of(
        "/model", "/models", "/level", "/sessions", "/agents"
    );

    private AutocompleteManager autocompleteManager;

    // Navigation state
    private int currentBubbleIndex = -1;
    private boolean isAutoTrackingLatest = true;
    private String lastSentText;
    private boolean isProcessing;
    private int maxTokenCountThisTurn;
    private String displayedMessageId;
    private String displayedText;

    private int wordCount;
    private final Map<String, Integer> wordsByMessageId = new ConcurrentHashMap<>();
    
    public static synchronized MiniAssistantDialog getInstance() {
        if (instance == null) {
            instance = new MiniAssistantDialog();
        }
        return instance;
    }

    public static void closeIfVisible() {
        if (instance != null && instance.isVisible()) {
            if (SwingUtilities.isEventDispatchThread()) {
                instance.setVisible(false);
            } else {
                SwingUtilities.invokeLater(() -> {
                    if (instance != null && instance.isVisible()) {
                        instance.setVisible(false);
                    }
                });
            }
        }
    }

    private MiniAssistantDialog() {
        super(WindowManager.getDefault().getMainWindow(), false);
        restoreBounds();
        
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                saveBounds();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                saveBounds();
            }
        });
        
        setLayout(new BorderLayout());
        
        responsePane = new JPanel(new BorderLayout());
        inputArea = new PlaceholderTextArea("");
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        
        Set<String> miniExcluded = Set.of("model", "models", "level", "sessions", "agents");
        autocompleteManager = new AutocompleteManager(inputArea, this::sendMessage, miniExcluded);
        
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (autocompleteManager != null) {
                    autocompleteManager.handleKeyReleased(e);
                }
            }
        });
        
        keyDispatcher = new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (!isVisible()) return false;
                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focusOwner != MiniAssistantDialog.this && !isAncestorOf(focusOwner)) return false;

                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    boolean isMac = Utilities.isMac();
                    boolean isCmdOrCtrl = isMac ? e.isMetaDown() : e.isControlDown();
                    boolean isPrev = e.getKeyCode() == KeyEvent.VK_PAGE_UP ||
                                     (isMac && e.getKeyCode() == KeyEvent.VK_LEFT && e.isMetaDown());
                    boolean isNext = e.getKeyCode() == KeyEvent.VK_PAGE_DOWN ||
                                     (isMac && e.getKeyCode() == KeyEvent.VK_RIGHT && e.isMetaDown());

                    if (isPrev) {
                        navigateAssistantBubble(-1);
                        return true;
                    } else if (isNext) {
                        navigateAssistantBubble(1);
                        return true;
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        if (autocompleteManager != null && autocompleteManager.isPopupVisible()) {
                            autocompleteManager.handleKeyReleased(e);
                            return true;
                        }
                        AssistantTopComponent tc = AssistantTopComponent.findInstance();
                        if (tc != null && tc.getConfigPanelController() != null
                                && tc.getConfigPanelController().isAnyPopupVisible()) {
                            tc.getConfigPanelController().closeAnyPopup();
                            return true;
                        }
                        setVisible(false);
                        return true;
                    } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        if (inputArea.isFocusOwner()) {
                            if (autocompleteManager != null && autocompleteManager.isPopupVisible()) {
                                autocompleteManager.handleKeyPressed(e);
                                return true;
                            }
                            if (e.isShiftDown()) {
                                inputArea.insert("\n", inputArea.getCaretPosition());
                                return true;
                            } else {
                                sendMessage();
                                return true;
                            }
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_TAB) {
                        if (inputArea.isFocusOwner()) {
                            e.consume();
                            if (autocompleteManager != null && autocompleteManager.isPopupVisible()) {
                                autocompleteManager.selectCommand();
                            } else {
                                var pc = Lookup.getDefault()
                                        .lookup(ProcessControl.class);
                                var interceptor = pc != null ? pc.getSlashCommandInterceptor() : null;
                                var cb = interceptor != null ? interceptor.getCallback() : null;
                                if (cb != null) {
                                    cb.popupAgentCombo();
                                }
                            }
                            return true;
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_C && isCmdOrCtrl) {
                        copyContent();
                        return true;
                    } else if (e.getKeyCode() == KeyEvent.VK_L && isCmdOrCtrl && !e.isAltDown()) {
                        AssistantTopComponent tc = AssistantTopComponent.findInstance();
                        if (tc != null) {
                            tc.toggleVisibility();
                        }
                        return true;
                    } else if (e.getKeyCode() == KeyEvent.VK_L && isCmdOrCtrl && e.isAltDown()) {
                        toggleVisibility();
                        return true;
                    }
                }
                return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
        
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, null, inputArea);
        splitPane.setResizeWeight(0.85); // give more space to response
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> saveBounds());
        splitPane.addComponentListener(new ComponentAdapter() {
            private boolean dividerRestored = false;
            @Override
            public void componentResized(ComponentEvent e) {
                if (!dividerRestored && splitPane.getHeight() > 50) {
                    dividerRestored = true;
                    restoreDividerLocation();
                }
            }
        });
        responsePane = new ScrollablePanel(new BorderLayout());
        responsePane.setOpaque(true);
        responsePane.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));

        JScrollPane scrollPane = new JScrollPane(responsePane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Progress bar right at the top of the dialog
        spinnerBar = new JProgressBar();
        spinnerBar.setIndeterminate(true);
        spinnerBar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 3));
        spinnerBar.setBorderPainted(false);
        spinnerBar.setVisible(false);

        // Token count overlay — permanently reserved top-right header space
        tokenOverlay = new JLabel();
        tokenOverlay.setOpaque(false);
        tokenOverlay.setFont(ThemeManager.getMonospaceFont().deriveFont(10f));
        tokenOverlay.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        tokenOverlay.setPreferredSize(new Dimension(Integer.MAX_VALUE, 20));
        tokenOverlay.setMinimumSize(new Dimension(0, 20));
        tokenOverlay.setVisible(true);
        tokenOverlay.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel topHeader = new JPanel(new BorderLayout());
        topHeader.setOpaque(false);
        topHeader.add(spinnerBar, BorderLayout.NORTH);
        topHeader.add(tokenOverlay, BorderLayout.SOUTH);

        // Wrapper so header floats above the scroll pane
        JPanel responseWrapper = new JPanel(new BorderLayout());
        responseWrapper.setOpaque(false);
        responseWrapper.add(scrollPane, BorderLayout.CENTER);
        responseWrapper.add(topHeader, BorderLayout.NORTH);

        splitPane.setTopComponent(responseWrapper);
        splitPane.setDividerSize(2);
        
        boolean isMac = Utilities.isMac();
        String toggleAction = ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleAssistantAction");
        if (toggleAction == null || toggleAction.isEmpty()) toggleAction = isMac ? "Cmd+L" : "Ctrl+L";
        
        String miniToggleAction = ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleMiniAssistantAction");
        if (miniToggleAction == null || miniToggleAction.isEmpty()) miniToggleAction = isMac ? "Cmd+Alt+L" : "Ctrl+Alt+L";
        
        String scrollAction = isMac ? "Cmd+Left/Right: scroll" : "PgUp/PgDn: scroll";
        String copyAction = isMac ? "Cmd+C: copy" : "Ctrl+C: copy";
        
        inputArea.setOverlayText("Esc: close | " + toggleAction + ": Main Assistant Panel | " + miniToggleAction + ": focus toggle | "
            + scrollAction + " | " + copyAction + " | Enter: send");
        
        add(splitPane, BorderLayout.CENTER);
        
        applyTheme();
    }

    public PlaceholderTextArea getInputArea() {
        return inputArea;
    }

    public void toggleVisibility() {
        if (isVisible()) {
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            boolean hasFocus = inputArea.isFocusOwner() || isAncestorOf(focusOwner);
            if (hasFocus) {
                Mode editorMode = WindowManager.getDefault().findMode("editor");
                if (editorMode != null && editorMode.getSelectedTopComponent() != null) {
                    editorMode.getSelectedTopComponent().requestActive();
                }
            } else {
                SwingUtilities.invokeLater(() -> {
                    toFront();
                    requestFocus();
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
        
        String trimmed = text.trim();
        int spaceIdx = trimmed.indexOf(' ');
        String firstWord = spaceIdx > 0 ? trimmed.substring(0, spaceIdx).toLowerCase() : trimmed.toLowerCase();
        if (DISALLOWED_MINI_COMMANDS.contains(firstWord)) {
            inputArea.setText("");
            return;
        }

        var pc = Lookup.getDefault()
                .lookup(ProcessControl.class);
        var interceptor = pc != null ? pc.getSlashCommandInterceptor() : null;
        if (interceptor != null && trimmed.startsWith("/")) {
            String cmd = spaceIdx > 0 ? trimmed.substring(0, spaceIdx) : trimmed;
            if (interceptor.getCommands().containsKey(cmd)) {
                inputArea.setText("");
                interceptor.intercept(trimmed, Lookup.getDefault());
                return;
            }
        }
        
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            lastSentText = text;
            inputArea.setText("");
            
            tc.setInputText(text);
            tc.sendMessage();
            
            showSpinner();
        }
    }
    
    private void showSpinner() {
        wordCount = 0;
        wordsByMessageId.clear();
        isAutoTrackingLatest = true;
        currentBubbleIndex = -1;
        if (spinnerBar != null) {
            spinnerBar.setVisible(true);
        }
        updateTokenOverlay();
        startTokenPolling();
    }

    private void setTokenOverlayVisible(boolean active, String text) {
        if (active && text != null && !text.isEmpty()) {
            if (!text.equals(tokenOverlay.getText())) {
                tokenOverlay.setText(text);
            }
            applyTokenOverlayColors(true);
        } else {
            tokenOverlay.setText("");
            applyTokenOverlayColors(false);
        }
    }

    private void applyTokenOverlayColors(boolean active) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        if (!active) {
            tokenOverlay.setOpaque(false);
            tokenOverlay.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return;
        }
        tokenOverlay.setOpaque(true);
        tokenOverlay.setBackground(theme.sunkenBackground());
        tokenOverlay.setForeground(theme.foreground());
        tokenOverlay.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.bubbleBorder()),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
    }

    private void updateTokenOverlay() {
        int count = calculateCurrentTokenCount();
        if (count > maxTokenCountThisTurn) {
            maxTokenCountThisTurn = count;
        }
        int displayCount = Math.max(count, maxTokenCountThisTurn);
        if (displayCount > 0) {
            setTokenOverlayVisible(true, displayCount + " tokens received");
        } else if (!isProcessing) {
            setTokenOverlayVisible(false, null);
        }
    }

    private void startTokenPolling() {
        stopTokenPolling();
        tokenTimer = new Timer(500, e -> updateTokenOverlay());
        tokenTimer.start();
    }

    private void stopTokenPolling() {
        if (tokenTimer != null) {
            tokenTimer.stop();
            tokenTimer = null;
        }
        maxTokenCountThisTurn = 0;
        setTokenOverlayVisible(false, null);
    }
    
    private void navigateAssistantBubble(int direction) {
        List<MessageBubble> bubbles = getAssistantBubbles();
        if (bubbles.isEmpty()) return;
        
        if (currentBubbleIndex == -1) {
            currentBubbleIndex = bubbles.size() - 1;
        }
        
        currentBubbleIndex += direction;
        currentBubbleIndex = Math.max(0, Math.min(bubbles.size() - 1, currentBubbleIndex));
        isAutoTrackingLatest = (currentBubbleIndex == bubbles.size() - 1);
        
        displayBubble(bubbles.get(currentBubbleIndex));
    }
    
    private List<MessageBubble> getAllResponseBubbles() {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                List<MessageBubble>[] result = new List[]{List.of()};
                SwingUtilities.invokeAndWait(() -> result[0] = getAllResponseBubbles());
                return result[0];
            } catch (InterruptedException | InvocationTargetException ex) {
                return List.of();
            }
        }
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc == null || tc.getChatThreadPanel() == null) return List.of();

        List<MessageBubble> bubbles = new ArrayList<>();
        Component[] comps = tc.getChatThreadPanel().getMessagesContainer().getComponents();
        for (Component c : comps) {
            if (c instanceof MessageBubble mb && !"user".equals(mb.getRole())) {
                bubbles.add(mb);
            }
        }
        return bubbles;
    }

    private List<MessageBubble> getAssistantBubbles() {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                List<MessageBubble>[] result = new List[]{List.of()};
                SwingUtilities.invokeAndWait(() -> result[0] = getAssistantBubbles());
                return result[0];
            } catch (InterruptedException | InvocationTargetException ex) {
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

    private int calculateCurrentTokenCount() {
        List<MessageBubble> bubbles = getAllResponseBubbles();
        if (bubbles.isEmpty()) return 0;
        int total = 0;
        for (MessageBubble mb : bubbles) {
            String text = mb.getRawText();
            if (text != null && !text.isBlank()) {
                total += countWords(text);
            }
        }
        return total;
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
            Font f = comp.getFont();
            comp.setFont(f.deriveFont(Math.max(10f, f.getSize() - 1f)));
        }
        if (comp instanceof JEditorPane) {
            JEditorPane pane = (JEditorPane) comp;
            pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        }
        if (comp instanceof Container) {
            for (Component c : ((Container) comp).getComponents()) {
                reduceFontSize(c);
            }
        }
    }
    
    public void onProcessingChanged(boolean processing) {
        SwingUtilities.invokeLater(() -> {
            this.isProcessing = processing;
            String sessionId = Lookup.getDefault()
                    .lookup(SessionControl.class).getCurrentSessionId();
            inputArea.setEnabled(sessionId != null);
            if (processing) {
                maxTokenCountThisTurn = 0;
                displayedMessageId = null;
                displayedText = null;
                showSpinner();
            } else {
                stopTokenPolling();
                if (spinnerBar != null) {
                    spinnerBar.setVisible(false);
                }
                if (isVisible()) {
                    syncLatestBubble();
                }
            }
        });
    }

    private void syncLatestBubble() {
        List<MessageBubble> bubbles = getAssistantBubbles();
        if (bubbles.isEmpty()) return;
        
        if (isAutoTrackingLatest) {
            currentBubbleIndex = bubbles.size() - 1;
        }
        MessageBubble latest = bubbles.get(currentBubbleIndex);
        String id = latest.getMessageId() != null ? latest.getMessageId() : "";
        String text = latest.getRawText() != null ? latest.getRawText() : "";

        if (id.equals(displayedMessageId) && text.equals(displayedText)) {
            return;
        }

        displayedMessageId = id;
        displayedText = text;
        displayBubble(latest);
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    public void onStreamUpdate(ProcessedMessage msg) {
        if (msg != null && msg.messageType() == MessageType.error_response) {
            if (lastSentText != null && !lastSentText.isBlank()) {
                final String textToRestore = lastSentText;
                lastSentText = null;
                SwingUtilities.invokeLater(() -> {
                    if (inputArea.getText().isEmpty()) {
                        inputArea.setText(textToRestore);
                    }
                });
            }
        }
        SwingUtilities.invokeLater(() -> {
            updateTokenOverlay();
            if (isAutoTrackingLatest && isVisible()) {
                syncLatestBubble();
            }
        });
    }

    private void styleAsMiniBubble(MessageBubble mb) {
        mb.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        mb.setOpaque(false); // Let responsePane background show through margins
        if (mb.getLayout() instanceof GridBagLayout gb) {
            for (Component c : mb.getComponents()) {
                GridBagConstraints gbc = gb.getConstraints(c);
                gbc.insets = new Insets(0, 0, 0, 0);
                gbc.weightx = 1.0;
                gbc.weighty = 1.0;
                gbc.fill = GridBagConstraints.BOTH;
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
                        StringSelection selection = new StringSelection(code);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                        return;
                    }
                }
            }
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        }
    }
    
    private void updateResponsePane() {
        String sessionId = Lookup.getDefault().lookup(SessionControl.class).getCurrentSessionId();
        
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
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();
        Rectangle dialogRect = new Rectangle(x, y, w, h);
        for (GraphicsDevice screen : screens) {
            Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
            if (screenBounds.intersects(dialogRect)) {
                return true;
            }
        }
        return false;
    }

    private void restoreDividerLocation() {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        int savedInputHeight = prefs.getInt(PreferenceKeys.MINI_ASSISTANT_INPUT_HEIGHT, -1);
        if (savedInputHeight > 0 && splitPane != null && splitPane.getHeight() > 50) {
            int targetDiv = Math.max(30, splitPane.getHeight() - savedInputHeight - splitPane.getDividerSize());
            splitPane.setDividerLocation(targetDiv);
        }
    }

    private void saveBounds() {
        if (!isVisible()) return;
        Rectangle bounds = getBounds();
        if (bounds.width > 0 && bounds.height > 0) {
            Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
            prefs.putInt(PreferenceKeys.MINI_ASSISTANT_X, bounds.x);
            prefs.putInt(PreferenceKeys.MINI_ASSISTANT_Y, bounds.y);
            prefs.putInt(PreferenceKeys.MINI_ASSISTANT_WIDTH, bounds.width);
            prefs.putInt(PreferenceKeys.MINI_ASSISTANT_HEIGHT, bounds.height);
            if (splitPane != null && splitPane.getHeight() > 50 && splitPane.getDividerLocation() > 0) {
                int inputHeight = splitPane.getHeight() - splitPane.getDividerLocation() - splitPane.getDividerSize();
                if (inputHeight > 20) {
                    prefs.putInt(PreferenceKeys.MINI_ASSISTANT_INPUT_HEIGHT, inputHeight);
                }
            }
        }
    }

    @Override
    public void setVisible(boolean b) {
        if (!b && isVisible()) {
            saveBounds();
        }
        super.setVisible(b);
        if (b) {
            SwingUtilities.invokeLater(this::restoreDividerLocation);
        }
    }

    @Override
    public void dispose() {
        if (isVisible()) {
            saveBounds();
        }
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
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
        if (responsePane.getParent() instanceof JViewport vp) {
            vp.setBackground(theme.background());
            vp.setOpaque(true);
            if (vp.getParent() instanceof JScrollPane sp) {
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
                BorderFactory.createSoftBevelBorder(BevelBorder.LOWERED)
            ),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));

        Font f = ThemeManager.getFont();
        inputArea.setFont(f);

        applyTokenOverlayColors(isProcessing && maxTokenCountThisTurn > 0);

        SwingUtilities.updateComponentTreeUI(this);
    }
    
    private static class ScrollablePanel extends JPanel implements Scrollable {
        ScrollablePanel(LayoutManager layout) { super(layout); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { 
            if (getParent() instanceof JViewport vp) {
                return getPreferredSize().height < vp.getHeight();
            }
            return false;
        }
    }
}
