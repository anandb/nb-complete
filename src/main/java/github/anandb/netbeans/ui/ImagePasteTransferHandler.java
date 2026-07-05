package github.anandb.netbeans.ui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;
import javax.swing.text.JTextComponent;

import org.openide.util.NbBundle;

import github.anandb.netbeans.model.AttachedFile;
import github.anandb.netbeans.support.ImagePasteIoProcessor;

// DSL-CONTROLLER: not a view — image paste background I/O thread pool + EDT
// bridge to AttachmentManager. Stays imperative; the input area + preview
// components it touches are bound by InputAreaSpec.
public class ImagePasteTransferHandler extends TransferHandler {

    private static final long serialVersionUID = 1L;

    private final transient PasteCallback callback;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    public interface PasteCallback {
        boolean canAddAttachment();

        void onAttachmentAdded(AttachedFile file);

        void onError(String message);

        void onAttachmentLimitReached();
    }

    public ImagePasteTransferHandler(PasteCallback callback) {
        this.callback = callback;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (c instanceof JTextComponent tc) {
            String text = tc.getSelectedText();
            if (text != null) {
                return new StringSelection(text);
            }
        }
        return null;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        if (source instanceof JTextComponent tc && action == MOVE && data != null) {
            tc.replaceSelection("");
        }
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.imageFlavor)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || super.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        boolean hasImage = support.isDataFlavorSupported(DataFlavor.imageFlavor);
        boolean hasFileList = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);

        if ((hasImage || hasFileList) && callback != null && !callback.canAddAttachment()) {
            callback.onAttachmentLimitReached();
            return false;
        }

        try {
            if (hasImage) {
                Image image = (Image) support.getTransferable().getTransferData(DataFlavor.imageFlavor);
                if (image != null) {
                    // Do I/O on background thread, report via callback
                    ImagePasteIoProcessor.get().post(() -> processImageAsync(image));
                    return true;
                }
            }

            if (hasFileList) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (files != null && !files.isEmpty()) {
                    File file = files.get(0);
                    if (file != null) {
                        ImagePasteIoProcessor.get().post(() -> processFileAsync(file));
                        return true;
                    }
                }
            }
        } catch (UnsupportedFlavorException | IOException e) {
            reportError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_ReadClipboard", e.getMessage()));
        }

        // Fallback: text paste on EDT
        if (support.getComponent() instanceof JTextComponent tc) {
            try {
                String text = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                tc.replaceSelection(text != null ? text : "");
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                // text not available, continue
            }
        }

        // Wayland fallback: AWT imageFlavor broken, read via wl-paste CLI.
        // Both wl-paste probes call Process.waitFor(), so they MUST run off the
        // EDT to avoid blocking the UI for hundreds of ms. Optimistically return
        // true (matching the image/file async paths) and report via the callback.
        if (isWayland() && callback != null && callback.canAddAttachment()) {
            ImagePasteIoProcessor.get().post(this::processWaylandClipboardAsync);
            return true;
        }
        return false;
    }

    private void processImageAsync(Image image) {
        try {
            AttachedFile attachedFile = createAttachedFileFromImage(image);
            if (attachedFile != null && callback != null) {
                javax.swing.SwingUtilities.invokeLater(() -> callback.onAttachmentAdded(attachedFile));
            }
        } catch (Exception e) {
            reportError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_SavePastedImage", e.getMessage()));
        }
    }

    private void processFileAsync(File file) {
        try {
            AttachedFile attachedFile = createAttachedFileFromFile(file);
            if (attachedFile != null && callback != null) {
                javax.swing.SwingUtilities.invokeLater(() -> callback.onAttachmentAdded(attachedFile));
            }
        } catch (Exception e) {
            reportError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_ProcessPastedFile", e.getMessage()));
        }
    }

    private void processWaylandClipboardAsync() {
        try {
            if (!isWlPasteAvailable()) {
                reportError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_WlPasteNotFound"));
                return;
            }
            AttachedFile attachedFile = fetchWaylandClipboardImage();
            if (attachedFile != null) {
                javax.swing.SwingUtilities.invokeLater(() -> callback.onAttachmentAdded(attachedFile));
            }
        } catch (Exception e) {
            reportError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_ReadClipboard", e.getMessage()));
        }
    }

    private void reportError(String message) {
        if (callback != null) {
            javax.swing.SwingUtilities.invokeLater(() -> callback.onError(message));
        }
    }

    private AttachedFile createAttachedFileFromImage(Image image) {
        try {
            BufferedImage bufferedImage = null;
            if (image instanceof BufferedImage) {
                bufferedImage = (BufferedImage) image;
            } else if (image != null) {
                int w = image.getWidth(null);
                int h = image.getHeight(null);
                if (w <= 0 || h <= 0) {
                    if (callback != null) {
                        callback.onError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_InvalidDimensions"));
                    }
                    return null;
                }

                bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }

            if (bufferedImage != null) {
                return attachImage(bufferedImage, image);
            }
        } catch (IOException | RuntimeException e) {
            if (callback != null) {
                callback.onError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_SavePastedImage", e.getMessage()));
            }
        }

        return null;
    }

    private AttachedFile attachImage(BufferedImage bufferedImage, Image image) throws IOException {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        long size = (long) bufferedImage.getWidth() * bufferedImage.getHeight() * 4;
        if (size > MAX_FILE_SIZE) {
            if (callback != null) {
                callback.onError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_FileTooLarge"));
            }
            return null;
        }

        String filename = "paste_" + UUID.randomUUID().toString() + ".png";
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path tempFile = tempDir.resolve(filename);

        boolean success = ImageIO.write(bufferedImage, "png", tempFile.toFile());
        if (!success) {
            if (callback != null) {
                callback.onError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_NoPngWriter"));
            }
            return null;
        }

        return new AttachedFile(tempFile.toFile());
    }

    private static boolean isWayland() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        return os.contains("linux") && "wayland".equals(sessionType);
    }

    private static boolean isWlPasteAvailable() {
        Process proc = null;
        try {
            proc = new ProcessBuilder("which", "wl-paste")
                    .redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (proc != null) proc.destroy();
        }
    }

    private AttachedFile fetchWaylandClipboardImage() throws Exception {
        // Probe actual MIME types on clipboard
        Process listProc = null;
        try {
            listProc = new ProcessBuilder("wl-paste", "--list-types")
                    .redirectErrorStream(true).start();
            String types = new String(listProc.getInputStream().readAllBytes()).strip();
            int listExit = listProc.waitFor();
            if (listExit != 0 || types.isEmpty()) {
                return null;
            }

            // Find first image/* type
            String imageType = null;
            for (String line : types.split("\n")) {
                String t = line.strip();
                if (t.startsWith("image/")) {
                    imageType = t;
                    break;
                }
            }
            if (imageType == null) {
                return null;
            }

            Process proc = null;
            try {
                proc = new ProcessBuilder("wl-paste", "-t", imageType)
                        .redirectErrorStream(true).start();
                byte[] data = proc.getInputStream().readAllBytes();
                int exitCode = proc.waitFor();
                if (exitCode != 0 || data.length == 0) {
                    return null;
                }

                Path tempFile = Path.of(System.getProperty("java.io.tmpdir"),
                        "paste_" + UUID.randomUUID().toString() + ".png");
                Files.write(tempFile, data);
                return new AttachedFile(tempFile.toFile());
            } finally {
                if (proc != null) proc.destroy();
            }
        } finally {
            if (listProc != null) listProc.destroy();
        }
    }

    private AttachedFile createAttachedFileFromFile(File file) {
        try {
            if (file.length() > MAX_FILE_SIZE) {
                if (callback != null) {
                    callback.onError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_FileTooLarge"));
                }
                return null;
            }

            String originalName = file.getName();
            String extension = "";
            if (originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf('.'));
            }

            String baseName = originalName;
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf('.'));
            }

            String tempFilename = "paste_" + UUID.randomUUID().toString() + "_" + baseName + extension;

            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
            Path tempFile = tempDir.resolve(tempFilename);

            Files.copy(file.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            return new AttachedFile(tempFile.toFile());

        } catch (IOException e) {
            if (callback != null) {
                callback.onError(NbBundle.getMessage(ImagePasteTransferHandler.class, "ERR_ProcessPastedFile", e.getMessage()));
            }
            return null;
        }
    }
}
