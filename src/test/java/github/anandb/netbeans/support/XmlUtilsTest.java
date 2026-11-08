package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XmlUtilsTest {

    @Test
    void escapesElementContentAndCdataDelimiter() {
        assertEquals("a &lt;b> &amp; c ]]&gt;", XmlUtils.escapeHtml("a <b> & c ]]>"));
        assertEquals("a &lt;b> &amp; c ]]&gt;", XmlUtils.escapeXml("a <b> & c ]]>"));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", XmlUtils.escapeHtml(null));
        assertEquals("", XmlUtils.escapeXml(null));
    }

    @Test
    void invalidXmlControlFallsBackInsteadOfThrowing() {
        assertEquals("a\u0001b", XmlUtils.escapeHtml("a\u0001b"));
    }
}
