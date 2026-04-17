package github.anandb.netbeans.manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.modules.Places;

public class SessionTitleManager {
    private static final Logger LOG = Logger.getLogger(SessionTitleManager.class.getName());
    private static final String TITLES_FILE = "acp_session_titles.properties";
    private static final Properties titles = new Properties();

    static {
        load();
    }

    private static File getStorageFile() {
        File userDir = Places.getUserDirectory();
        File opencodeDir;
        if (userDir != null) {
            opencodeDir = new File(userDir, "acp");
        } else {
            opencodeDir = new File(System.getProperty("user.home"), ".opencode");
        }
        if (!opencodeDir.exists()) {
            opencodeDir.mkdirs();
        }
        return new File(opencodeDir, TITLES_FILE);
    }

    public static synchronized void setTitle(String sessionId, String title) {
        titles.setProperty(sessionId, title);
        save();
    }

    public static synchronized String getTitle(String sessionId, String defaultTitle) {
        return titles.getProperty(sessionId, defaultTitle);
    }

    private static void load() {
        File file = getStorageFile();
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                titles.load(is);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load session titles: {0}", e.getMessage());
            }
        }
    }

    private static void save() {
        File file = getStorageFile();
        try (OutputStream os = new FileOutputStream(file)) {
            titles.store(os, "ACP Session Titles");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to save session titles: {0}", e.getMessage());
        }
    }
}
