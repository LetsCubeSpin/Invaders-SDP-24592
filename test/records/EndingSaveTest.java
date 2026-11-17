package records;

import event.AllEndingsSeenEvent;
import event.EndingReachedEvent;
import event.GameplayEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import story.Ending;
import story.EndingCollection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndingSaveTest {

    @TempDir Path temp;

    private Path profile() {
        return temp.resolve("profile.properties");
    }

    @Test
    void seenEndingsAreStillSeenAfterARestart() {
        RecordsService records = new RecordsService(profile());
        assertTrue(records.endingSeen(Ending.ENDING2));
        assertTrue(records.endingSeen(Ending.ENDING4));
        records.close();

        RecordsService restarted = new RecordsService(profile());
        try {
            assertEquals(EnumSet.of(Ending.ENDING2, Ending.ENDING4),
                    restarted.getSeenEndings());
        } finally {
            restarted.close();
        }
    }

    @Test
    void aSeenEndingIsSavedOnlyOnce() {
        RecordsService records = new RecordsService(profile());
        try {
            assertTrue(records.endingSeen(Ending.ENDING1));
            assertFalse(records.endingSeen(Ending.ENDING1));
            assertEquals(EnumSet.of(Ending.ENDING1), records.getSeenEndings());
        } finally {
            records.close();
        }
    }

    @Test
    void aClosedServiceSavesNothing() {
        RecordsService records = new RecordsService(profile());
        records.close();

        assertFalse(records.endingSeen(Ending.ENDING1));
        assertTrue(records.getSeenEndings().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> records.endingSeen(null));
    }

    @Test
    void endingsSeenBeforeARestartCountTowardsTheAchievement() {
        // First session: three endings, wired the way the game is meant to be.
        RecordsService records = new RecordsService(profile());
        EndingCollection endings = connect(records);
        endings.see(Ending.ENDING1);
        endings.see(Ending.ENDING2);
        endings.see(Ending.ENDING3);
        records.close();

        // Second session: the saved endings are restored, then the last one is seen.
        RecordsService restarted = new RecordsService(profile());
        try {
            EndingCollection restored = connect(restarted);
            restored.restore(restarted.getSeenEndings());
            assertEquals(3, restored.getSeenCount());
            assertNull(restarted.getUnlockTime(Achievement.ALL_ENDINGS));

            restored.see(Ending.ENDING4);

            assertTrue(restored.hasSeenAll());
            assertNotNull(restarted.getUnlockTime(Achievement.ALL_ENDINGS));
            assertEquals(EnumSet.allOf(Ending.class), restarted.getSeenEndings());
        } finally {
            restarted.close();
        }
    }

    @Test
    void aProfileSavedBeforeEndingsExistedStillLoads() throws IOException {
        Files.write(profile(), ("version=1\n"
                + "record.count=1\n"
                + "record.0.id=old-run\n"
                + "record.0.score=770\n"
                + "record.0.enemies=42\n"
                + "record.0.duration=60493\n"
                + "record.0.completed=1792477779343\n"
                + "achievement.count=1\n"
                + "achievement.0.id=first_run\n"
                + "achievement.0.unlocked=1792477779343\n")
                .getBytes(StandardCharsets.UTF_8));

        RecordsService records = new RecordsService(profile());
        try {
            assertFalse(records.isWritesBlocked());
            assertEquals(1, records.getRecordCount());
            assertNotNull(records.getUnlockTime(Achievement.FIRST_RUN));
            assertTrue(records.getSeenEndings().isEmpty());
        } finally {
            records.close();
        }
    }

    @Test
    void unknownOrRepeatedEndingsMakeTheProfileInvalid() throws IOException {
        String header = "version=1\nrecord.count=0\nachievement.count=0\n";
        Files.write(profile(), (header + "ending.count=1\nending.0=ENDING9\n")
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> new ProfileStore(profile()).load());

        Path repeated = temp.resolve("repeated.properties");
        Files.write(repeated, (header + "ending.count=2\nending.0=ENDING1\nending.1=ENDING1\n")
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> new ProfileStore(repeated).load());
    }

    /** Subscribes the records service the same way the game is meant to. */
    private static EndingCollection connect(final RecordsService records) {
        GameplayEventPublisher events = new GameplayEventPublisher();
        events.subscribe(EndingReachedEvent.class, e -> records.endingSeen(e.getEnding()));
        events.subscribe(AllEndingsSeenEvent.class, e -> records.allEndingsSeen());
        return new EndingCollection(events);
    }
}
