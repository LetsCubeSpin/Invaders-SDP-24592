package audio;

import event.EndingReachedEvent;
import event.GameplayEventPublisher;
import org.junit.jupiter.api.Test;
import story.Ending;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndingBgmTest {

    @Test
    void everyEndingHasMusicThatExistsAndLoads() throws Exception {
        AudioCache cache = new AudioCache();
        for (Ending ending : Ending.values()) {
            String path = EndingBgm.pathFor(ending);
            assertNotNull(getClass().getClassLoader().getResource(path),
                    "missing music for " + ending + ": " + path);
            assertNotNull(cache.getAudioData(path), "music of " + ending);
        }
    }

    @Test
    void eachEndingEventPlaysThatEndingsMusic() {
        List<String> played = new ArrayList<String>();
        GameplayEventPublisher events = new GameplayEventPublisher();
        events.subscribe(EndingReachedEvent.class, new EndingBgm(played::add));

        for (Ending ending : Ending.values())
            events.publish(new EndingReachedEvent(ending, true));
        events.publish(new EndingReachedEvent(Ending.ENDING2, false));

        List<String> expected = new ArrayList<String>();
        for (Ending ending : Ending.values())
            expected.add(EndingBgm.pathFor(ending));
        expected.add(EndingBgm.pathFor(Ending.ENDING2));
        assertEquals(expected, played, "music plays every time, not only the first");
    }

    @Test
    void rejectsMissingValues() {
        assertThrows(IllegalArgumentException.class, () -> EndingBgm.pathFor(null));
        assertThrows(IllegalArgumentException.class, () -> new EndingBgm(null));
    }
}
