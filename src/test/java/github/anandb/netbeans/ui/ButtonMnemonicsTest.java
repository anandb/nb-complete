package github.anandb.netbeans.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;

class ButtonMnemonicsTest {

    @Test
    void altStrokeAnswersTheButtonOnEveryPlatform() {
        JButton button = new JButton("Yes, allow all edits during this session");

        ButtonMnemonics.apply(button, 'A');

        assertEquals('A', button.getMnemonic());
        InputMap inputMap = button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        Object actionKey = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK));
        assertNotNull(actionKey, "Alt+A must be bound: macOS binds setMnemonic to Ctrl+Alt");
        assertNotNull(button.getActionMap().get(actionKey));
    }

    @Test
    void altStrokeClicksTheButtonOnce() {
        JButton button = new JButton("No");
        AtomicInteger clicks = new AtomicInteger();
        button.addActionListener(e -> clicks.incrementAndGet());

        ButtonMnemonics.apply(button, 'R');
        Object actionKey = button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK));
        button.getActionMap().get(actionKey)
                .actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, "R"));

        assertEquals(1, clicks.get());
    }

    @Test
    void unknownOptionGetsNoMnemonic() {
        JButton button = new JButton("Something");

        ButtonMnemonics.apply(button, (char) 0);

        assertEquals(0, button.getMnemonic());
        assertNull(button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK)));
    }
}
