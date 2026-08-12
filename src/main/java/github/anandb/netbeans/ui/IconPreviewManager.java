package github.anandb.netbeans.ui;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Manages icon preview display, browse, and clear for user icon selection.
 */
// DSL-LEAF: not a controller — keeps the user-icon preview label in sync with
// the icon path text field via a DocumentListener. Migration target:
// IconPreviewSpec; the SvgIconLoader call stays imperative.
@NbBundle.Messages({
    "LBL_IconPreview_RightClickClear=Right-click to clear icon",
    "LBL_IconPreview_Clear=Clear",
    "LBL_IconPreview_SvgNoPreview=SVG (no preview)"
})
final class IconPreviewManager {

    private static final Logger LOG = Logger.from(IconPreviewManager.class);

    private final JLabel previewLabel;
    private final JTextField pathField;
    private final Runnable onChangeCallback;
    private Image rawImage;

    IconPreviewManager(JLabel previewLabel, JTextField pathField, Runnable onChangeCallback) {
        this.previewLabel = previewLabel;
        this.pathField = pathField;
        this.onChangeCallback = onChangeCallback;
        initPreviewLabel();
    }

    private void initPreviewLabel() {
        previewLabel.setPreferredSize(new Dimension(80, 80));
        previewLabel.setMinimumSize(new Dimension(32, 32));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        previewLabel.setToolTipText(Bundle.LBL_IconPreview_RightClickClear());
        previewLabel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (rawImage != null) {
                    scaleToLabel();
                }
            }
        });
        previewLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }

            private void showPopup(MouseEvent e) {
                if (pathField.getText().isEmpty()) return;
                JPopupMenu popup = new JPopupMenu();
                JMenuItem clearItem = new JMenuItem(Bundle.LBL_IconPreview_Clear());
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
            rawImage = null;
            previewLabel.setIcon(null);
            previewLabel.setText("");
            return;
        }
        File file = new File(path);
        if (!file.exists()) {
            rawImage = null;
            previewLabel.setIcon(null);
            previewLabel.setText("");
            return;
        }
        try {
            ImageIcon icon = new ImageIcon(path);
            if (icon.getIconWidth() > 0) {
                rawImage = icon.getImage();
                scaleToLabel();
                previewLabel.setText("");
            } else {
                rawImage = null;
                previewLabel.setIcon(null);
                previewLabel.setText("<html><center>" + Bundle.LBL_IconPreview_SvgNoPreview() + "</center></html>");
            }
        } catch (Exception e) {
            LOG.warn("Failed to update icon preview for: {0}", path, e);
            rawImage = null;
            previewLabel.setIcon(null);
            previewLabel.setText("?");
        }
    }

    private void scaleToLabel() {
        int w = previewLabel.getWidth();
        int h = previewLabel.getHeight();
        if (w < 1 || h < 1 || rawImage == null) return;
        previewLabel.setIcon(new ImageIcon(
            rawImage.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
    }
}
