package story;

import event.AllEndingsSeenEvent;
import event.EndingReachedEvent;
import event.GameplayEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndingCollectionTest {

    private final GameplayEventPublisher events = new GameplayEventPublisher();
    private final List<EndingReachedEvent> received =
            new ArrayList<EndingReachedEvent>();
    private final List<AllEndingsSeenEvent> completed =
            new ArrayList<AllEndingsSeenEvent>();
    private final EndingCollection endings = new EndingCollection(events);

    EndingCollectionTest() {
        events.subscribe(EndingReachedEvent.class, received::add);
        events.subscribe(AllEndingsSeenEvent.class, completed::add);
    }

    @Test
    void thereAreFourEndingsNumberedOneToFour() {
        assertEquals(4, Ending.values().length);
        Set<Integer> numbers = new HashSet<Integer>();
        for (Ending ending : Ending.values()) {
            assertTrue(numbers.add(ending.getNumber()),
                    "Repeated ending " + ending.getNumber());
            assertSame(ending, Ending.fromNumber(ending.getNumber()));
        }
        assertEquals(new HashSet<Integer>(Arrays.asList(1, 2, 3, 4)), numbers);
        assertThrows(IllegalArgumentException.class, () -> Ending.fromNumber(5));
    }

    @Test
    void startsWithNoEndingSeen() {
        assertTrue(endings.getSeen().isEmpty());
        assertEquals(0, endings.getSeenCount());
        assertFalse(endings.hasSeenAll());
        for (Ending ending : Ending.values())
            assertFalse(endings.hasSeen(ending));
    }

    @Test
    void seeingAnEndingStoresItAndPublishesAnEvent() {
        assertTrue(endings.see(Ending.ENDING4));

        assertTrue(endings.hasSeen(Ending.ENDING4));
        assertFalse(endings.hasSeen(Ending.ENDING1));
        assertEquals(1, received.size());
        assertSame(Ending.ENDING4, received.get(0).getEnding());
        assertTrue(received.get(0).isFirstTime());
    }

    @Test
    void seeingAnEndingAgainPublishesAgainButNotAsFirstTime() {
        endings.see(Ending.ENDING3);

        assertFalse(endings.see(Ending.ENDING3));

        assertEquals(1, endings.getSeenCount());
        assertEquals(2, received.size());
        assertSame(Ending.ENDING3, received.get(1).getEnding());
        assertFalse(received.get(1).isFirstTime());
    }

    @Test
    void seeingEveryEndingCompletesTheCollection() {
        for (Ending ending : Ending.values())
            endings.see(ending);

        assertTrue(endings.hasSeenAll());
        assertEquals(EnumSet.allOf(Ending.class), endings.getSeen());
    }

    @Test
    void theLastNewEndingPublishesAllEndingsSeenOnce() {
        Ending[] all = Ending.values();
        for (int i = 0; i < all.length - 1; i++) {
            endings.see(all[i]);
            endings.see(all[i]);
        }
        assertTrue(completed.isEmpty(), "not every ending seen yet");

        endings.see(all[all.length - 1]);
        assertEquals(1, completed.size());

        for (Ending ending : all)
            endings.see(ending);
        assertEquals(1, completed.size(), "seeing endings again adds nothing");
    }

    @Test
    void endingsRestoredFromASaveCountTowardsAllEndingsSeen() {
        Ending[] all = Ending.values();
        endings.restore(Arrays.asList(all).subList(0, all.length - 1));
        assertTrue(completed.isEmpty());

        endings.see(all[all.length - 1]);
        assertEquals(1, completed.size());
    }

    @Test
    void aFullyRestoredCollectionDoesNotPublishAllEndingsSeenAgain() {
        endings.restore(Arrays.asList(Ending.values()));

        endings.see(Ending.ENDING1);

        assertTrue(endings.hasSeenAll());
        assertTrue(completed.isEmpty());
    }

    @Test
    void restoringASaveMarksEndingsWithoutEvents() {
        endings.restore(Arrays.asList(Ending.ENDING1, Ending.ENDING4));

        assertTrue(endings.hasSeen(Ending.ENDING1));
        assertTrue(endings.hasSeen(Ending.ENDING4));
        assertTrue(received.isEmpty());
        assertFalse(endings.see(Ending.ENDING1), "restored ending is not new");
        assertTrue(endings.see(Ending.ENDING2));
    }

    @Test
    void seenEndingsCannotBeChangedFromOutside() {
        endings.see(Ending.ENDING1);
        Set<Ending> seen = endings.getSeen();

        assertThrows(UnsupportedOperationException.class,
                () -> seen.add(Ending.ENDING4));
        endings.see(Ending.ENDING4);
        assertEquals(EnumSet.of(Ending.ENDING1), seen, "a copy, not a view");
    }

    @Test
    void rejectsMissingValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new EndingCollection(null));
        assertThrows(IllegalArgumentException.class, () -> endings.see(null));
        assertThrows(IllegalArgumentException.class,
                () -> endings.restore(null));
        assertThrows(IllegalArgumentException.class,
                () -> endings.restore(Arrays.asList(Ending.ENDING1, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new EndingReachedEvent(null, true));
        assertTrue(received.isEmpty());
        assertFalse(endings.hasSeen(Ending.ENDING1), "bad restore adds nothing");
    }
}
