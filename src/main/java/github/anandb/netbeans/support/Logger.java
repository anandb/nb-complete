package github.anandb.netbeans.support;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * A shorthand wrapper for java.util.logging.Logger to improve readability.
 */
public class Logger {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static String currentSessionId;
    private static String currentSessionName;

    private final java.util.logging.Logger acpLogger;

    private Logger(Class<?> clazz) {
        this.acpLogger = java.util.logging.Logger.getLogger(clazz.getName());
    }

    public static Logger from(Class<?> clazz) {
        return new Logger(clazz);
    }

    public static void setSession(String sessionId, String sessionName) {
        currentSessionId = sessionId;
        currentSessionName = sessionName;
    }

    public static void clearSession() {
        currentSessionId = null;
        currentSessionName = null;
    }

    private static String prefix() {
        String sid = currentSessionId;
        String sname = currentSessionName;
        String sessionPart;
        if (sid != null && sname != null) {
            sessionPart = String.format(" [%s/%s]", sid, sname);
        } else if (sid != null) {
            sessionPart = " [" + sid + "]";
        } else {
            sessionPart = "";
        }
        return String.format("[%s] [T-%d]%s ",
            LocalTime.now().format(TIME_FMT),
            Thread.currentThread().getId(),
            sessionPart);
    }

    public void info(String msg, Object... args) {
        acpLogger.log(Level.INFO, prefix() + msg, args);
    }

    public void warn(String msg, Object... args) {
        acpLogger.log(Level.WARNING, prefix() + msg, args);
    }

    public void severe(String msg, Object... args) {
        acpLogger.log(Level.SEVERE, prefix() + msg, args);
    }

    public void fine(String msg, Object... args) {
        acpLogger.log(Level.FINE, prefix() + msg, args);
    }

    public void log(Level level, String msg, Object... args) {
        acpLogger.log(level, prefix() + msg, args);
    }
}
