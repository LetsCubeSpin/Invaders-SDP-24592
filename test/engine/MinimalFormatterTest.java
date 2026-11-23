package engine;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimalFormatterTest {
    @Test
    void formatsLocalTimeAndEnglishLevelWithMessageAndStackTrace() {
        LogRecord record = new LogRecord(Level.WARNING, "Stage {0} failed.");
        record.setInstant(Instant.parse("2026-11-23T09:34:05Z"));
        record.setLoggerName("screen.GameScreen");
        record.setParameters(new Object[] { "7" });
        record.setThrown(new IllegalStateException("test error"));

        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                .format(record.getInstant().atZone(ZoneId.systemDefault()));
        String output = new MinimalFormatter().format(record);
        assertTrue(output.startsWith(time + " [WARNING] screen.GameScreen: Stage 7 failed."
                + System.lineSeparator()));
        assertTrue(output.contains("java.lang.IllegalStateException: test error"));
        assertTrue(output.contains("MinimalFormatterTest.java:"));
    }
}
