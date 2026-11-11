package engine;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import story.StoryProgress;
import story.StoryRoute;
import story.StoryScenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreStoryFlowTest {

    /** Records which questions were asked and answers with one route. */
    private static final class RecordingScenes implements StoryScenes {
        private final StoryRoute route;
        private final List<String> asked = new ArrayList<String>();

        RecordingScenes(final StoryRoute route) {
            this.route = route;
        }

        @Override
        public void showOpening() {
            this.asked.add("opening");
        }

        @Override
        public int askFirst() {
            this.asked.add("first");
            return this.route.getFirstChoice();
        }

        @Override
        public int askSecond(final int firstChoice) {
            this.asked.add("second:" + firstChoice);
            return this.route.getSecondChoice();
        }

        @Override
        public void showEnding(final StoryRoute finished) {
            this.asked.add("ending");
        }
    }

    @Test
    void firstQuestionComesAfterStageOne() {
        RecordingScenes scenes = new RecordingScenes(StoryRoute.TRUCE);
        StoryProgress progress = new StoryProgress();
        Core.askStoryQuestion(1, progress, scenes);
        assertEquals(List.of("first"), scenes.asked);
        assertTrue(progress.hasFirstChoice());
        assertFalse(progress.hasSecondChoice());
    }

    @Test
    void secondQuestionComesAfterStageThreeWithTheFirstAnswer() {
        RecordingScenes scenes = new RecordingScenes(StoryRoute.HOMECOMING);
        StoryProgress progress = new StoryProgress();
        Core.askStoryQuestion(1, progress, scenes);
        Core.askStoryQuestion(3, progress, scenes);
        assertEquals(List.of("first", "second:1"), scenes.asked);
        assertEquals(StoryRoute.HOMECOMING, progress.getRoute());
    }

    @Test
    void otherStagesAskNothing() {
        RecordingScenes scenes = new RecordingScenes(StoryRoute.HYBRID);
        StoryProgress progress = new StoryProgress();
        for (int stage : new int[] {2, 4, 5, 6, 7})
            Core.askStoryQuestion(stage, progress, scenes);
        assertTrue(scenes.asked.isEmpty());
        assertFalse(progress.hasFirstChoice());
    }

    @Test
    void clearingEveryStageDecidesEachRoute() {
        for (StoryRoute route : StoryRoute.values()) {
            RecordingScenes scenes = new RecordingScenes(route);
            StoryProgress progress = new StoryProgress();
            for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++)
                Core.askStoryQuestion(stage, progress, scenes);
            assertEquals(route, progress.getRoute());
            assertEquals(2, scenes.asked.size(), route.toString());
        }
    }
}
