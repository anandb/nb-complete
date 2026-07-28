package github.anandb.netbeans.ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.FlowLayout;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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

import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.Utilities;
import com.fasterxml.jackson.databind.JsonNode;
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

    // Permission panel components
    private JPanel miniPermissionPanel;
    private JLabel miniPermissionLabel;
    private JLabel miniContextLabel;
    private JPanel miniPermissionButtons;
    private CompletableFuture<String> activePermissionFuture;

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
        
        updateOverlayText(true);
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                updateOverlayText(true);
            }
            @Override
            public void windowLostFocus(WindowEvent e) {
                updateOverlayText(false);
            }
        });
        
        miniPermissionPanel = new JPanel();
        miniPermissionPanel.setLayout(new BoxLayout(miniPermissionPanel, BoxLayout.Y_AXIS));
        miniPermissionPanel.setVisible(false);
        miniPermissionPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0, 0, 0, 0))); // updated in theme
        
        JPanel labelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        labelRow.setOpaque(false);
        miniPermissionLabel = new JLabel();
        miniContextLabel = new JLabel();
        miniContextLabel.setForeground(ThemeManager.getCurrentTheme().mutedForeground());
        miniContextLabel.setFont(IconResourceManager.getMonospaceFont());
        labelRow.add(miniPermissionLabel);
        labelRow.add(miniContextLabel);
        
        miniPermissionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        miniPermissionButtons.setOpaque(false);
        
        miniPermissionPanel.add(labelRow);
        miniPermissionPanel.add(miniPermissionButtons);
        
        add(splitPane, BorderLayout.CENTER);
        add(miniPermissionPanel, BorderLayout.SOUTH);
        
        applyTheme();
    }

    private void updateOverlayText(boolean isFocused) {
        boolean isMac = Utilities.isMac();
        String toggleAction = ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleAssistantAction");
        if (toggleAction == null || toggleAction.isEmpty()) toggleAction = isMac ? "Cmd+L" : "Ctrl+L";
        
        String miniToggleAction = ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleMiniAssistantAction");
        if (miniToggleAction == null || miniToggleAction.isEmpty()) miniToggleAction = isMac ? "Cmd+Alt+L" : "Ctrl+Alt+L";
        
        String scrollAction = isMac ? "Cmd+Left/Right: scroll" : "PgUp/PgDn: scroll";
        String copyAction = isMac ? "Cmd+C: copy" : "Ctrl+C: copy";
        
        String focusText = isFocused ? "Focus Editor" : "Focus Assistant";
        
        inputArea.setOverlayText("Esc: close | " + toggleAction + ": Main Assistant Panel | " + miniToggleAction + ": " + focusText + " | "
            + scrollAction + " | " + copyAction + " | Enter: send");
        inputArea.repaint();
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
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.isDark() ? new Color(60, 60, 60) : new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
            ),
            inputArea.getBorder()
        ));
        
        if (miniPermissionPanel != null) {
            miniPermissionPanel.setBackground(theme.permissionBg());
            miniPermissionPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.permissionAccent()));
            miniPermissionLabel.setForeground(theme.permissionTitle());
            miniPermissionLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        }
        
        Font f = ThemeManager.getFont();
        inputArea.setFont(f);

        applyTokenOverlayColors(isProcessing && maxTokenCountThisTurn > 0);

        SwingUtilities.updateComponentTreeUI(this);
    }
    
    public void showPermissionRequest(
            String prompt, JsonNode options, CompletableFuture<String> responseFuture, JsonNode toolCall,
            List<github.anandb.netbeans.support.ToolCallDiffParser.FileChange> fileChanges) {
        SwingUtilities.invokeLater(() -> {
            this.activePermissionFuture = responseFuture;
            
            String toolName = "tool";
            if (toolCall != null) {
                if (toolCall.has("kind") && !toolCall.get("kind").asText().isEmpty()) {
                    toolName = toolCall.get("kind").asText();
                } else if (toolCall.has("title")) {
                    toolName = toolCall.get("title").asText();
                } else if (toolCall.has("name")) {
                    toolName = toolCall.get("name").asText();
                }
            }
            
            miniPermissionLabel.setText("Permission required: " + toolName);
            miniPermissionLabel.setIcon(ThemeManager.getIcon("shield.svg", 16));
            
            String contextStr = github.anandb.netbeans.support.ToolContextExtractor.extractToolContext(toolCall, 64);
            if (contextStr == null) contextStr = "";
            
            boolean requiresDiff = "edit".equals(toolName) 
                    || "replace_file_content".equals(toolName) 
                    || "multi_replace_file_content".equals(toolName);
            if (requiresDiff && (fileChanges == null || fileChanges.isEmpty())) {
                if (!contextStr.isEmpty()) contextStr += " - ";
                contextStr += "Unable to parse diff preview.";
            }
            
            if (!contextStr.isEmpty()) {
                miniContextLabel.setText(contextStr);
                miniContextLabel.setVisible(true);
            } else {
                miniContextLabel.setVisible(false);
            }
            
            miniPermissionButtons.removeAll();
            ColorTheme theme = ThemeManager.getCurrentTheme();

            if (fileChanges != null && !fileChanges.isEmpty()) {
                JButton showDiffBtn = new JButton(Bundle.BTN_ShowDiff());
                showDiffBtn.setFocusPainted(false);
                showDiffBtn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(theme.bubbleBorder(), 1),
                        BorderFactory.createEmptyBorder(4, 12, 4, 12)));
                showDiffBtn.addActionListener(e -> {
                    PermissionRequestPanel.openDiffView(fileChanges);
                });
                miniPermissionButtons.add(showDiffBtn);
            }
            
            if (options != null && options.isArray() && options.size() > 0) {
                for (JsonNode opt : options) {
                    String optionId = opt.has("optionId") ? opt.get("optionId").asText() : "";
                    String name = opt.has("name") ? opt.get("name").asText() : optionId;
                    String kind = opt.has("kind") ? opt.get("kind").asText() : "";
                    
                    JButton btn = new JButton(name);
                    btn.setFocusPainted(false);
                    if (kind.contains("allow")) {
                        btn.setForeground(theme.permissionGrantFg());
                        btn.setBackground(theme.permissionGrantBg());
                        btn.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(theme.permissionGrantBorder(), 1),
                                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
                    } else if (kind.contains("reject") || kind.contains("deny")) {
                        btn.setForeground(theme.permissionDenyFg());
                        btn.setBackground(theme.permissionDenyBg());
                        btn.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(theme.permissionDenyBorder(), 1),
                                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
                    } else {
                        btn.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(theme.bubbleBorder(), 1),
                                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
                    }
                    
                    btn.addActionListener(e -> {
                        if (activePermissionFuture != null) {
                            activePermissionFuture.complete(optionId);
                            AssistantTopComponent tc = AssistantTopComponent.findInstance();
                            if (tc != null) {
                                tc.getChatThreadPanel().addPermissionResult(name, kind.contains("allow"));
                            }
                        }
                    });
                    miniPermissionButtons.add(btn);
                }
            } else {
                JButton denyBtn = new JButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_Deny"));
                denyBtn.setFocusPainted(false);
                denyBtn.setForeground(theme.permissionDenyFg());
                denyBtn.setBackground(theme.permissionDenyBg());
                denyBtn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(theme.permissionDenyBorder(), 1),
                        BorderFactory.createEmptyBorder(4, 12, 4, 12)));
                denyBtn.addActionListener(e -> {
                    if (activePermissionFuture != null) {
                        activePermissionFuture.complete("reject");
                        AssistantTopComponent tc = AssistantTopComponent.findInstance();
                        if (tc != null) {
                            tc.getChatThreadPanel().addPermissionResult(NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionDenied"), false);
                        }
                    }
                });
                
                JButton allowBtn = new JButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_Allow"));
                allowBtn.setFocusPainted(false);
                allowBtn.setForeground(theme.permissionGrantFg());
                allowBtn.setBackground(theme.permissionGrantBg());
                allowBtn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(theme.permissionGrantBorder(), 1),
                        BorderFactory.createEmptyBorder(4, 12, 4, 12)));
                allowBtn.addActionListener(e -> {
                    if (activePermissionFuture != null) {
                        activePermissionFuture.complete("allow");
                        AssistantTopComponent tc = AssistantTopComponent.findInstance();
                        if (tc != null) {
                            tc.getChatThreadPanel().addPermissionResult(NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionGranted"), true);
                        }
                    }
                });
                
                miniPermissionButtons.add(denyBtn);
                miniPermissionButtons.add(allowBtn);
            }
            
            miniPermissionPanel.setVisible(true);
            revalidate();
            repaint();
        });
    }

    public void hidePermissionRequest() {
        SwingUtilities.invokeLater(() -> {
            activePermissionFuture = null;
            miniPermissionPanel.setVisible(false);
            revalidate();
            repaint();
        });
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
