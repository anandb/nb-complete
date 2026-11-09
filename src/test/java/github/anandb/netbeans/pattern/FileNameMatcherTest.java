package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * One set of matching rules serves Jump to File and the find_file tool, so the rules
 * themselves are pinned here rather than through the dialog.
 */
class FileNameMatcherTest {

    private static FileNameMatcher.Query query(String text) {
        return FileNameMatcher.compile(text);
    }

    @Test
    void blankQueryIsNotUsable() {
        assertFalse(query("").usable());
        assertFalse(query("   ").usable());
        assertFalse(query(null).usable());
    }

    @Test
    void plainQueryShorterThanTheMinimumIsNotUsable() {
        assertFalse(query("ab").usable());
        assertTrue(query("abc").usable());
    }

    @Test
    void fileNamePrefixMatches() {
        FileNameMatcher.Query q = query("chat");
        assertTrue(q.matches("ChatThreadPanel.java", "src/ui/ChatThreadPanel.java"));
        assertTrue(q.namePrefix("ChatThreadPanel.java"));
    }

    @Test
    void pathSubstringMatchesButIsNotANameHit() {
        FileNameMatcher.Query q = query("chat");
        assertTrue(q.matches("MessageBubble.java", "src/ui/chat/MessageBubble.java"));
        assertFalse(q.namePrefix("MessageBubble.java"));
    }

    @Test
    void fileNamePrefixIsCaseInsensitive() {
        assertTrue(query("CHAT").matches("chatbox.java", "chatbox.java"));
    }

    @Test
    void globIsAcceptedBelowThePlainMinimum() {
        FileNameMatcher.Query q = query("*Ab");
        assertTrue(q.usable());
        assertTrue(q.matches("MyAbFile.java", "src/MyAbFile.java"));
    }

    @Test
    void globGetsAnImplicitTrailingWildcard() {
        assertTrue(query("*Test").matches("MyTestFile.java", "src/MyTestFile.java"));
        assertTrue(query("*.java").matches("Thing.java", "src/Thing.java"));
        assertFalse(query("*.java").matches("Thing.txt", "src/Thing.txt"));
    }

    @Test
    void globMatchesAgainstTheRelativePathToo() {
        assertTrue(query("**/main/**").matches("App.java", "src/main/java/App.java"));
    }

    @Test
    void anUnparseableGlobFallsBackToThePlainLengthRule() {
        FileNameMatcher.Query q = query("a[");
        assertFalse(q.usable(), "a broken glob must not slip past the plain length rule");
    }

    @Test
    void anUnparseableGlobStillMatchesLiterally() {
        assertTrue(query("[abc").matches("x[abc.txt", "src/x[abc.txt"),
                "plain substring rules keep working when the glob cannot be built");
    }

    @Test
    void aQueryLongerThanThePathDoesNotMatch() {
        assertFalse(query("nosuchthing").matches("Abc.java", "src/Abc.java"));
    }
}
