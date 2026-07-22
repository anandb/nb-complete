package github.anandb.netbeans.ui;

import java.awt.Dimension;
import java.awt.Image;
import java.io.File;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import github.anandb.netbeans.support.Logger;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Manages icon preview display, browse, and clear for user icon selection.
 */
// DSL-LEAF: not a controller — keeps the user-icon preview label in sync with
// the icon path text field via a DocumentListener. Migration target:
// IconPreviewSpec; the SvgIconLoader call stays imperative.
final class IconPreviewManager {

    private static final Logger LOG = Logger.from(IconPreviewManager.class);

    private final JLabel previewLabel;
    private final JTextField pathField;
    private final Runnable onChangeCallback;

    IconPreviewManager(JLabel previewLabel, JTextField pathField, Runnable onChangeCallback) {
        this.previewLabel = previewLabel;
        this.pathField = pathField;
        this.onChangeCallback = onChangeCallback;
        initPreviewLabel();
    }

    private void initPreviewLabel() {
        previewLabel.setPreferredSize(new Dimension(100, 100));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setToolTipText("Right-click to clear icon");
        previewLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }

            private void showPopup(MouseEvent e) {
                if (pathField.getText().isEmpty()) return;
                JPopupMenu popup = new JPopupMenu();
                JMenuItem clearItem = new JMenuItem("Clear");
                clearItem.addActionListener(evt -> {
                    pathField.setText("");
                    updatePreview("");
                    onChangeCallback.run();
                });
                popup.add(clearItem);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    void updatePreview(String path) {
        if (path == null || path.isEmpty()) {
            previewLabel.setIcon(null);
            previewLabel.setText("");
            return;
        }
        File file = new File(path);
        if (!file.exists()) {
            previewLabel.setIcon(null);
            previewLabel.setText("");
            return;
        }
        try {
            ImageIcon icon = new ImageIcon(path);
            if (icon.getIconWidth() > 0) {
                previewLabel.setIcon(new ImageIcon(
                    icon.getImage().getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
                previewLabel.setText("");
            } else {
                // SVG files are not supported as user icons
                previewLabel.setIcon(null);
                previewLabel.setText("SVG (no preview)");
            }
        } catch (Exception e) {
            LOG.warn("Failed to update icon preview for: {0}", path, e);
            previewLabel.setIcon(null);
            previewLabel.setText("?");
        }
    }
}
