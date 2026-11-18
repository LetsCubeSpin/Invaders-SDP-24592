// 오프닝은 실제 화면을 실행하고 나머지 장면은 맡겨 둔 장면으로 넘기는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DialogueStoryScenesTest {

    @Test
    void openingRunsTheOpeningScreen() {
        AtomicInteger runs = new AtomicInteger();
        DialogueStoryScenes scenes = new DialogueStoryScenes(
                runs::incrementAndGet, new PlaceholderStoryScenes("TRUCE"));
        scenes.showOpening();
        assertEquals(1, runs.get());
    }

    @Test
    void questionsAndEndingsGoToTheOtherScenes() {
        DialogueStoryScenes scenes = new DialogueStoryScenes(() -> { },
                new PlaceholderStoryScenes("TRUCE"));
        assertEquals(StoryRoute.TRUCE.getFirstChoice(), scenes.askFirst());
        assertEquals(StoryRoute.TRUCE.getSecondChoice(),
                scenes.askSecond(StoryRoute.TRUCE.getFirstChoice()));
        scenes.showEnding(StoryRoute.TRUCE);
    }

    @Test
    void rejectsMissingParts() {
        assertThrows(IllegalArgumentException.class,
                () -> new DialogueStoryScenes(null,
                        new PlaceholderStoryScenes()));
        assertThrows(IllegalArgumentException.class,
                () -> new DialogueStoryScenes(() -> { }, null));
    }
}
