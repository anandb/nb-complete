package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.AttachedFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttachmentManager {

    static final int MAX_ATTACHMENTS = 2;
    static final long MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024;
    /**
     * Largest file body inlined into a prompt, in characters. A 10 MB log would
     * otherwise become a multi-million-token turn (and stay in session history),
     * so the body is cut here with a marker the model can see.
     */
    static final int MAX_INLINE_TEXT_CHARS = 256 * 1024;

    private final List<AttachedFile> attachments = new ArrayList<>();

    public boolean canAdd() {
        return attachments.size() < MAX_ATTACHMENTS;
    }

    public boolean add(AttachedFile file) {
        if (file.size() > MAX_ATTACHMENT_SIZE) return false;
        if (attachments.size() >= MAX_ATTACHMENTS) return false;
        return attachments.add(file);
    }

    public boolean remove(AttachedFile file) {
        return attachments.remove(file);
    }

    public void clear() {
        attachments.clear();
    }

    public List<AttachedFile> getAttachments() {
        return new ArrayList<>(attachments);
    }

    public int size() {
        return attachments.size();
    }

    /**
     * Builds ACP v1 {@code ContentBlock}s for the attachments.
     * <p>
     * ACP has no {@code "file"} content block, so a non-image attachment is sent
     * as (1) an embedded {@code resource} when the agent advertises
     * {@code promptCapabilities.embeddedContext}, (2) an inlined {@code text}
     * block for textual files otherwise (text is the one block type every agent
     * must support, and it does not depend on the agent reading a local path),
     * and (3) a {@code resource_link} as the last resort for binary files.
     */
    public List<Map<String, Object>> buildFileBlocks(boolean embeddedContextSupported) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (AttachedFile af : attachments) {
            blocks.add(toContentBlock(af, embeddedContextSupported));
        }
        return blocks;
    }

    private static Map<String, Object> toContentBlock(AttachedFile af, boolean embeddedContextSupported) {
        if (af.isImage()) {
            Map<String, Object> block = new HashMap<>();
            block.put("type", "image");
            block.put("mimeType", af.mimeType());
            block.put("data", af.base64Data());
            return block;
        }

        if (embeddedContextSupported) {
            Map<String, Object> resource = new HashMap<>();
            resource.put("uri", af.uri());
            resource.put("mimeType", af.mimeType());
            String text = af.textContent();
            if (text != null) {
                resource.put("text", inlineText(text));
            } else {
                resource.put("blob", af.base64Data());
            }
            Map<String, Object> block = new HashMap<>();
            block.put("type", "resource");
            block.put("resource", resource);
            return block;
        }

        String text = af.textContent();
        if (text != null) {
            String inline = inlineText(text);
            // Fence longer than the longest backtick run, so file content that
            // itself contains a code fence cannot terminate the block early.
            int fenceLength = Math.max(3, longestBacktickRun(inline) + 1);
            String fence = "`".repeat(fenceLength);
            Map<String, Object> block = new HashMap<>();
            block.put("type", "text");
            block.put("text", "Attached file `" + af.filename() + "`:\n"
                    + fence + "\n" + inline + "\n" + fence + "\n");
            // The whole file body is in the prompt, so hide it from the replayed
            // user bubble (same audience trick as the editor-context block).
            block.put("annotations", Map.of("audience", List.of("assistant")));
            return block;
        }

        Map<String, Object> block = new HashMap<>();
        block.put("type", "resource_link");
        block.put("uri", af.uri());
        block.put("name", af.filename());
        block.put("mimeType", af.mimeType());
        block.put("size", af.size());
        return block;
    }

    /**
     * File body as it may enter a prompt: unchanged up to
     * {@link #MAX_INLINE_TEXT_CHARS}, otherwise cut with a marker that tells the
     * model the text continues. The marker is part of the size budget so the
     * result never exceeds the cap.
     */
    private static String inlineText(String text) {
        if (text.length() <= MAX_INLINE_TEXT_CHARS) {
            return text;
        }
        String marker = "\n\n[BeanBot: attachment truncated at " + MAX_INLINE_TEXT_CHARS
                + " characters; the file continues]";
        int keep = Math.max(0, MAX_INLINE_TEXT_CHARS - marker.length());
        return text.substring(0, keep) + marker;
    }

    private static int longestBacktickRun(String text) {
        int longest = 0;
        int current = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '`') {
                current++;
                if (current > longest) {
                    longest = current;
                }
            } else {
                current = 0;
            }
        }
        return longest;
    }

    public void addFromFiles(File[] files) {
        if (files == null) return;
        for (File f : files) {
            if (attachments.size() >= MAX_ATTACHMENTS) break;
            if (f.length() > MAX_ATTACHMENT_SIZE) continue;
            try {
                attachments.add(new AttachedFile(f));
            } catch (IOException e) {
                // skip files that can't be read
            }
        }
    }
}
