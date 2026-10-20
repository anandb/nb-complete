package github.anandb.netbeans.manager;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import javax.swing.text.Document;


import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.support.FsWriteSettings;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.ToolDataExtractor;

class AcpRequestRouter {
    private static final Logger LOG = Logger.from(AcpRequestRouter.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private volatile PermissionHandler permissionHandler;

    AcpRequestRouter() {
    }

    void setPermissionHandler(PermissionHandler handler) {
        this.permissionHandler = handler;
    }

    CompletableFuture<JsonNode> handleRequestPermission(JsonNode params) {
        String sessionId = params.has("sessionId") ? params.get("sessionId").asText() : null;
        String toolCallId = ToolDataExtractor.extractToolCallId(params);

        final String extractedId = toolCallId;
        CompletableFuture<String> response = new CompletableFuture<>();

        if (permissionHandler != null) {
            // Capture the message epoch on this (reader) thread, i.e. at the moment the
            // request is received. If the user sends a new message before the panel is
            // displayed on the EDT, the epoch advances and this request is stale: it
            // belongs to a turn that was superseded. Showing it would leave the server
            // waiting forever on a decision for an abandoned tool call, hanging the plugin.
            final long epochAtReceipt = permissionHandler.currentPermissionEpoch();
            SwingUtilities.invokeLater(() -> {
                if (permissionHandler.currentPermissionEpoch() != epochAtReceipt) {
                    LOG.info("Rejecting stale permission request: superseded by a newer user message");
                    response.complete("reject");
                    return;
                }
                permissionHandler.handlePermissionRequest(sessionId, params, response);
            });
        } else {
            response.complete("reject");
        }

        return response.thenApply(optionId -> {
            ObjectNode res = MAPPER.createObjectNode();

            // Map common internal IDs to standard ACP ones if needed
            String mappedId = optionId;
            if ("allow".equals(optionId) || "true".equals(optionId)) {
                mappedId = "once";
            } else if ("deny".equals(optionId) || "false".equals(optionId)) {
                mappedId = "reject";
            }
            if (mappedId == null) {
                // Fail CLOSED. A null decision means "we do not know" — a dismissed panel,
                // an error path, or a handler that completed empty. Every other unknown in
                // this method rejects (no handler above), and an allow here would let the
                // agent act on a tool call the user never approved. There is no undo for
                // that, so an unresolvable decision is a rejection, not a grant.
                LOG.log(Level.WARNING, "session/request_permission returned no option id "
                        + "for session {0}, tool call {1} — rejecting", sessionId, extractedId);
                mappedId = "reject";
            }

            // Match ACP outcome structure
            ObjectNode outcome = MAPPER.createObjectNode();
            outcome.put("outcome", "selected");
            outcome.put("optionId", mappedId);
            res.set("outcome", outcome);

            if (sessionId != null) {
                res.put("sessionId", sessionId);
            }
            if (extractedId != null) {
                res.put("toolCallId", extractedId);
                res.put("tool_call_id", extractedId);
            }

            // Compatibility fields
            // Legacy boolean for older harnesses. Derive it from the NORMALISED id, not the
            // raw one: keying this off the raw optionId let a null decision write allow=true
            // while outcome.optionId above said "reject", so a harness reading this field was
            // granted access the standard field had just denied. The two must never disagree.
            // Residual: an arbitrary harness-supplied option id whose semantics we cannot
            // read is still treated as non-reject. That is deliberate — it is what the
            // outcome field already says, and the outcome field is the ACP standard one.
            res.put("allow", !"reject".equals(mappedId));

            return res;
        });
    }

    CompletableFuture<JsonNode> handleReadTextFile(JsonNode params) {
        String filePath = extractFilePath(params);

        if (filePath == null) {
            return CompletableFuture.failedFuture(new RequestRejectedException(
                    NbBundle.getMessage(ProcessManager.class, "ERR_MissingFilePath")));
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return CompletableFuture.failedFuture(new RequestRejectedException(
                    NbBundle.getMessage(ProcessManager.class, "ERR_FileNotFound", filePath)));
        }

        // Reads are not confined to open projects: the harness (agent) owns the
        // permission decision and gates file access through session/request_permission.

        CompletableFuture<JsonNode> resultFuture = new CompletableFuture<>();

        // ACP optionally scopes a read to a 1-based start line and a line count.
        // Absent values mean the whole file; a non-positive limit reads nothing.
        int startLine = Math.max(1, params.path("line").asInt(1));
        Integer lineLimit = params.hasNonNull("limit") ? Math.max(0, params.path("limit").asInt(0)) : null;

        FileObject fo = FileUtil.toFileObject(file);
        if (fo != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    DataObject dobj = DataObject.find(fo);
                    EditorCookie ec = dobj.getLookup().lookup(EditorCookie.class);
                    if (ec != null) {
                        Document doc = ec.getDocument();
                        if (doc != null) {
                            String content = selectLines(doc.getText(0, doc.getLength()), startLine, lineLimit);
                            resultFuture.complete(MAPPER.createObjectNode().put("content", content));
                            return;
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Could not read from editor for {0}, falling back to disk", filePath, e);
                }

                readFromDisk(file, startLine, lineLimit, resultFuture);
            });
        } else {
            readFromDisk(file, startLine, lineLimit, resultFuture);
        }

        return resultFuture;
    }

