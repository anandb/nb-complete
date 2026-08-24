package github.anandb.netbeans.tasks;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Locale;

import org.netbeans.modules.bugtracking.spi.IssuePriorityInfo;
import org.netbeans.modules.bugtracking.spi.IssuePriorityProvider;

/**
 * {@link IssuePriorityProvider} mapping the {@code priority} letter ({@code A}–
 * {@code Z}) to the Tasks Dashboard priority column. {@code A}–{@code M} render
 * red (high urgency), {@code N}–{@code Z} gray. Unknown or blank values fall
 * back to {@code N}.
 *
 * <p>Each priority supplies a locally-generated icon so the framework never
 * falls back to {@code IssuePrioritySupport.getDefaultIcon()}: that class
 * eagerly loads images via {@code ImageUtilities.loadImage(..., true)} in a
 * static initializer and resolving it per task row on the EDT re-triggers the
 * image-loading freeze.</p>
 */
public final class TaskPriorityProvider implements IssuePriorityProvider<TaskIssue> {

    private static final IconHolder HIGH = new IconHolder(new Color(220, 60, 60));
    private static final IconHolder NORMAL = new IconHolder(new Color(120, 120, 120));

    @Override
    public String getPriorityID(TaskIssue i) {
        String p = i.getRecord() == null ? null : i.getRecord().priority();
        if (p != null && p.trim().toUpperCase(Locale.ROOT).matches("[A-Z]")) {
            return p.trim().toUpperCase(Locale.ROOT);
        }
        return "N";
    }

    @Override
    public IssuePriorityInfo[] getPriorityInfos() {
        IssuePriorityInfo[] infos = new IssuePriorityInfo[26];
        for (int n = 0; n < 26; n++) {
            String letter = String.valueOf((char) ('A' + n));
            boolean high = n < 13; // A–M
            infos[n] = new IssuePriorityInfo(letter, letter, (high ? HIGH : NORMAL).image());
        }
        return infos;
    }

    /** Lazily builds a small colored square icon (avoids any image loading on EDT). */
    private static final class IconHolder {
        private final Color color;
        private volatile Image image;

        IconHolder(Color color) {
            this.color = color;
        }

        Image image() {
            Image img = image;
            if (img == null) {
                synchronized (this) {
                    img = image;
                    if (img == null) {
                        image = img = build();
                    }
                }
            }
            return img;
        }

        private Image build() {
            BufferedImage b = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = b.createGraphics();
            try {
                g.setColor(color);
                g.fillOval(2, 2, 12, 12);
            } finally {
                g.dispose();
            }
            return b;
        }
    }
}