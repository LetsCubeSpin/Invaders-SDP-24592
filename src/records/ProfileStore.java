package records;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import story.Ending;

/**
 * Versioned UTF-8 profile storage with validation, backup, and atomic replace.
 *
 * <p>Writes always use version 2. Version 1 profiles, saved before runs kept
 * a mode and play statistics, still load: their records get the
 * {@link RunMode#UNKNOWN} mode and {@link RunStats#EMPTY}. A version 1 file
 * stays valid, so it is backed up as usual before the first version 2 write
 * replaces it.</p>
 */
public final class ProfileStore {
    /** Version written by this class. */
    private static final String VERSION = "2";
    /** Version without the run mode and play statistics. */
    private static final String VERSION_1 = "1";
    private final Path file;
    private final Path backup;
    private String loadWarning;

    public ProfileStore(final Path file) {
        this.file = file;
        this.backup = file.resolveSibling(file.getFileName().toString() + ".bak");
    }

    public ProfileSnapshot load() throws IOException {
        if (!Files.exists(this.file) && !Files.exists(this.backup))
            return new ProfileSnapshot(new ArrayList<RunRecord>(),
                    new LinkedHashMap<String, Long>());
        IOException primaryError = null;
        if (Files.exists(this.file)) {
            try {
                return read(this.file);
            } catch (IOException exception) {
                primaryError = exception;
                Path quarantine = quarantinePath();
                try {
                    Files.copy(this.file, quarantine);
                } catch (IOException copyError) {
                    exception.addSuppressed(copyError);
                }
            }
        }
        if (Files.exists(this.backup)) {
            try {
                ProfileSnapshot recovered = read(this.backup);
                write(recovered);
                this.loadWarning = "Profile was restored from backup; newer progress may be unavailable.";
                return recovered;
            } catch (IOException backupError) {
                if (primaryError != null)
                    backupError.addSuppressed(primaryError);
                throw backupError;
            }
        }
        throw primaryError == null ? new IOException("Profile is unavailable.")
                : primaryError;
    }

    public String getLoadWarning() { return this.loadWarning; }