    private void readFromDisk(File file, int startLine, Integer lineLimit,
            CompletableFuture<JsonNode> resultFuture) {
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
                content = selectLines(content, startLine, lineLimit);
                return MAPPER.createObjectNode().put("content", content);
            } catch (Exception e) {
                LOG.severe("fs/readTextFile failed: {0}", ExceptionUtils.getMessage(e));
                LOG.log(Level.FINE, "fs/readTextFile details", e);
                throw new RequestRejectedException("Failed to read file");
            }
        }).thenAccept(resultFuture::complete)
          .exceptionally(ex -> {
              resultFuture.completeExceptionally(ex);
              return null;
          });
    }

    /**
     * Selects the ACP {@code line}/{@code limit} window from {@code content}.
     * {@code startLine} is 1-based; {@code limit} is a line count where
     * {@code null} means "to end of file" and a non-positive value yields an
     * empty string. A start line past the end of the file also yields an empty
     * string. The returned slice keeps the newline that terminates the last
     * selected line, matching the source text.
     */
    static String selectLines(String content, int startLine, Integer limit) {
        if (limit != null && limit <= 0) {
            return "";
        }
        int start = 0;
        for (int current = 1; current < startLine; current++) {
            int newline = content.indexOf('\n', start);
            if (newline < 0) {
                return "";
            }
            start = newline + 1;
        }
        int maxLines = limit == null ? Integer.MAX_VALUE : limit;
        int end = start;
        for (int read = 0; read < maxLines; read++) {
            int newline = content.indexOf('\n', end);
            if (newline < 0) {
                end = content.length();
                break;
            }
            end = newline + 1;
        }
        return content.substring(start, end);
    }

    CompletableFuture<JsonNode> handleWriteTextFile(JsonNode params) {
        // fs/write tools are ENABLED by default; -Dbeanbot.fs.write.enabled=false
        // turns them off. Reject early so no work runs even if a server ignores
        // the (un)advertised capability.
        if (!FsWriteSettings.isEnabled()) {
            LOG.log(Level.FINE, "Rejected fs/writeTextFile — writing is disabled (system property {0})",
                    PreferenceKeys.FS_WRITE_ENABLED_PROP);
            return CompletableFuture.failedFuture(new RequestRejectedException(
                    NbBundle.getMessage(ProcessManager.class, "ERR_WriteDisabled")));
        }

        String filePath = extractFilePath(params);
        String content = params.has("content") ? params.get("content").asText() : "";

        if (filePath == null) {
            return CompletableFuture.failedFuture(new RequestRejectedException(
                    NbBundle.getMessage(ProcessManager.class, "ERR_MissingFilePath")));
        }

        File file = new File(filePath);

        // Writes are not confined to open projects: the harness (agent) owns the
        // permission decision and gates file access through session/request_permission.

        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        // Offload filesystem work (folder creation, file creation, writing new
        // files) off the EDT so the UI cannot hang on slow disks or large writes.
        CompletableFuture.supplyAsync(() -> {
            try {
                boolean wasNew = !file.exists();
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    FileUtil.createFolder(parent);
                }
                FileObject fo = FileUtil.toFileObject(file);
                if (fo == null) {
                    FileObject parentFo = FileUtil.toFileObject(parent);
                    if (parentFo != null) {
                        fo = parentFo.createData(file.getName());
                    }
                }
                if (fo == null) {
                    throw new IOException("Could not create FileObject for " + filePath);
                }
                if (wasNew) {
                    // New files: content is already on disk; no editor
                    // involvement, so no tab opens and nothing steals focus.
                    try (OutputStream os = fo.getOutputStream()) {
                        os.write(content.getBytes(StandardCharsets.UTF_8));
                    }
                }
                return new WriteContext(fo, wasNew);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(ctx -> {
            if (ctx.wasNew) {
                result.complete(MAPPER.createObjectNode());
                return;
            }
            SwingUtilities.invokeLater(() -> {
                try {
                    DataObject dobj = DataObject.find(ctx.fo);
                    EditorCookie ec = dobj != null ? dobj.getLookup().lookup(EditorCookie.class) : null;
                    if (ec == null) {
                        throw new IOException("File is not editable: " + filePath);
                    }
                    // openDocument() loads the buffer without opening a visible
                    // editor tab (unlike open(), which also requests focus).
                    Document doc = ec.openDocument();
                    doc.remove(0, doc.getLength());
                    doc.insertString(0, content, null);
                    FileUtil.runAtomicAction((FileSystem.AtomicAction) () -> ec.saveDocument());
                    LOG.fine("Wrote fs/writeTextFile via editor: {0}", filePath);
                    result.complete(MAPPER.createObjectNode());
                } catch (Exception e) {
                    LOG.severe("fs/writeTextFile failed: {0}", ExceptionUtils.getMessage(e));
                    LOG.log(Level.FINE, "fs/writeTextFile details", e);
                    result.completeExceptionally(new RequestRejectedException(
                            NbBundle.getMessage(ProcessManager.class, "ERR_WriteFileFailed", filePath)));
                }
            });
        }).exceptionally(ex -> {
            LOG.severe("fs/writeTextFile failed: {0}", ExceptionUtils.getMessage(ex));
            LOG.log(Level.FINE, "fs/writeTextFile details", ex);
            result.completeExceptionally(new RequestRejectedException(
                    NbBundle.getMessage(ProcessManager.class, "ERR_WriteFileFailed", filePath)));
            return null;
        });
        return result;
    }

    private record WriteContext(FileObject fo, boolean wasNew) {}

    /** Resolves the file path param, which may be named "filePath" or "path". */
    private static String extractFilePath(JsonNode params) {
        return params.has("filePath") ? params.get("filePath").asText()
                : params.has("path") ? params.get("path").asText() : null;
    }
}
