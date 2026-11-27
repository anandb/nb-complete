package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import javax.swing.JTextField;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.UiTestSupport;

/** The Message Saving group must show the stored folder and write edits back. */
class ACPOptionsPanelSaveDirectoryTest {

    private String previous;

    @BeforeEach
    void setUp() {
        UiTestSupport.assumeGraphicsStack();
        previous = PluginSettings.getMessageSaveDirectory();
    }

    @AfterEach
    void tearDown() {
        PluginSettings.setMessageSaveDirectory(previous);
    }

    @Test
    void loadShowsStoredDirectory() throws Exception {
        PluginSettings.setMessageSaveDirectory("/tmp/beanbot-saved");

        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        assertEquals("/tmp/beanbot-saved", saveDirectoryField(panel).getText());
    }

    @Test
    void storePersistsEditedDirectory() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        saveDirectoryField(panel).setText("/tmp/beanbot-edited");

        panel.store();

        assertEquals("/tmp/beanbot-edited", PluginSettings.getMessageSaveDirectory());
    }

    private static JTextField saveDirectoryField(ACPOptionsPanel panel) throws Exception {
        Field field = ACPOptionsPanel.class.getDeclaredField("saveDirectoryField");
        field.setAccessible(true);
        return (JTextField) field.get(panel);
    }
}
