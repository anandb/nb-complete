package github.anandb.netbeans.manager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelCacheTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // ModelCache uses static methods
    }

    @Test
    void testCacheAddAndRetrieve() {
        ModelCache.updateModels(List.of("model1", "model2"));
        List<String> models = ModelCache.getCachedModels();
        
        assertTrue(models.contains("model1"));
        assertTrue(models.contains("model2"));
    }

    @Test
    void testCacheAddModel() {
        ModelCache.addModel("new-model");
        List<String> models = ModelCache.getCachedModels();
        assertTrue(models.contains("new-model"));
    }
}
