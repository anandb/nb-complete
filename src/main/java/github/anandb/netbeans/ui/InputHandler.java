package github.anandb.netbeans.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import org.openide.util.Lookup;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.contract.SlashCommandCallback;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProcessService;

/**
 * Handles keyboard input for the chat input area: Tab (switch agent),
 * Enter (send), Shift+Enter (newline), Alt+Up/Down (history navigation),
 * Ctrl+Z/Y (undo/redo), and autocomplete integration.
 */
// DSL-CONTROLLER: not a view — keyboard input dispatch for the input area
// (Enter to send, Shift+Enter newline, Ctrl+Enter force-send, history
// navigation). The DSL binds these via InputMap/ActionMap; the dispatcher impl
// stays imperative.
public class InputHandler {

    private final PlaceholderTextArea inputArea;
    private final AutocompleteManager autocompleteManager;
    private final MessageSender messageSender;
    private final MessageHistory messageHistory;
    private final ProcessService processService = Lookup.getDefault().lookup(PlatformBridge.class).processService();

    public InputHandler(
            PlaceholderTextArea inputArea,
            AutocompleteManager autocompleteManager,
            MessageSender messageSender,
            MessageHistory messageHistory) {
        this.inputArea = inputArea;
        this.autocompleteManager = autocompleteManager;
        this.messageSender = messageSender;
        this.messageHistory = messageHistory;
        install();
    }

    private void install() {
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    if (autocompleteManager.isPopupVisible()) {
                        e.consume();
                        autocompleteManager.selectCommand();
                    } else {
                        e.consume();
                        SlashCommandInterceptor interceptor = processService.get().getSlashCommandInterceptor();
                        SlashCommandCallback cb = interceptor != null ? interceptor.getCallback() : null;
                        if (cb != null) {
                            cb.expandOptionsPanel();
                            cb.popupAgentCombo();
                        }
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (autocompleteManager.handleKeyPressed(e)) {
                        // handled by autocomplete
                    } else if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                        e.consume();
                        inputArea.insert("\n", inputArea.getCaretPosition());
                    } else {
                        e.consume();
                        messageSender.sendMessage();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP
                        && (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
                    if (autocompleteManager.isPopupVisible()) {
                        e.consume();
                    } else if (!messageHistory.isEmpty()) {
                        inputArea.setText(messageHistory.navigateUp(inputArea.getText()));
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN
                        && (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
                    if (autocompleteManager.isPopupVisible()) {
                        e.consume();
                    } else if (messageHistory.isNavigating()) {
                        inputArea.setText(messageHistory.navigateDown(inputArea.getText()));
                    }
                } else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_R) {
                    e.consume();
                    HistorySearchDialog.show(inputArea, messageHistory);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                autocompleteManager.handleKeyReleased(e);
                processService.get().touchConnection();
            }
        });
    }
}
