package github.anandb.netbeans.tasks;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;

import org.openide.util.ImageUtilities;

/**
 * The Beanbot Tasks connector/repository icon. Loaded once and cached because
 * the Tasks Dashboard renders it via {@code ImageUtilities.image2Icon}, which
 * throws on a null image.
 */
public final class TasksIcon {

    /** On-Disk resource path for the connector icon. */
    public static final String PATH = "github/anandb/netbeans/tasks/icons/tasks.png";

    private static final Image ICON =
        ImageUtilities.loadImage(PATH, false);

    private TasksIcon() {
    }

    /** The connector icon (never null; falls back to a generated image). */
    public static Image getIcon() {
        return ICON != null ? ICON : placeholder();
    }

    private static Image placeholder() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(52, 120, 246));
            g.fillRect(0, 0, 16, 16);
        } finally {
            g.dispose();
        }
        return img;
    }
}