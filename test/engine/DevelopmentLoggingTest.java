package engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentLoggingTest {
    @TempDir
    Path temp;

    @Test
    void explicitConfigurationTakesPriorityOverTheDevelopmentDefault() throws Exception {
        Path config = temp.resolve("custom logging.properties");
        Files.writeString(config, """
                handlers = java.util.logging.ConsoleHandler
                .level = FINE
                java.util.logging.ConsoleHandler.level = FINE
                java.util.logging.SimpleFormatter.format = CUSTOM %4$s %5$s%n
                """);

        String defaults = runProbe(List.of(), "default.log");
        assertTrue(defaults.contains("[INFO] engine.DevelopmentLoggingTest"), defaults);
        assertFalse(defaults.contains("fine probe"), defaults);

        String custom = runProbe(List.of("-Djava.util.logging.config.file=" + config), "custom.log");
        assertTrue(custom.contains("CUSTOM FINE fine probe"), custom);
        assertTrue(custom.contains("CUSTOM INFO info probe"), custom);
    }

    private String runProbe(final List<String> options, final String fileName) throws Exception {
        String java = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", java).toString(),
                "-Duser.language=en", "-Duser.country=US",
                "-Djava.util.logging.config.class=" + DevelopmentLogging.class.getName()));
        command.addAll(options);
        command.addAll(List.of("-cp", System.getProperty("invaders.test.classpath",
                System.getProperty("java.class.path")), Probe.class.getName()));
        Path output = temp.resolve(fileName);
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Logging probe did not exit");
            String logs = Files.readString(output);
            assertEquals(0, process.exitValue(), logs);
            return logs;
        } finally {
            process.destroyForcibly();
        }
    }

    public static class Probe {
        public static void main(final String[] args) {
            Logger logger = Logger.getLogger(Probe.class.getName());
            logger.fine("fine probe");
            logger.info("info probe");
        }
    }
}
