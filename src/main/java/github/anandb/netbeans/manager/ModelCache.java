package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.modules.Places;

public class ModelCache {
    private static final Logger LOG = Logger.getLogger(ModelCache.class.getName());
    private static final String MODELS_FILE = "acp_models.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static List<String> cachedModels = new ArrayList<>();

    static {
        load();
    }

    private static File getStorageFile() {
        File userDir = Places.getUserDirectory();
        File acpDir;
        if (userDir != null) {
            acpDir = new File(userDir, "acp");
        } else {
            acpDir = new File(System.getProperty("user.home"), ".acp");
        }
        if (!acpDir.exists()) {
            acpDir.mkdirs();
        }
        return new File(acpDir, MODELS_FILE);
    }

    public static synchronized List<String> getCachedModels() {
        return new ArrayList<>(cachedModels);
    }

    public static synchronized void updateModels(List<String> models) {
        if (models != null && !models.isEmpty()) {
            cachedModels.clear();
            cachedModels.addAll(models);
            save();
        }
    }

    public static synchronized void addModel(String model) {
        if (model != null && !model.isEmpty() && !cachedModels.contains(model)) {
            cachedModels.add(model);
            save();
        }
    }

    private static void load() {
        File file = getStorageFile();
        if (file.exists()) {
            try {
                cachedModels = objectMapper.readValue(file, new TypeReference<List<String>>() {});
                LOG.log(Level.INFO, "Loaded {0} cached models", cachedModels.size());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load cached models: {0}", e.getMessage());
                cachedModels = new ArrayList<>();
            }
        }
    }

    private static void save() {
        File file = getStorageFile();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, cachedModels);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to save cached models: {0}", e.getMessage());
        }
    }
}
