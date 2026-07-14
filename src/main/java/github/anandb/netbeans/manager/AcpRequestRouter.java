package github.anandb.netbeans.manager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import javax.swing.text.Document;

import github.anandb.netbeans.support.ToolDataExtractor;

import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.swing.SwingUtilities;

import github.anandb.netbeans.project.ACPProjectManager;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import org.netbeans.api.project.Project;

class AcpRequestRouter {
    private static final Logger LOG = Logger.from(AcpRequestRouter.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private volatile github.anandb.netbeans.contract.PermissionHandler permissionHandler;

    AcpRequestRouter() {
    }

    void setPermissionHandler(github.anandb.netbeans.contract.PermissionHandler handler) {
        this.permissionHandler = handler;
    }

    CompletableFuture<JsonNode> handleRequestPermission(JsonNode params) {
        String sessionId = params.has("sessionId") ? params.get("sessionId").asText() : null;
        String toolCallId = ToolDataExtractor.extractToolCallId(params);

        final String extractedId = toolCallId;
        CompletableFuture<String> response = new CompletableFuture<>();

        if (permissionHandler != null) {
            SwingUtilities.invokeLater(() -> {
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
                mappedId = "once";
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
            if ("reject".equals(optionId)) {
                res.put("allow", false);
            } else {
                res.put("allow", true);
            }

            return res;
        });
    }

    CompletableFuture<JsonNode> handleReadTextFile(JsonNode params) {
        String filePath = params.has("filePath") ? params.get("filePath").asText()
                : params.has("path") ? params.get("path").asText() : null;

        if (filePath == null) {
            return CompletableFuture.failedFuture(new RuntimeException(NbBundle.getMessage(ProcessManager.class, "ERR_MissingFilePath")));
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return CompletableFuture.failedFuture(new RuntimeException(NbBundle.getMessage(ProcessManager.class, "ERR_FileNotFound", filePath)));
        }

        // Prevent arbitrary file reads — only allow files inside open projects.
        // Resolve both to canonical paths to prevent .. and symlink escapes.
        try {
            String canonicalRequested = file.getCanonicalPath();
            boolean inProject = false;
            for (Project p : ACPProjectManager.getInstance().getAllOpenProjects()) {
                File projectDirFile = FileUtil.toFile(p.getProjectDirectory());
                if (projectDirFile == null) continue;
                String canonicalProject = projectDirFile.getCanonicalPath();
                if (canonicalRequested.startsWith(canonicalProject)) {
                    inProject = true;
                    break;
                }
            }
            if (!inProject) {
                LOG.warn("Blocked fs/readTextFile on path outside project: {0}", filePath);
                return CompletableFuture.failedFuture(new RuntimeException(
                    NbBundle.getMessage(ProcessManager.class, "ERR_PathOutsideProject")));
            }
        } catch (java.io.IOException e) {
            LOG.warn("Failed to resolve canonical path for {0}", filePath);
            return CompletableFuture.failedFuture(new RuntimeException("Invalid file path"));
        }

        CompletableFuture<JsonNode> resultFuture = new CompletableFuture<>();

        FileObject fo = FileUtil.toFileObject(file);
        if (fo != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    DataObject dobj = DataObject.find(fo);
                    EditorCookie ec = dobj.getLookup().lookup(EditorCookie.class);
                    if (ec != null) {
                        Document doc = ec.getDocument();
                        if (doc != null) {
                            String content = doc.getText(0, doc.getLength());
                            resultFuture.complete(MAPPER.createObjectNode().put("content", content));
                            return;
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Could not read from editor for {0}, falling back to disk", filePath, e);
                }

                readFromDisk(file, filePath, resultFuture);
            });
        } else {
            readFromDisk(file, filePath, resultFuture);
        }

        return resultFuture;
    }

    private void readFromDisk(File file, String filePath, CompletableFuture<JsonNode> resultFuture) {
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
                return MAPPER.createObjectNode().put("content", content);
            } catch (Exception e) {
                LOG.severe("fs/readTextFile failed: {0}", e.getMessage());
                LOG.log(Level.FINE, "fs/readTextFile details", e);
                throw new RuntimeException("Failed to read file");
            }
        }).thenAccept(resultFuture::complete)
          .exceptionally(ex -> {
              resultFuture.completeExceptionally(ex);
              return null;
          });
    }
}
