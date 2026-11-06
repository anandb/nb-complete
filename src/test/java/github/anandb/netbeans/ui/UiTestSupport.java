package github.anandb.netbeans.ui;

import java.io.File;

import org.junit.jupiter.api.Assumptions;

/**
 * Graphics prerequisite for the Swing layout assertions in this package.
 *
 * <p>The build forces {@code -Djava.awt.headless=true}, so these tests run on AWT's headless
 * toolkit and need font metrics and LAF defaults, not a window. The assumption therefore only
 * rejects a machine with no graphics stack at all: it accepts a desktop session
 * ({@code DISPLAY}, and {@code WAYLAND_DISPLAY} for a Wayland session where AWT still needs
 * XWayland) or an {@code xvfb-run} install, which is how a headless build obtains one.</p>
 */
public final class UiTestSupport {

    private UiTestSupport() {
    }

    /** Skips unless a desktop session or an Xvfb launcher is available. */
    public static void assumeGraphicsStack() {
        Assumptions.assumeTrue(hasDisplay() || xvfbRunAvailable(), () ->
                "Skipping layout assertion: no DISPLAY/WAYLAND_DISPLAY and no xvfb-run on PATH. "
                        + "Install xvfb or run the suite under xvfb-run -a.");
    }

    static boolean hasDisplay() {
        return isSet(System.getenv("DISPLAY")) || isSet(System.getenv("WAYLAND_DISPLAY"));
    }

    /** True when {@code xvfb-run} is installed and executable. */
    static boolean xvfbRunAvailable() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isEmpty()) {
                continue;
            }
            File candidate = new File(dir, "xvfb-run");
            if (candidate.isFile() && candidate.canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
