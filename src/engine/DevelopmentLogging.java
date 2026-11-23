package engine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.LogManager;

/** Configures the default console handler for development JVMs at startup. */
public final class DevelopmentLogging {
    public DevelopmentLogging() throws IOException {
        String configuration = """
                handlers = java.util.logging.ConsoleHandler
                .level = INFO
                java.util.logging.ConsoleHandler.level = INFO
                java.util.logging.ConsoleHandler.formatter = %s
                """.formatted(MinimalFormatter.class.getName());
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(configuration.getBytes(StandardCharsets.US_ASCII)));
    }
}
