package github.anandb.netbeans.support;

import java.util.List;
import java.util.Locale;

import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.Harness;

/**
 * Maps the configured executable and the stored harness preference to the harness the
 * session will actually use. Pure and preference-free so the Options panel and its tests
 * share one rule set.
 *
 * <p>Runtime behavior this mirrors: {@code ServerProcessLifecycle.initializeProtocol()}
 * resolves {@code HarnessCatalog.byId(stored)}, then falls back to
 * {@code HarnessCatalog.byBinaryName(BinaryResolver.resolveBinaryName())}, then
 * {@link HarnessCatalog#UNKNOWN}.
 */
public final class HarnessSelection {

    /** How the configured executable reaches the harness. */
    public enum Launcher {
        /** The executable is the harness itself. */
        DIRECT,
        /** The executable is a container runtime; the harness runs inside the image. */
        CONTAINER
    }

    /**
     * The Options-panel choice. {@code auto} means "derive the id from the binary name";
     * otherwise {@code harness} is the user's explicit answer for this path.
     */
    public record Selection(Harness harness, boolean auto) {
    }

    /** Container runtimes whose basename can never identify the harness inside them. */
    private static final List<String> CONTAINER_LAUNCHERS = List.of("docker", "podman", "nerdctl", "finch");

    private HarnessSelection() {
    }

    /** Classifies a binary basename as a direct harness or a container runtime. */
    public static Launcher launcherKind(String binaryName) {
        return CONTAINER_LAUNCHERS.contains(normalize(binaryName)) ? Launcher.CONTAINER : Launcher.DIRECT;
    }

    /** Harness detected from the binary name, or {@link HarnessCatalog#UNKNOWN}. */
    public static Harness detect(String binaryName) {
        return binaryName == null ? HarnessCatalog.UNKNOWN : HarnessCatalog.byBinaryName(binaryName);
    }

    /**
     * Initial selection for a stored id and the current binary name. An id that the
     * binary name cannot explain is a deliberate choice for a custom path or container
     * launcher and is kept; otherwise the state is Auto-detect.
     */
    public static Selection resolve(String storedHarnessId, String binaryName) {
        Harness detected = detect(binaryName);
        Harness stored = HarnessCatalog.byId(storedHarnessId);
        if (stored != HarnessCatalog.UNKNOWN && stored != detected) {
            return new Selection(stored, false);
        }
        return new Selection(detected, true);
    }

    /** Preference value to store for this choice; empty string when Auto cannot detect. */
    public static String harnessId(Selection selection, String binaryName) {
        if (selection == null) {
            return harnessId(resolve(null, binaryName), binaryName);
        }
        if (!selection.auto()) {
            return selection.harness().id();
        }
        Harness detected = detect(binaryName);
        return detected == HarnessCatalog.UNKNOWN ? "" : detected.id();
    }

    /** Display name to store alongside {@link #harnessId}; empty when nothing is known. */
    public static String displayName(Selection selection, String binaryName) {
        if (selection == null) {
            return displayName(resolve(null, binaryName), binaryName);
        }
        if (!selection.auto()) {
            return selection.harness().displayName();
        }
        Harness detected = detect(binaryName);
        return detected == HarnessCatalog.UNKNOWN ? "" : detected.displayName();
    }

    /** Lowercase basename without a Windows executable extension, or {@code ""}. */
    private static String normalize(String binaryName) {
        if (binaryName == null) {
            return "";
        }
        String name = binaryName.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : List.of(".exe", ".cmd", ".bat")) {
            if (lower.endsWith(ext)) {
                return lower.substring(0, lower.length() - ext.length());
            }
        }
        return lower;
    }
}
