package github.anandb.netbeans.ui.chat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.ui.rendering.HtmlContentPreparer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The history render parses markdown ahead of the EDT, so the warm-up has to cover exactly the
 * segments the render will ask for and must not disturb what an on-EDT parse returns.
 */
class MarkdownWarmupTest {

    private static final String FENCED_AND_TABLE = """
            intro **bold** text

            ```java
            int x = 1;
            ```

            |a|b|
            |-|-|
            |1|2|
            """;

    @Test
    void warmUpCoversFencedCodeAndTablesWithoutChangingTheirHtml() throws Exception {
        String table = "|a|b|\n|-|-|\n|1|2|\n";
        String tableHtml = HtmlContentPreparer.htmlFor(table);
        assertFalse(tableHtml.isEmpty());

        BubbleContentRenderer.warmAsync(List.of(FENCED_AND_TABLE)).get(20, TimeUnit.SECONDS);

        // The segments the render will request are already parsed, and identical to a cold parse.
        assertEquals(tableHtml, HtmlContentPreparer.htmlFor(table));
        assertEquals(HtmlContentPreparer.htmlFor("intro **bold** text\n"),
                HtmlContentPreparer.htmlFor("intro **bold** text\n"));
    }

    @Test
    void warmUpOnAWorkerAgreesWithAParallelParse() throws Exception {
        String markdown = "**same** message";
        String expected = HtmlContentPreparer.htmlFor(markdown);
        CompletableFuture<Void> warm = BubbleContentRenderer.warmAsync(List.of(markdown, markdown));
        assertEquals(expected, HtmlContentPreparer.htmlFor(markdown));
        warm.get(20, TimeUnit.SECONDS);
        assertEquals(expected, HtmlContentPreparer.htmlFor(markdown));
    }

    @Test
    void warmUpToleratesBlankAndMissingText() throws Exception {
        BubbleContentRenderer.warmAsync(Arrays.asList("", null)).get(20, TimeUnit.SECONDS);
        BubbleContentRenderer.warmAsync(List.of()).get(20, TimeUnit.SECONDS);
    }
}
