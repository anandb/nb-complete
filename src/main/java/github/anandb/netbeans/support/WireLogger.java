package github.anandb.netbeans.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


public final class WireLogger implements Closeable {
    private static final Logger LOG = Logger.from(WireLogger.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();
    /** Local wall-clock time, no zone or offset, printed before every logged message. */
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final boolean wireLoggingEnabled;
    private final BufferedWriter wireLogWriter;

    /**
     * Production constructor.
     *
     * @param harnessId the harness the log belongs to, already reduced to a safe
     *                  filename token by its producer; null or blank logs as
     *                  {@code unknown}
     */
    public WireLogger(String harnessId) {
        this(System.getenv("ACP_WIRE_LOG"), harnessId);
    }

    /**
     * Test constructor: names the log from {@code template} instead of the
     * {@code ACP_WIRE_LOG} environment variable.
     */
    WireLogger(String template, String harnessId) {
        String wireLogFileName = null;
        BufferedWriter writer = null;
        try {
            if (isNotBlank(template)) {
                Path templatePath = Paths.get(template);
                String baseName = templatePath.getFileName().toString();
                int dotIdx = baseName.lastIndexOf('.');
                if (dotIdx > 0) {
                    baseName = baseName.substring(0, dotIdx);
                }

                String harness = defaultIfBlank(harnessId, "unknown");
                wireLogFileName = baseName + "_"
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                        + "-" + harness + ".log";
                Path parentDir = templatePath.getParent();
                if (parentDir != null) {
                    Files.createDirectories(parentDir);
                    wireLogFileName = parentDir.resolve(wireLogFileName).toString();
                }
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(wireLogFileName, true), StandardCharsets.UTF_8));
            }
        } catch (FileNotFoundException ex) {
            LOG.warn("Couldn't open Wire Log for writing: " + wireLogFileName, ex);
        } catch (IOException ex) {
            LOG.warn("Couldn't create parent directories for Wire Log: " + wireLogFileName, ex);
        }
        this.wireLogWriter = writer;
        this.wireLoggingEnabled = (writer != null);
    }

    public synchronized void log(String json) {
        if (wireLoggingEnabled) {
            try {
                writeLine(json);
            } catch (IOException e) {
                LOG.warn("Couldn't write to wire log", e);
            }
        }
    }

    public synchronized void log(JsonNode node) {
        if (wireLoggingEnabled) {
            try {
                writeLine(MAPPER.writeValueAsString(node));
            } catch (IOException e) {
                LOG.warn("Couldn't write to wire log", e);
            }
        }
    }

    /** Writes one entry as {@code <local time> <json>} followed by a newline. */
    private void writeLine(String json) throws IOException {
        wireLogWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        wireLogWriter.write(' ');
        wireLogWriter.write(json);
        wireLogWriter.write('\n');
        wireLogWriter.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.wireLogWriter != null) {
            this.wireLogWriter.close();
        }
    }
}
