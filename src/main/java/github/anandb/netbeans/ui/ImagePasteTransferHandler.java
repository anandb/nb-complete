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
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;
import javax.swing.text.JTextComponent;

import github.anandb.netbeans.model.AttachedFile;

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
        if (support.isDataFlavorSupported(DataFlavor.imageFlavor)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            if (callback != null && !callback.canAddAttachment()) {
                callback.onAttachmentLimitReached();
                return false;
            }
        }

        try {
            if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image image = (Image) support.getTransferable().getTransferData(DataFlavor.imageFlavor);
                if (image != null) {
                    AttachedFile attachedFile = createAttachedFileFromImage(image);
                    if (attachedFile != null && callback != null) {
                        callback.onAttachmentAdded(attachedFile);
                        return true;
                    }
                }
            }

            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (files != null && !files.isEmpty()) {
                    File file = files.get(0);
                    if (file != null) {
                        AttachedFile attachedFile = createAttachedFileFromFile(file);
                        if (attachedFile != null && callback != null) {
                            callback.onAttachmentAdded(attachedFile);
                            return true;
                        }
                    }
                }
            }
        } catch (UnsupportedFlavorException | IOException e) {
            if (callback != null) {
                callback.onError("Failed to process pasted file: " + e.getMessage());
            }
        }

        if (support.getComponent() instanceof JTextComponent tc) {
            try {
                String text = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                tc.replaceSelection(text != null ? text : "");
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }
        }
        return false;
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
                        callback.onError("Pasted image has invalid dimensions");
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
                callback.onError("Failed to save pasted image: " + e.getMessage());
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
                callback.onError("File too large (max 10MB)");
            }
            return null;
        }

        String filename = "paste_" + UUID.randomUUID().toString() + ".png";
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path tempFile = tempDir.resolve(filename);

        boolean success = ImageIO.write(bufferedImage, "png", tempFile.toFile());
        if (!success) {
            if (callback != null) {
                callback.onError("Failed to save pasted image - no PNG writer available");
            }
            return null;
        }

        return new AttachedFile(tempFile.toFile());
    }

    private AttachedFile createAttachedFileFromFile(File file) {
        try {
            if (file.length() > MAX_FILE_SIZE) {
                if (callback != null) {
                    callback.onError("File too large (max 10MB)");
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
                callback.onError("Failed to process pasted file: " + e.getMessage());
            }
            return null;
        }
    }
}
