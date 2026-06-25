package github.anandb.netbeans.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.openide.util.spi.SVGLoader;

/**
 * Test-only SVG loader that returns a tiny transparent icon.
 * Prevents "No SVG loader available" warnings in test environments
 * where the NetBeans SVG loader SPI is not registered.
 */
public class MockSvgLoader implements SVGLoader {

    @Override
    public Icon loadIcon(URL url) throws IOException {
        BufferedImage bi = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.dispose();
        return new ImageIcon(bi);
    }
}
