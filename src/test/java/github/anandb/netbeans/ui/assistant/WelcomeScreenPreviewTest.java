package github.anandb.netbeans.ui.assistant;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.ui.components.ScrollablePanel;

/** Manual preview: renders the welcome screen to target/welcome-preview.png. */
class WelcomeScreenPreviewTest {

    @Test
    void renderPreview() throws Exception {
        JPanel container = new ScrollablePanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        JScrollPane sp = new JScrollPane(container);
        sp.setSize(760, 460);

        SwingUtilities.invokeAndWait(() ->
            WelcomeScreen.show(container, List.of(), id -> { }, () -> { }, prompt -> { }));
        SwingUtilities.invokeAndWait(() -> layout(sp));

        BufferedImage img = new BufferedImage(760, 460, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            SwingUtilities.invokeAndWait(() -> sp.paint(g));
        } finally {
            g.dispose();
        }
        ImageIO.write(img, "png", new File("target/welcome-preview.png"));
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }
}
