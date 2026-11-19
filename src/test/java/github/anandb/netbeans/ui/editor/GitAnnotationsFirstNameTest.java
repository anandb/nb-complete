package github.anandb.netbeans.ui.editor;

import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Author-name derivation and annotation rewriting for the git "first name"
 * option.
 */
class GitAnnotationsFirstNameTest {

    /** Mirror of the git module's annotation bar: the map field the patcher rewrites. */
    private static final class FakeBar extends JComponent {
        private final Map<Object, Object> elementAnnotations = new HashMap<>();
    }

    /** Mirror of {@code AnnotateLine}: private author and abbreviation fields. */
    private static final class FakeLine {
        private final int lineNum;
        private final Object author;
        private final String authorShort;

        FakeLine(int lineNum, Object author, String authorShort) {
            this.lineNum = lineNum;
            this.author = author;
            this.authorShort = authorShort;
        }

        public int getLineNum() {
            return lineNum;
        }

        String currentShortAuthor() {
            return authorShort;
        }
    }

    @Test
    void realNameContributesItsFirstToken() {
        assertEquals("John", GitAnnotationsFirstName.preferredAuthor(
                "John Doe", "john@example.com"));
        assertEquals("Jane", GitAnnotationsFirstName.preferredAuthor(
                "  Jane   Doe  ", "jane@example.com"));
    }

    @Test
    void missingNameFallsBackToEmailUserName() {
        assertEquals("octocat", GitAnnotationsFirstName.preferredAuthor(
                null, "octocat@github.com"));
        assertEquals("octocat", GitAnnotationsFirstName.preferredAuthor(
                "   ", "octocat@github.com"));
    }

    @Test
    void gitHubNoreplyEmailDropsTheNumericPrefix() {
        assertEquals("octocat", GitAnnotationsFirstName.preferredAuthor(
                null, "1234+octocat@users.noreply.github.com"));
        assertEquals("octocat", GitAnnotationsFirstName.preferredAuthor(
                "1234+octocat@users.noreply.github.com", "ignored@example.com"));
    }

    @Test
    void emailWithoutAtSignIsUsedAsIs() {
        assertEquals("octocat", GitAnnotationsFirstName.preferredAuthor(
                null, "octocat"));
    }

    @Test
    void nothingUsableYieldsNull() {
        assertNull(GitAnnotationsFirstName.preferredAuthor(null, null));
        assertNull(GitAnnotationsFirstName.preferredAuthor("  ", null));
    }

    @Test
    void applyRewritesEveryAuthorAbbreviation() {
        FakeBar bar = new FakeBar();
        FakeLine named = new FakeLine(1, new FakeGitUser("John Doe", "john@example.com"), "john");
        FakeLine noreply = new FakeLine(2,
                new FakeGitUser(null, "1234+octocat@users.noreply.github.com"), "1234+octocat");
        bar.elementAnnotations.put("b", noreply);
        bar.elementAnnotations.put("a", named);

        GitAnnotationsFirstName.apply(bar);

        assertEquals("John", named.currentShortAuthor());
        assertEquals("octocat", noreply.currentShortAuthor());
    }

    @Test
    void authorChangesAlternateTheNameCase() {
        FakeBar bar = new FakeBar();
        FakeLine john1 = new FakeLine(1, new FakeGitUser("John Doe", "john@example.com"), "john");
        FakeLine john2 = new FakeLine(2, new FakeGitUser("John Doe", "john@example.com"), "john");
        FakeLine jane1 = new FakeLine(3, new FakeGitUser("Jane Roe", "jane@example.com"), "jane");
        FakeLine jane2 = new FakeLine(4, new FakeGitUser("Jane Roe", "jane@example.com"), "jane");
        FakeLine bob1 = new FakeLine(5, new FakeGitUser("Bob Kim", "bob@example.com"), "bob");
        // Keys are unordered, so the patch must sort by line number.
        bar.elementAnnotations.put("e", bob1);
        bar.elementAnnotations.put("d", jane2);
        bar.elementAnnotations.put("c", jane1);
        bar.elementAnnotations.put("b", john2);
        bar.elementAnnotations.put("a", john1);

        GitAnnotationsFirstName.apply(bar);

        assertEquals("John", john1.currentShortAuthor());
        assertEquals("John", john2.currentShortAuthor());
        assertEquals("jane", jane1.currentShortAuthor());
        assertEquals("jane", jane2.currentShortAuthor());
        assertEquals("Bob", bob1.currentShortAuthor());
    }

    @Test
    void applyIsStableAndLeavesUnusableLinesAlone() {
        FakeBar bar = new FakeBar();
        FakeLine line = new FakeLine(1, new FakeGitUser("Jane Doe", "jane@example.com"), "jane");
        FakeLine anonymous = new FakeLine(2, null, "unknown");
        bar.elementAnnotations.put("a", line);
        bar.elementAnnotations.put("b", anonymous);

        GitAnnotationsFirstName.apply(bar);
        GitAnnotationsFirstName.apply(bar);

        assertEquals("Jane", line.currentShortAuthor());
        assertEquals("unknown", anonymous.currentShortAuthor());
    }
}
