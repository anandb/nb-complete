package github.anandb.netbeans.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BubbleContentRendererTest {

    @Test
    void replacesGenericImageAttachmentWithMimeType() {
        String content = "Look at [@image](data:image/png;base64,AAECAw==)";

        assertEquals("Look at [Image: image/png]", BubbleContentRenderer.imageAttachmentMetadata(content));
    }

    @Test
    void preservesNamedImageMetadataAndReplacesMultiplePayloads() {
        String content = "![diagram](data:image/svg+xml;base64,AAAA) and "
                + "[photo](data:image/jpeg;base64,BBBB)";

        assertEquals("[Image: diagram (image/svg+xml)] and [Image: photo (image/jpeg)]",
                BubbleContentRenderer.imageAttachmentMetadata(content));
    }

    @Test
    void leavesTextWithoutEmbeddedImageDataUnchanged() {
        String content = "![image](https://example.com/image.png)";

        assertEquals(content, BubbleContentRenderer.imageAttachmentMetadata(content));
    }
}
