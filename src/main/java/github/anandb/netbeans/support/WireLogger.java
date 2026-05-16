package github.anandb.netbeans.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


public final class WireLogger implements Closeable {
    private static final Logger LOG = new Logger(WireLogger.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private final boolean wireLoggingEnabled;
    private BufferedWriter wireLogWriter;

    public WireLogger() {
        String wireLogFileName = null;
        try {
            wireLogFileName = System.getenv("ACP_WIRE_LOG");
            if (isNotBlank(wireLogFileName)) {
                this.wireLogWriter = new BufferedWriter(new PrintWriter(
                    new FileOutputStream(wireLogFileName, true), true, StandardCharsets.UTF_8
                ));
            }
        } catch (FileNotFoundException ex) {
            LOG.warn("Couldn't open Wire Log for writing {0}", wireLogFileName);
        } finally {
            this.wireLoggingEnabled = (this.wireLogWriter != null);
        }
    }

    public void log(String json) {
        if (wireLoggingEnabled) {
            try {
                wireLogWriter.write(json);
                wireLogWriter.write("\n");
                wireLogWriter.flush();
            } catch (Exception e) {
                LOG.warn("Couldn't write to wire log", e);
            }
        }
    }

    public void log(JsonNode node) {
        if (wireLoggingEnabled) {
            try {
                wireLogWriter.write(MAPPER.writeValueAsString(node));
                wireLogWriter.write("\n");
                wireLogWriter.flush();
            } catch (Exception e) {
                LOG.warn("Couldn't write to wire log", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (this.wireLogWriter != null) {
            this.wireLogWriter.close();
        }
    }
}