    public synchronized void write(final ProfileSnapshot snapshot)
            throws IOException {
        Path parent = this.file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Properties properties = new Properties();
        properties.setProperty("version", VERSION);
        properties.setProperty("record.count",
                Integer.toString(snapshot.getRecords().size()));
        int index = 0;
        for (RunRecord record : snapshot.getRecords()) {
            String prefix = "record." + index++ + ".";
            properties.setProperty(prefix + "id", record.getRunId());
            properties.setProperty(prefix + "score", Integer.toString(record.getScore()));
            properties.setProperty(prefix + "enemies", Integer.toString(record.getEnemiesDefeated()));
            properties.setProperty(prefix + "duration", Long.toString(record.getActiveDurationMillis()));
            properties.setProperty(prefix + "completed", Long.toString(record.getCompletionTimeMillis()));
            properties.setProperty(prefix + "mode", record.getMode().name());
            RunStats stats = record.getStats();
            properties.setProperty(prefix + "stage", Integer.toString(stats.getStageReached()));
            properties.setProperty(prefix + "waves", Integer.toString(stats.getWavesReached()));
            properties.setProperty(prefix + "shots", Integer.toString(stats.getShotsFired()));
            properties.setProperty(prefix + "hits", Integer.toString(stats.getHits()));
            properties.setProperty(prefix + "timesHit", Integer.toString(stats.getTimesHit()));
            properties.setProperty(prefix + "bosses", Integer.toString(stats.getBossesDefeated()));
            properties.setProperty(prefix + "items", Integer.toString(stats.getItemsUsed()));
        }
        properties.setProperty("achievement.count",
                Integer.toString(snapshot.getUnlockTimes().size()));
        index = 0;
        for (Map.Entry<String, Long> entry : snapshot.getUnlockTimes().entrySet()) {
            String prefix = "achievement." + index++ + ".";
            properties.setProperty(prefix + "id", entry.getKey());
            properties.setProperty(prefix + "unlocked", Long.toString(entry.getValue()));
        }
        properties.setProperty("ending.count",
                Integer.toString(snapshot.getSeenEndings().size()));
        index = 0;
        for (Ending ending : snapshot.getSeenEndings())
            properties.setProperty("ending." + index++, ending.name());

        Path temporary = Files.createTempFile(parent, "profile-", ".tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary,
                    StandardCharsets.UTF_8)) {
                properties.store(writer, "Space Invaders local profile");
            }
            read(temporary);
            if (Files.exists(this.file)) {
                boolean validCurrent = true;
                try {
                    read(this.file);
                } catch (IOException invalidCurrent) {
                    validCurrent = false;
                }
                if (validCurrent) {
                    // A backup write failure must abort, rather than being
                    // mistaken for corruption and silently discarding protection.
                    Path backupTemporary = Files.createTempFile(parent, "backup-", ".tmp");
                    try {
                        Files.copy(this.file, backupTemporary,
                                StandardCopyOption.REPLACE_EXISTING);
                        read(backupTemporary);
                        replaceFile(backupTemporary, this.backup);
                    } finally {
                        Files.deleteIfExists(backupTemporary);
                    }
                } else {
                    Files.copy(this.file, quarantinePath());
                }
            }
            replaceFile(temporary, this.file);
            moved = true;
        } finally {
            if (!moved)
                Files.deleteIfExists(temporary);
        }
    }

    private static void replaceFile(final Path source, final Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ProfileSnapshot read(final Path path) throws IOException {
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path,
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Profile contains invalid properties.", exception);
        }
        String version = properties.getProperty("version");
        boolean versionOne = VERSION_1.equals(version);
        if (!versionOne && !VERSION.equals(version))
            throw new IOException("Unsupported profile version.");
        int recordCount = count(properties, "record.count", 100000);
        List<RunRecord> records = new ArrayList<RunRecord>();
        Map<String, Boolean> ids = new LinkedHashMap<String, Boolean>();
        for (int i = 0; i < recordCount; i++) {
            String prefix = "record." + i + ".";
            String id = required(properties, prefix + "id");
            if (ids.put(id, Boolean.TRUE) != null)
                throw new IOException("Duplicate run ID in profile.");
            try {
                int score = Integer.parseInt(required(properties, prefix + "score"));
                int enemies = Integer.parseInt(required(properties, prefix + "enemies"));
                long duration = Long.parseLong(required(properties, prefix + "duration"));
                long completed = Long.parseLong(required(properties, prefix + "completed"));
                if (versionOne)
                    records.add(new RunRecord(id, score, enemies, duration, completed));
                else
                    records.add(new RunRecord(id,
                            RunMode.valueOf(required(properties, prefix + "mode")),
                            score, enemies, duration, completed,
                            readStats(properties, prefix)));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid run record in profile.", exception);
            }
        }
        int achievementCount = count(properties, "achievement.count",
                Achievement.values().length);
        Map<String, Long> unlocked = new LinkedHashMap<String, Long>();
        for (int i = 0; i < achievementCount; i++) {
            String prefix = "achievement." + i + ".";
            String id = required(properties, prefix + "id");
            if (Achievement.fromId(id) == null || unlocked.containsKey(id))
                throw new IOException("Unknown or duplicate achievement ID.");
            long time;
            try {
                time = Long.parseLong(required(properties, prefix + "unlocked"));
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid achievement time.", exception);
            }
            if (time <= 0)
                throw new IOException("Invalid achievement time.");
            unlocked.put(id, Long.valueOf(time));
        }
        return new ProfileSnapshot(records, unlocked, readEndings(properties));
    }

    /** Reads the statistics a version 2 record stores. */
    private static RunStats readStats(final Properties properties,
            final String prefix) throws IOException {
        return new RunStats(
                Integer.parseInt(required(properties, prefix + "stage")),
                Integer.parseInt(required(properties, prefix + "waves")),
                Integer.parseInt(required(properties, prefix + "shots")),
                Integer.parseInt(required(properties, prefix + "hits")),
                Integer.parseInt(required(properties, prefix + "timesHit")),
                Integer.parseInt(required(properties, prefix + "bosses")),
                Integer.parseInt(required(properties, prefix + "items")));
    }

    /** Profiles saved before endings were stored have no ending entries. */
    private static Set<Ending> readEndings(final Properties properties)
            throws IOException {
        Set<Ending> endings = EnumSet.noneOf(Ending.class);
        if (properties.getProperty("ending.count") == null)
            return endings;
        int endingCount = count(properties, "ending.count", Ending.values().length);
        for (int i = 0; i < endingCount; i++) {
            Ending ending;
            try {
                ending = Ending.valueOf(required(properties, "ending." + i));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Unknown ending in profile.", exception);
            }
            if (!endings.add(ending))
                throw new IOException("Duplicate ending in profile.");
        }
        return endings;
    }

    private static int count(final Properties properties, final String key,
            final int maximum) throws IOException {
        try {
            int value = Integer.parseInt(required(properties, key));
            if (value < 0 || value > maximum)
                throw new IOException("Invalid count for " + key);
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid count for " + key, exception);
        }
    }

    private static String required(final Properties properties, final String key)
            throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().length() == 0)
            throw new IOException("Missing profile field: " + key);
        return value;
    }

    private Path quarantinePath() {
        return this.file.resolveSibling(this.file.getFileName() + ".corrupt-"
                + UUID.randomUUID().toString());
    }
}
