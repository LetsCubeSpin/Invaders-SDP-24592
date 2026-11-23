package engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/** Uses a locale-independent header for game and test logs. */
public class MinimalFormatter extends Formatter {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    @Override
    public final String format(final LogRecord record) {
        String source = record.getSourceClassName();
        if (source == null) {
            source = record.getLoggerName();
        }
        StringBuilder output = new StringBuilder()
                .append(TIMESTAMP.format(record.getInstant().atZone(ZoneId.systemDefault())))
                .append(" [").append(record.getLevel().getName()).append("] ");
        if (source != null) {
            output.append(source).append(": ");
        }
        output.append(formatMessage(record)).append(System.lineSeparator());
        if (record.getThrown() != null) {
            StringWriter trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            output.append(trace);
        }
        return output.toString();
    }
}
