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
 * Enter (send), Shift+Enter (newline), Alt+Up/Down (history navigation),
 * Ctrl+Z/Y (undo/redo), and autocomplete integration.
 */
public class InputHandler {

    private final PlaceholderTextArea inputArea;
    private final AutocompleteManager autocompleteManager;
    private final MessageSender messageSender;
    private final MessageHistory messageHistory;
    /** Cached so keyReleased doesn't hit Lookup on every keystroke. */
    private final ProcessControl processControl = Lookup.getDefault().lookup(ProcessControl.class);

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
                    SlashCommandInterceptor interceptor = processControl.getSlashCommandInterceptor();
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
                } else if ((e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0 && e.getKeyCode() == KeyEvent.VK_Z) {
                    e.consume();
                    inputArea.undo();
                } else if ((e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0 && e.getKeyCode() == KeyEvent.VK_Y) {
                    e.consume();
                    inputArea.redo();
                } else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_R) {
                    e.consume();
                    HistorySearchDialog.show(inputArea, messageHistory);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                autocompleteManager.handleKeyReleased(e);
                processControl.touchConnection();
            }
        });
    }
}
