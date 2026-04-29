package github.anandb.netbeans.ui;

import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import github.anandb.netbeans.manager.ToolMetadataExtractor;
import github.anandb.netbeans.support.Logger;

public class MessageCopyMouseAdapter extends MouseAdapter {
    private static final Logger LOG = new Logger(MessageCopyMouseAdapter.class);

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
        String textToCopy = ToolMetadataExtractor.stripMetadata(
            bubble.get().getRawText()
        );

        if (textToCopy.isEmpty()) {
            LOG.warn("No text to copy, msgId={0}, type={1}", new Object[]{messageId, type});
            return;
        }

        AssistantTopComponent.copyToInput(textToCopy);

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(textToCopy), null);
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
