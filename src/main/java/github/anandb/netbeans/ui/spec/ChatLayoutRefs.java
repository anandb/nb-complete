package github.anandb.netbeans.ui.spec;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.ui.PlaceholderTextArea;

/**
 * Immutable bundle of references to the Swing components that make up the chat
 * toolbar + bottom input panel. Returned by spec builders
 * ({@link ChatLayoutSpec#build}) so the caller ({@code AssistantTopComponent})
 * can wire listeners / controllers without holding duplicate field copies.
 * <p>
 * This is the DSL-ready signature: a future swingtree migration returns this
 * record from a {@code UI.of(this).add(...)} tree; the record's shape does not
 * change, only the body of {@code build()} becomes declarative.
 * <p>
 * <b>Not Swing-free</b> — this is a UI-layer type (holds JComponent refs).
 * Swing-free view-state lives in {@code ui/vm/}.
 */
public record ChatLayoutRefs(
        JPanel header,
        JPanel rightStatusPanel,
        JComboBox<SessionItem> sessionDropdown,
        JButton hideBtn,
        JButton showHiddenBtn,
        JButton newSessionBtn,
        JButton renameSessionBtn,
        JButton toggleBlocksBtn,
        JButton keepBtn,
        JButton filterBtn,
        JButton helpBtn,
        JButton toggleOptionsBtn,
        JButton restartServerBtn,
        JButton refreshBtn,
        JButton exportBtn,
        JButton sendBtn,
        JButton stopBtn,
        JLabel statusLabel,
        JLabel versionLabel,
        JLabel cwdLabel,
        PlaceholderTextArea inputArea,
        JScrollPane inputScrollPane
) {
}
