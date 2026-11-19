package github.anandb.netbeans.ui.rendering;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The support-link mark is a raw SVG resource, so nothing type-checks it: a malformed file or a
 * portrait aspect ratio only shows up as a missing or squashed toolbar icon at runtime.
 */
class SproutIconResourceTest {

    private static final String LIGHT = "/github/anandb/netbeans/ui/icons/sprout.svg";
    private static final String DARK = "/github/anandb/netbeans/ui/icons/sprout_dark.svg";
    private static final String MOUND = "#6E5B3F";
    private static final String LEAF = "#B5E06C";
    private static final String DARK_OUTLINE = "#EDE6D8";

    @Test
    void bothVariantsAreValidSquareSvgDocuments() throws Exception {
        for (String resource : List.of(LIGHT, DARK)) {
            Document doc = parse(resource);
            Element root = doc.getDocumentElement();
            assertEquals("svg", root.getTagName(), resource + " must have an <svg> root");

            String[] viewBox = root.getAttribute("viewBox").trim().split("\\s+");
            assertEquals(4, viewBox.length, resource + " needs a viewBox");
            // IconResourceManager draws into a size x size square, so a non-square viewBox squashes.
            assertEquals(Double.parseDouble(viewBox[2]), Double.parseDouble(viewBox[3]),
                    resource + " must be square or the toolbar icon is distorted");

            assertEquals(0, doc.getElementsByTagName("div").getLength(),
                    resource + " must not wrap the <svg> in a <div> — SVGLoader rejects it");
        }
    }

    @Test
    void darkVariantKeepsTheLeavesAndLightensTheOutlines() throws Exception {
        String light = read(LIGHT);
        String dark = read(DARK);

        assertTrue(light.contains(MOUND), "light variant should keep the brown mound");
        assertTrue(light.contains(LEAF), "light variant should keep the leaves");
        assertTrue(dark.contains(LEAF), "dark variant should keep the leaves");
        assertTrue(dark.contains(DARK_OUTLINE), "dark variant needs a light outline");
        assertFalse(dark.contains(MOUND), "dark variant must not keep the dark brown mound");
    }

    private static Document parse(String resource) throws Exception {
        URL url = SproutIconResourceTest.class.getResource(resource);
        assertNotNull(url, resource + " is missing from the classpath");
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(url.openStream());
    }

    private static String read(String resource) throws Exception {
        URL url = SproutIconResourceTest.class.getResource(resource);
        assertNotNull(url, resource + " is missing from the classpath");
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
