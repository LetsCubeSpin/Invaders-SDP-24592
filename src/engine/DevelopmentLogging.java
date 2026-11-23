package engine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.LogManager;

/** Configures the default console handler for development JVMs at startup. */
public final class DevelopmentLogging {
    public DevelopmentLogging() throws IOException {
        LogManager manager = LogManager.getLogManager();
        String configFile = System.getProperty("java.util.logging.config.file");
        if (configFile != null) {
            try (var input = Files.newInputStream(Path.of(configFile))) {
                manager.readConfiguration(input);
            }
            return;
        }

        String configuration = """
                handlers = java.util.logging.ConsoleHandler
                .level = INFO
                java.util.logging.ConsoleHandler.level = INFO
                java.util.logging.ConsoleHandler.formatter = %s
                """.formatted(MinimalFormatter.class.getName());
        manager.readConfiguration(
                new ByteArrayInputStream(configuration.getBytes(StandardCharsets.US_ASCII)));
    }
}
