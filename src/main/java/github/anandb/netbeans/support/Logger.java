package github.anandb.netbeans.support;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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
        doLog(Level.INFO, msg, args);
    }

    public void warn(String msg, Object... args) {
        doLog(Level.WARNING, msg, args);
    }

    public void severe(String msg, Object... args) {
        doLog(Level.SEVERE, msg, args);
    }

    public void fine(String msg, Object... args) {
        doLog(Level.FINE, msg, args);
    }

    public void log(Level level, String msg, Object... args) {
        doLog(level, msg, args);
    }

    /**
     * Routes the call to the correct java.util.logging.Logger overload.
     *
     * <p>The varargs {@code log(Level, String, Object...)} overload stores args
     * as {@link LogRecord#getParameters() LogRecord parameters} — it does NOT
     * detect a trailing {@code Throwable}. So a call like {@code LOG.warn("msg", e)}
     * would lose the stack trace (the exception never reaches
     * {@link LogRecord#getThrown()}). Per the project convention (exceptions are
     * passed as the LAST argument with NO placeholder), detect a trailing
     * Throwable here and forward it via the {@code log(Level, String, Throwable)}
     * overload (or a {@link LogRecord} when format args are also present).</p>
     */
    private void doLog(Level level, String msg, Object... args) {
        String fullMsg = prefix() + msg;
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable) {
            Throwable thrown = (Throwable) args[args.length - 1];
            Object[] formatArgs = args.length == 1 ? new Object[0] : Arrays.copyOf(args, args.length - 1);
            if (formatArgs.length == 0) {
                acpLogger.log(level, fullMsg, thrown);
            } else {
                LogRecord rec = new LogRecord(level, fullMsg);
                rec.setParameters(formatArgs);
                rec.setThrown(thrown);
                acpLogger.log(rec);
            }
        } else {
            acpLogger.log(level, fullMsg, args);
        }
    }
}
