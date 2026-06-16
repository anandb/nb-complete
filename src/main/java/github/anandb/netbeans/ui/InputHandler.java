package github.anandb.netbeans.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import github.anandb.netbeans.contract.ProcessControl;
import org.openide.util.Lookup;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.contract.SlashCommandCallback;

/**
 * Handles keyboard input for the chat input area: Tab (switch agent),
 * Enter (send), Shift+Enter (newline), Up/Down (history navigation),
 * Ctrl+Z/Y (undo/redo), and autocomplete integration.
 */
public class InputHandler {

    private final PlaceholderTextArea inputArea;
    private final AutocompleteManager autocompleteManager;
    private final MessageSender messageSender;
    private final MessageHistory messageHistory;

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
                    e.consume();
                    SlashCommandInterceptor interceptor = Lookup.getDefault().lookup(ProcessControl.class).getSlashCommandInterceptor();
                    SlashCommandCallback cb = interceptor != null ? interceptor.getCallback() : null;
                    if (cb != null) {
                        cb.expandOptionsPanel();
                        cb.popupAgentCombo();
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
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (autocompleteManager.isPopupVisible()) {
                        e.consume();
                    } else if (inputArea.getCaretPosition() == 0 && !messageHistory.isEmpty()) {
                        inputArea.setText(messageHistory.navigateUp(inputArea.getText()));
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (autocompleteManager.isPopupVisible()) {
                        e.consume();
                    } else if (inputArea.getCaretPosition() == inputArea.getText().length() && messageHistory.isNavigating()) {
                        inputArea.setText(messageHistory.navigateDown(inputArea.getText()));
                    }
                } else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_Z) {
                    e.consume();
                    inputArea.undo();
                } else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_Y) {
                    e.consume();
                    inputArea.redo();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                autocompleteManager.handleKeyReleased(e);
                Lookup.getDefault().lookup(ProcessControl.class).touchConnection();
            }
        });
    }
}
