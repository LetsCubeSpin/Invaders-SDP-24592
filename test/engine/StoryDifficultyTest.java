package engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import story.StoryProgress;
import story.StoryRoute;
import story.StoryStage;
import story.StoryStageData;

/** Checks that story mode is easier than the other modes, and only story mode. */
class StoryDifficultyTest {

    @Test
    void storyIsEasierThanTheOtherModes() {
        assertEquals(1.25, GameplayConfig.getMultiplier());
        assertEquals(1.5, GameplayConfig.getStoryMultiplier());
        assertTrue(GameplayConfig.getStoryMultiplier() > GameplayConfig.getMultiplier(),
                "A larger multiplier means slower, less frequent enemy fire");
        assertEquals(1.2, GameplayConfig.getStoryBossIntervalScale());
    }

    @Test
    void everyStoryStageUsesTheStoryMultiplier() {
        for (StoryRoute route : StoryRoute.values()) {
            StoryProgress progress = new StoryProgress();
            progress.chooseFirst(route.getFirstChoice());
            progress.chooseSecond(route.getSecondChoice());
            for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
                GameSettings stageSettings = StoryStageData.get(stage, progress).getSettings();
                GameSettings story = Core.applyStoryDifficulty(stageSettings);
                GameSettings expected = Core.applyDifficulty(stageSettings, 1.5);
                assertEquals(expected.getBaseSpeed(), story.getBaseSpeed(), route + " stage " + stage);
                assertEquals(expected.getShootingFrecuency(), story.getShootingFrecuency(),
                        route + " stage " + stage);
            }
        }
    }

    @Test
    void storyEnemiesMoveAndShootLessOftenThanBefore() {
        StoryStage first = StoryStageData.get(1, new StoryProgress());
        GameSettings before = Core.applyDifficulty(first.getSettings(), GameplayConfig.getMultiplier());
        GameSettings after = Core.applyStoryDifficulty(first.getSettings());
        assertTrue(after.getBaseSpeed() > before.getBaseSpeed());
        assertTrue(after.getShootingFrecuency() > before.getShootingFrecuency());
    }
}
