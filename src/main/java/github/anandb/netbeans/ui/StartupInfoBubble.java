package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.openide.util.NbBundle;

/**
 * Compact info bubble displayed when a session starts. Shows version,
 * context files, and extensions in a styled card format.
 *
 * <pre>
 * ┌─ pi v0.84.4 ─────────────────────────┐
 * │  📄 AGENTS.md                         │
 * │  📦 pi-mcp-adapter (index.ts)         │
 * └────────────────────────────────────────┘
 * </pre>
 */
class StartupInfoBubble extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Pattern VERSION_PATTERN = Pattern.compile("^pi v(.+)$", Pattern.MULTILINE);
    private static final Pattern CONTEXT_PATTERN = Pattern.compile("## Context\\s*\n((?:- .+\n?)+)", Pattern.MULTILINE);
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("## Extensions\\s*\n((?:- .+\n?(?:  - .+\n?)*)*)", Pattern.MULTILINE);

    StartupInfoBubble(String text) {
        setLayout(new BorderLayout());
        setAlignmentY(Component.CENTER_ALIGNMENT);
        setOpaque(false);
        setBorder(new EmptyBorder(4, 8, 4, 8));

        ColorTheme theme = ThemeManager.getCurrentTheme();
        JPanel card = UIUtils.createBubbleContentPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(8, 12, 8, 12));

        // Parse version
        String version = extractVersion(text);
        if (version != null) {
            JLabel versionLabel = new JLabel("pi v" + version);
            versionLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, ThemeManager.getFont().getSize() + 1f));
            versionLabel.setForeground(theme.foreground());
            versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(versionLabel);
            card.add(Box.createVerticalStrut(6));
        }

        // Parse context files
        List<String> contextFiles = extractContextFiles(text);
        if (!contextFiles.isEmpty()) {
            JLabel contextHeader = new JLabel("\uD83D\uDCC4 " + NbBundle.getMessage(StartupInfoBubble.class, "StartupInfoBubble.Context"));
            contextHeader.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() - 1f));
            contextHeader.setForeground(theme.secondary2());
            contextHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(contextHeader);

            for (String file : contextFiles) {
                JLabel fileLabel = new JLabel("  \u2022 " + shortenPath(file));
                fileLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() - 1f));
                fileLabel.setForeground(theme.foreground());
                fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(fileLabel);
            }
            card.add(Box.createVerticalStrut(4));
        }

        // Parse extensions
        List<String[]> extensions = extractExtensions(text);
        if (!extensions.isEmpty()) {
            JLabel extHeader = new JLabel("\uD83D\uDCE6 " + NbBundle.getMessage(StartupInfoBubble.class, "StartupInfoBubble.Extensions"));
            extHeader.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() - 1f));
            extHeader.setForeground(theme.secondary2());
            extHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(extHeader);

            for (String[] ext : extensions) {
                String label = "  \u2022 " + ext[0];
                if (ext.length > 1 && ext[1] != null && !ext[1].isEmpty()) {
                    label += " (" + ext[1] + ")";
                }
                JLabel extLabel = new JLabel(label);
                extLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() - 1f));
                extLabel.setForeground(theme.foreground());
                extLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(extLabel);
            }
        }

        add(card, BorderLayout.CENTER);
        setAlignmentX(LEFT_ALIGNMENT);
    }

    private static String extractVersion(String text) {
        Matcher m = VERSION_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static List<String> extractContextFiles(String text) {
        List<String> files = new ArrayList<>();
        Matcher m = CONTEXT_PATTERN.matcher(text);
        if (m.find()) {
            String block = m.group(1);
            for (String line : block.split("\n")) {
                line = line.trim();
                if (line.startsWith("- ")) {
                    files.add(line.substring(2).trim());
                }
            }
        }
        return files;
    }

    private static List<String[]> extractExtensions(String text) {
        List<String[]> extensions = new ArrayList<>();
        Matcher m = EXTENSION_PATTERN.matcher(text);
        if (m.find()) {
            String block = m.group(1);
            String currentName = null;
            List<String> subItems = new ArrayList<>();

            for (String line : block.split("\n")) {
                if (line.startsWith("- ")) {
                    if (currentName != null) {
                        extensions.add(new String[]{currentName, String.join(", ", subItems)});
                    }
                    currentName = line.substring(2).trim();
                    subItems = new ArrayList<>();
                } else if (line.trim().startsWith("- ") && currentName != null) {
                    subItems.add(line.trim().substring(2).trim());
                }
            }
            if (currentName != null) {
                extensions.add(new String[]{currentName, String.join(", ", subItems)});
            }
        }
        return extensions;
    }

    /** Shortens a path to show just the filename or relative portion. */
    private static String shortenPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        // Just show the filename
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    /** Returns true if the text matches the startup message pattern. */
    static boolean isStartupMessage(String text) {
        if (text == null || !text.startsWith("pi v")) {
            return false;
        }
        return text.contains("## Context") && text.contains("## Extensions");
    }
}
