package github.anandb.netbeans.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.Timer;

import github.anandb.netbeans.support.ToolDataExtractor;
import github.anandb.netbeans.support.Logger;

// DSL-CONTROLLER: not a view — copyRevertTimer handles the click-to-copy → revert-icon
// animation on assistant bubbles. Stays imperative; DSL wraps the leaf it drives.
public class MessageCopyMouseAdapter extends MouseAdapter {
    private static final Logger LOG = Logger.from(MessageCopyMouseAdapter.class);

    private final JLabel iconLabel;
    private final Icon userIcon;
    private final Icon copyIcon;
    private final Icon checkIcon;
    private final String messageId;
    private final String type;
    private final WeakReference<MessageBubble> bubble;

    public MessageCopyMouseAdapter(JLabel iconLabel, Icon userIcon, Icon copyIcon, Icon checkIcon,
                                  String messageId, String type, MessageBubble bubble) {
        this.iconLabel = iconLabel;
        this.userIcon = userIcon;
        this.copyIcon = copyIcon;
        this.checkIcon = checkIcon;
        this.messageId = messageId;
        this.type = type;
        this.bubble = new WeakReference<>(bubble);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        iconLabel.setIcon(copyIcon);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        iconLabel.setIcon(userIcon);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        MessageBubble b = bubble.get();
        if (b == null) {
            LOG.warn("Bubble was garbage collected before copy, msgId={0}, type={1}", new Object[]{messageId, type});
            return;
        }
        
        String textToCopy = ToolDataExtractor.stripMetadata(b.getRawText());

        if (textToCopy.isEmpty()) {
            LOG.warn("No text to copy, msgId={0}, type={1}", new Object[]{messageId, type});
            return;
        }

        AssistantTopComponent.copyToInput(textToCopy);

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(textToCopy), null);
        } catch (Exception ex) {
            LOG.warn("Clipboard fail: {0}", ex.getMessage());
        }
        iconLabel.setIcon(checkIcon);
        Timer timer = new Timer(1500, ev -> {
            if (!iconLabel.getBounds().contains(e.getPoint())) {
                iconLabel.setIcon(userIcon);
            } else {
                iconLabel.setIcon(copyIcon);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }
}
