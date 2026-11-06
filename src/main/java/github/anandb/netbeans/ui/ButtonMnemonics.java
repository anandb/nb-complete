package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Applies a button mnemonic and makes {@code Alt + letter} — the stroke the UI documents —
 * answer on every platform.
 * <p>
 * {@link JButton#setMnemonic(int)} binds {@code BasicLookAndFeel.getFocusAcceleratorKeyMask()},
 * which is Alt on Windows/Linux but <b>Ctrl+Alt</b> on macOS: {@code LWCToolkit} overrides the
 * mask because Option composes characters there (Option+A types "å"). A Mac user pressing the
 * documented Alt+A therefore hit nothing. The extra binding answers the documented stroke
 * regardless of the platform mask; on Windows/Linux it replaces the identical LAF binding.
 */
public final class ButtonMnemonics {

    private static final String ACTION_KEY = "mnemonicAlt";

    private ButtonMnemonics() {
    }

    /** Sets {@code mnemonic} on {@code button} and binds {@code Alt+mnemonic} to a click. */
    public static void apply(JButton button, char mnemonic) {
        if (mnemonic == 0) {
            return;
        }
        button.setMnemonic(mnemonic);
        int keyCode = KeyEvent.getExtendedKeyCodeForChar(mnemonic);
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return;
        }
        button.getActionMap().put(ACTION_KEY, clickAction(button));
        InputMap inputMap = button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke(keyCode, InputEvent.ALT_DOWN_MASK), ACTION_KEY);
    }

    private static Action clickAction(JButton button) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                button.doClick();
            }
        };
    }
}
