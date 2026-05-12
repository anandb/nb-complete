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

    public List<Map<String, Object>> buildFileBlocks() {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (AttachedFile af : attachments) {
            Map<String, Object> block = new HashMap<>();
            block.put("type", af.mimeType().startsWith("image/") ? "image" : "file");
            block.put("filename", af.filename());
            block.put("mimeType", af.mimeType());
            block.put("data", af.base64Data());
            blocks.add(block);
        }
        return blocks;
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
