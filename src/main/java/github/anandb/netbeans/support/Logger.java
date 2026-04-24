package github.anandb.netbeans.support;

import java.util.logging.Level;

/**
 * A shorthand wrapper for java.util.logging.Logger to improve readability.
 */
public class Logger {
    private final java.util.logging.Logger log;

    public Logger(Class<?> clazz) {
        this.log = java.util.logging.Logger.getLogger(clazz.getName());
    }

    public void info(String msg, Object... args) {
        log.log(Level.INFO, msg, args);
    }

    public void warn(String msg, Object... args) {
        log.log(Level.WARNING, msg, args);
    }

    public void severe(String msg, Object... args) {
        log.log(Level.SEVERE, msg, args);
    }

    public void fine(String msg, Object... args) {
        log.log(Level.FINE, msg, args);
    }

    public void log(Level level, String msg, Object... args) {
        log.log(level, msg, args);
    }
}
