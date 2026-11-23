package engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import event.EndingReachedEvent;
import event.GameplayEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import records.Achievement;
import records.RecordsService;
import story.Ending;
import story.EndingCollection;
import story.StoryRoute;

/** Checks how Core connects the story endings to the records. */
class CoreEndingTest {

    @TempDir
    Path temp;

    /** Endings whose music was requested, in order. */
    private final List<Ending> music = new ArrayList<Ending>();
    /** Stands in for the ending music so no audio plays. */
    private final GameplayEventListener<EndingReachedEvent> recordingMusic =
            event -> this.music.add(event.getEnding());
    /** Services opened by a test, closed after it. */
    private final List<RecordsService> opened = new ArrayList<RecordsService>();

    private RecordsService open() {
        RecordsService records = new RecordsService(temp.resolve("profile.properties"));
        this.opened.add(records);
        return records;
    }

    @AfterEach
    void closeRecords() {
        for (RecordsService records : this.opened)
            records.close();
    }

    @ParameterizedTest
    @EnumSource(StoryRoute.class)
    void eachRouteRecordsItsOwnEnding(final StoryRoute route) {
        RecordsService records = open();
        EndingCollection endings = Core.createEndingCollection(records, this.recordingMusic);
        Ending expected = Ending.fromNumber(route.getEndingNumber());

        assertTrue(Core.recordEnding(endings, route));

        assertEquals(EnumSet.of(expected), records.getSeenEndings());
        assertEquals(List.of(expected), this.music);
    }

    @Test
    void routesMapToEndingsOneToFour() {
        assertEquals(Ending.ENDING1, Ending.fromNumber(StoryRoute.HYBRID.getEndingNumber()));
        assertEquals(Ending.ENDING2, Ending.fromNumber(StoryRoute.HUMAN_VICTORY.getEndingNumber()));
        assertEquals(Ending.ENDING3, Ending.fromNumber(StoryRoute.HOMECOMING.getEndingNumber()));
        assertEquals(Ending.ENDING4, Ending.fromNumber(StoryRoute.TRUCE.getEndingNumber()));
    }

    @Test
    void seeingAnEndingAgainIsNotNewButStillPlaysItsMusic() {
        RecordsService records = open();
        EndingCollection endings = Core.createEndingCollection(records, this.recordingMusic);

        assertTrue(Core.recordEnding(endings, StoryRoute.TRUCE));
        assertFalse(Core.recordEnding(endings, StoryRoute.TRUCE));

        assertEquals(EnumSet.of(Ending.ENDING4), records.getSeenEndings());
        assertEquals(List.of(Ending.ENDING4, Ending.ENDING4), this.music);
    }

    @Test
    void endingsSavedInAnEarlierSessionAreRestored() {
        RecordsService first = open();
        Core.recordEnding(Core.createEndingCollection(first, this.recordingMusic),
                StoryRoute.HOMECOMING);
        first.close();

        RecordsService restarted = open();
        EndingCollection endings = Core.createEndingCollection(restarted, this.recordingMusic);

        assertTrue(endings.hasSeen(Ending.ENDING3));
        assertFalse(Core.recordEnding(endings, StoryRoute.HOMECOMING));
    }

    @Test
    void allFourEndingsUnlockEveryEnding() {
        RecordsService records = open();
        EndingCollection endings = Core.createEndingCollection(records, this.recordingMusic);

        Core.recordEnding(endings, StoryRoute.HYBRID);
        Core.recordEnding(endings, StoryRoute.HUMAN_VICTORY);
        Core.recordEnding(endings, StoryRoute.HOMECOMING);
        assertNull(records.getUnlockTime(Achievement.ALL_ENDINGS));

        Core.recordEnding(endings, StoryRoute.TRUCE);

        assertTrue(endings.hasSeenAll());
        assertNotNull(records.getUnlockTime(Achievement.ALL_ENDINGS));
        assertEquals(EnumSet.allOf(Ending.class), records.getSeenEndings());
    }

    @Test
    void missingArgumentsAreRejected() {
        RecordsService records = open();
        EndingCollection endings = Core.createEndingCollection(records, this.recordingMusic);

        assertThrows(IllegalArgumentException.class,
                () -> Core.createEndingCollection(null, this.recordingMusic));
        assertThrows(IllegalArgumentException.class,
                () -> Core.createEndingCollection(records, null));
        assertThrows(IllegalArgumentException.class,
                () -> Core.recordEnding(null, StoryRoute.HYBRID));
        assertThrows(IllegalArgumentException.class,
                () -> Core.recordEnding(endings, null));
    }
}
