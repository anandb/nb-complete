# Image Paste Support Design

## Overview
Add image paste support to `PlaceholderTextArea` via a custom `TransferHandler` that intercepts paste operations (Ctrl+V, right-click paste, menu paste).

## Requirements
- Support pasting images from clipboard (screenshots, image data from applications)
- Support pasting image files copied in file managers
- Save pasted images to system temp directory
- Add pasted images to existing attachment system
- Respect existing limits: max 2 files, 10MB per file
- Show appropriate error messages in status label when limits exceeded or image too large
- No preview needed - just add to attachments (visible via paperclip icon)

## Components

### 1. ImagePasteTransferHandler (new top-level class)
- **Package**: `github.anandb.netbeans.ui`
- **Extends**: `TransferHandler`
- **Purpose**: Intercept paste operations on `PlaceholderTextArea`
- **Methods**:
  - `canImport(TransferSupport support)`: Check if clipboard contains image or file list flavors
  - `importData(TransferSupport support)`: Handle the paste operation
  - `createAttachedFileFromImage(Image image)`: Convert `java.awt.Image` to temp file and create `AttachedFile`
  - `createAttachedFileFromFile(File file)`: Copy file to temp dir and create `AttachedFile`
  - `saveImageToTemp(BufferedImage bufferedImage, String filename)`: Save image as PNG to temp directory

### 2. Clipboard Detection Priority
1. First check: `DataFlavor.imageFlavor` - for screenshots/applications that copy image data
2. Second check: `DataFlavor.javaFileListFlavor` - for files copied in file managers
3. Fallback: delegate to default text paste behavior via `super.importData(support)`

### 3. Image Save Logic
- Generate unique filename: `paste_<timestamp>_<random>.png` (default to PNG for Image objects)
- For files from file manager: preserve original extension, generate `paste_<timestamp>_<originalname>.<ext>`
- Save to `System.getProperty("java.io.tmpdir")`
- For `java.awt.Image` objects:
  - Cast to `BufferedImage` or convert via `BufferedImage.TYPE_INT_ARGB`
  - Use `ImageIO.write(bufferedImage, "png", tempFile)`
- For files: copy to temp directory using `Files.copy()` (avoid using original path directly)

### 4. Integration with Existing Attachment System
- Create `AttachedFile` from saved temp file
- Add to `attachedFiles` list (respecting MAX_ATTACHMENTS=2 and MAX_ATTACHMENT_SIZE=10MB limits)
- Update paperclip tooltip via `updatePaperclipTooltip()`
- Show status message for successful paste or limit exceeded
- Callback interface to `AssistantTopComponent` for adding attachments

### 5. Modifications to PlaceholderTextArea
- Set the custom `TransferHandler` on the text area in constructor or via setter
- No changes needed to `PlaceholderTextArea` itself - the `TransferHandler` is set externally

### 6. Modifications to AssistantTopComponent
- Create `ImagePasteTransferHandler` instance with callback to handle attachment addition
- Set the transfer handler on `inputArea` after creation
- Add method `addAttachmentFromPaste(AttachedFile file)` that:
  - Checks limits
  - Adds to `attachedFiles`
  - Updates UI
  - Shows status message

## Data Flow
```
User pastes (Ctrl+V / right-click / menu)
    ↓
ImagePasteTransferHandler.canImport() checks clipboard flavors
    ↓
ImagePasteTransferHandler.importData() handles image/file
    ↓
Save image to temp dir → create AttachedFile
    ↓
Callback to AssistantTopComponent.addAttachmentFromPaste()
    ↓
Add to attachedFiles → Update UI (paperclip icon, status message)
```

## Error Handling
- **Temp file creation fails**: Show "Failed to save pasted image" in statusLabel
- **Max attachments reached**: Show "Max 2 files allowed" message (reuse existing logic via statusResetTimer)
- **Image too large (>10MB)**: Show "Image too large (max 10MB)" in statusLabel
- **Clipboard has no supported data**: Fall through to default text paste behavior
- **Image conversion fails**: Show "Failed to process pasted image" in statusLabel

## Files to Modify
1. **New file**: `src/main/java/github/anandb/netbeans/ui/ImagePasteTransferHandler.java`
2. **Modify**: `src/main/java/github/anandb/netbeans/ui/AssistantTopComponent.java`
   - Add `ImagePasteTransferHandler` setup in constructor
   - Add `addAttachmentFromPaste(AttachedFile file)` method

## Testing Considerations
- Test pasting screenshot from system screenshot tool
- Test pasting image copied from browser
- Test pasting image file from file manager
- Test paste limits (max 2 files)
- Test large image rejection
- Test fallback to text paste when clipboard has text
- Test that paste works via Ctrl+V, right-click paste, and menu paste
