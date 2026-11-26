package github.anandb.netbeans.ui.chat;

import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.model.Message;

/**
 * Applies message-type visibility filters to the chat thread panel.
 * Extracted from ChatThreadPanel to reduce class size.
 */
public final class TypeFilterApplier {

    private TypeFilterApplier() {}

    /**
     * Re-renders messages or toggles bubble visibility based on current filter state.
     *
     * @param cachedMessages the cached full message list (nullable)
     * @param setMessages    callback to re-render from the full list
     * @param messagesContainer the messages container panel
     */
    public static void apply(List<Message> cachedMessages, Consumer<List<Message>> setMessages, JPanel messagesContainer) {
        if (cachedMessages != null && !cachedMessages.isEmpty()) {
            setMessages.accept(cachedMessages);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Component[] comps = messagesContainer.getComponents();
            for (int i = 0; i < comps.length; i++) {
                if (comps[i] instanceof MessageBubble bubble) {
                    if (Boolean.TRUE.equals(bubble.getClientProperty("nb-complete.combined"))) {
                        int shown = bubble.applyCompositeFilter();
                        bubble.setVisible(shown > 0);
                        if (i + 1 < comps.length) comps[i + 1].setVisible(shown > 0);
                        if (shown > 0) {
                            bubble.revalidate();
                        }
                        continue;
                    }
                    boolean visible = !MessageFilterManager.isTypeHidden(bubble.getRole());
                    boolean wasHidden = !comps[i].isVisible();
                    comps[i].setVisible(visible);
                    if (i + 1 < comps.length) {
                        comps[i + 1].setVisible(visible);
                    }
                    if (wasHidden && visible) {
                        comps[i].revalidate();
                    }
                }
            }
            messagesContainer.revalidate();
        });
    }
}
