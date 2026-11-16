package story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceholderStoryScenesTest {

    @Test
    void noRouteNameMeansHybrid() {
        assertEquals(StoryRoute.HYBRID,
                new PlaceholderStoryScenes(null).getRoute());
        assertEquals(StoryRoute.HYBRID,
                new PlaceholderStoryScenes("  ").getRoute());
    }

    @Test
    void routeNameIgnoresCaseAndSpaces() {
        assertEquals(StoryRoute.TRUCE,
                new PlaceholderStoryScenes(" truce ").getRoute());
        assertEquals(StoryRoute.HOMECOMING,
                new PlaceholderStoryScenes("HomeComing").getRoute());
    }

    @Test
    void unknownRouteNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceholderStoryScenes("SECRET"));
    }

    @Test
    void answersLeadToTheChosenRoute() {
        for (StoryRoute route : StoryRoute.values()) {
            StoryScenes scenes = new PlaceholderStoryScenes(route.name());
            StoryProgress progress = new StoryProgress();
            progress.chooseFirst(scenes.askFirst());
            progress.chooseSecond(scenes.askSecond(progress.getFirstChoice()));
            assertEquals(route, progress.getRoute());
        }
    }

    @Test
    void secondQuestionChecksTheFirstAnswer() {
        StoryScenes scenes = new PlaceholderStoryScenes("HYBRID");
        assertThrows(IllegalArgumentException.class,
                () -> scenes.askSecond(1));
    }

    @Test
    void scenesWithoutAnswersDoNotFail() {
        StoryScenes scenes = new PlaceholderStoryScenes("TRUCE");
        assertDoesNotThrow(scenes::showOpening);
        assertDoesNotThrow(() -> scenes.showEnding(StoryRoute.TRUCE));
        assertThrows(IllegalArgumentException.class,
                () -> scenes.showEnding(null));
    }
}
