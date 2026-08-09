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

import static org.apache.commons.lang3.StringUtils.isNotBlank;


public final class WireLogger implements Closeable {
    private static final Logger LOG = Logger.from(WireLogger.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private final boolean wireLoggingEnabled;
    private final BufferedWriter wireLogWriter;

    public WireLogger() {
        String wireLogFileName = null;
        BufferedWriter writer = null;
        try {
            wireLogFileName = System.getenv("ACP_WIRE_LOG");
            if (isNotBlank(wireLogFileName)) {
                Path logPath = Paths.get(wireLogFileName);
                Path parentDir = logPath.getParent();
                if (parentDir != null) {
                    Files.createDirectories(parentDir);
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
                wireLogWriter.write(json);
                wireLogWriter.write("\n");
                wireLogWriter.flush();
            } catch (IOException e) {
                LOG.warn("Couldn't write to wire log", e);
            }
        }
    }

    public synchronized void log(JsonNode node) {
        if (wireLoggingEnabled) {
            try {
                wireLogWriter.write(MAPPER.writeValueAsString(node));
                wireLogWriter.write("\n");
                wireLogWriter.flush();
            } catch (IOException e) {
                LOG.warn("Couldn't write to wire log", e);
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.wireLogWriter != null) {
            this.wireLogWriter.close();
        }
    }
}
