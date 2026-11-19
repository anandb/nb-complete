package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;

import github.anandb.netbeans.support.ExtraMcpServers;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.UiTestSupport;
import github.anandb.netbeans.ui.components.UIUtils;

/** Guards the Extra MCP Servers editor on the General page. */
class ACPOptionsPanelExtraMcpTest {

    private String saved;

    @BeforeEach
    void setUp() {
        UiTestSupport.assumeGraphicsStack();
        saved = PluginSettings.getExtraMcpServers();
    }

    @AfterEach
    void tearDown() {
        PluginSettings.setExtraMcpServers(saved);
    }

    @Test
    void loadShowsTheStoredConfiguration() throws Exception {
        PluginSettings.setExtraMcpServers(ExtraMcpServers.HTTP_EXAMPLE);
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());

        panel.load();

        assertEquals(ExtraMcpServers.HTTP_EXAMPLE.trim(), area(panel).getText());
    }

    @Test
    void areaUsesJsonHighlighting() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());

        assertEquals(SyntaxConstants.SYNTAX_STYLE_JSON, area(panel).getSyntaxEditingStyle());
    }

    @Test
    void tokenPaletteAvoidsPinkStrings() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());

        Color strings = area(panel).getSyntaxScheme()
                .getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground;

        assertFalse(strings.getRed() > 150 && strings.getBlue() > 80 && strings.getGreen() < 100,
                "string literals must not use RSyntax's pink default: " + strings);
    }

    @Test
    void editorUsesTheChatFont() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());

        assertEquals(UIUtils.chatFont(), area(panel).getFont());
    }

    @Test
    void editorShowsLineNumbers() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());

        assertTrue(scroll(panel).getLineNumbersEnabled());
    }

    @Test
    void validationLabelTracksTheDraft() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        RSyntaxTextArea area = area(panel);

        SwingUtilities.invokeAndWait(() -> area.setText(ExtraMcpServers.HTTP_EXAMPLE));
        JLabel valid = validationLabel(panel);
        assertTrue(valid.getText().startsWith("Valid JSON"), valid.getText());
        assertTrue(valid.getText().contains("1 server"), valid.getText());
        assertNotNull(valid.getIcon());

        SwingUtilities.invokeAndWait(() -> area.setText("[{\"type\":\"http\"}]"));
        JLabel invalid = validationLabel(panel);
        assertTrue(invalid.getText().startsWith("Invalid JSON"), invalid.getText());
        assertNotNull(invalid.getIcon());
        assertNotNull(invalid.getToolTipText(), "the full parse error belongs in the tooltip");
    }

    @Test
    void blankDraftReportsNoServers() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        RSyntaxTextArea area = area(panel);

        SwingUtilities.invokeAndWait(() -> area.setText(" "));

        assertEquals("No extra MCP servers", validationLabel(panel).getText());
    }

    @Test
    void storePersistsAValidConfiguration() throws Exception {
        PluginSettings.setExtraMcpServers("");
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        area(panel).setText(ExtraMcpServers.STDIO_EXAMPLE);
        panel.store();

        assertEquals(1, ExtraMcpServers.parse(PluginSettings.getExtraMcpServers()).size());
    }

    @Test
    void storeKeepsThePreviousValueWhenTheDraftIsInvalid() throws Exception {
        PluginSettings.setExtraMcpServers(ExtraMcpServers.HTTP_EXAMPLE);
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        area(panel).setText("[{\"type\":\"http\"}]");
        panel.store();

        assertEquals(ExtraMcpServers.HTTP_EXAMPLE.trim(), PluginSettings.getExtraMcpServers());
    }

    @Test
    void insertingAnExampleUndoesBackToThePreviousText() throws Exception {
        PluginSettings.setExtraMcpServers(ExtraMcpServers.HTTP_EXAMPLE);
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        RSyntaxTextArea area = area(panel);
        String before = area.getText();

        String label = NbBundle.getMessage(UIBundle.class, "LBL_InsertStdioMcpExample");
        AbstractButton insert = findButton(panel, label);
        assertNotNull(insert, label);
        SwingUtilities.invokeAndWait(insert::doClick);
        assertTrue(area.getText().contains("example-stdio"), area.getText());

        SwingUtilities.invokeAndWait(area::undoLastAction);

        assertEquals(before, area.getText());
    }

    @Test
    void loadLeavesNoUndoableLoadStep() throws Exception {
        PluginSettings.setExtraMcpServers(ExtraMcpServers.HTTP_EXAMPLE);
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        RSyntaxTextArea area = area(panel);

        area.undoLastAction();

        assertEquals(ExtraMcpServers.HTTP_EXAMPLE.trim(), area.getText());
    }

    @Test
    void generalPageOffersBothExampleButtons() {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());

        List<String> labels = new ArrayList<>();
        collectButtons(panel, labels);

        assertTrue(labels.contains(NbBundle.getMessage(UIBundle.class, "LBL_InsertHttpMcpExample")));
        assertTrue(labels.contains(NbBundle.getMessage(UIBundle.class, "LBL_InsertStdioMcpExample")));
    }

    private static RSyntaxTextArea area(ACPOptionsPanel panel) throws Exception {
        Field field = ACPOptionsPanel.class.getDeclaredField("extraMcpServersArea");
        field.setAccessible(true);
        RSyntaxTextArea area = (RSyntaxTextArea) field.get(panel);
        assertNotNull(area);
        return area;
    }

    private static RTextScrollPane scroll(ACPOptionsPanel panel) throws Exception {
        Field field = ACPOptionsPanel.class.getDeclaredField("extraMcpScroll");
        field.setAccessible(true);
        RTextScrollPane scroll = (RTextScrollPane) field.get(panel);
        assertNotNull(scroll);
        return scroll;
    }

    private static JLabel validationLabel(ACPOptionsPanel panel) throws Exception {
        Field field = ACPOptionsPanel.class.getDeclaredField("extraMcpValidationLabel");
        field.setAccessible(true);
        JLabel label = (JLabel) field.get(panel);
        assertNotNull(label);
        return label;
    }

    private static AbstractButton findButton(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton button && label.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container nested) {
                AbstractButton found = findButton(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void collectButtons(Container container, List<String> labels) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton button) {
                labels.add(button.getText());
            }
            if (child instanceof Container nested) {
                collectButtons(nested, labels);
            }
        }
    }
}
