package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelIdNormalizerTest {

    @Test
    void colonSeparatorBecomesSlash() {
        assertEquals("opencode-go/deepseek-v4.1-flash",
                ModelIdNormalizer.normalize("opencode-go:deepseek-v4.1-flash"));
    }

    @Test
    void slashFormIsUnchanged() {
        assertEquals("opencode-go/deepseek-v4.1-flash",
                ModelIdNormalizer.normalize("opencode-go/deepseek-v4.1-flash"));
    }

    @Test
    void everySeparatorIsReplacedSoBothFormsAgree() {
        assertEquals("a/b/c", ModelIdNormalizer.normalize("a:b:c"));
        assertEquals("a/b/c", ModelIdNormalizer.normalize("a/b/c"));
    }

    @Test
    void surroundingWhitespaceIsRemoved() {
        assertEquals("opencode-go/glm-5", ModelIdNormalizer.normalize("  opencode-go:glm-5  "));
    }

    @Test
    void absentAndBlankStayAbsent() {
        assertNull(ModelIdNormalizer.normalize(null));
        assertNull(ModelIdNormalizer.normalize(""));
        assertNull(ModelIdNormalizer.normalize("   "));
    }

    @Test
    void aNameWithoutASeparatorSurvives() {
        assertEquals("auto", ModelIdNormalizer.normalize("auto"));
    }
}