package story;

import entity.EnemyShipFormation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryStageDataTest {

    /** Game screen size used by engine.Core. */
    private static final int SCREEN_WIDTH = 448;
    private static final int SCREEN_HEIGHT = 520;

    private static StoryProgress answered(final StoryRoute route) {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(route.getFirstChoice());
        progress.chooseSecond(route.getSecondChoice());
        return progress;
    }

    @Test
    void everyWaveOfEveryRouteFitsOnTheScreen() {
        for (StoryRoute route : StoryRoute.values()) {
            StoryProgress progress = answered(route);
            for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
                StoryStage data = StoryStageData.get(stage, progress);
                assertNotNull(data, route + " stage " + stage);
                for (int wave = 1; wave <= data.getWaveCount(); wave++) {
                    final int stageNumber = stage;
                    final int waveNumber = wave;
                    assertDoesNotThrow(() -> EnemyShipFormation.validateWave(
                            data.createWave(stageNumber, waveNumber),
                            SCREEN_WIDTH, SCREEN_HEIGHT),
                            route + " stage " + stage + " wave " + wave);
                }
            }
        }
    }

    @Test
    void stageOneIsTheSameForEveryone() {
        StoryProgress fresh = new StoryProgress();
        StoryStage first = StoryStageData.get(1, fresh);
        for (StoryRoute route : StoryRoute.values())
            assertSame(first, StoryStageData.get(1, answered(route)));
    }

    @Test
    void stagesTwoAndThreeOnlyNeedTheFirstAnswer() {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(1);
        assertNotNull(StoryStageData.get(2, progress));
        assertNotNull(StoryStageData.get(3, progress));
        assertThrows(IllegalStateException.class,
                () -> StoryStageData.get(4, progress));
    }

    @Test
    void stagesTwoAndThreeFollowTheFirstAnswer() {
        for (int stage = 2; stage <= 3; stage++) {
            assertSame(StoryStageData.get(stage, answered(StoryRoute.HYBRID)),
                    StoryStageData.get(stage,
                            answered(StoryRoute.HUMAN_VICTORY)));
            assertSame(StoryStageData.get(stage,
                    answered(StoryRoute.HOMECOMING)),
                    StoryStageData.get(stage, answered(StoryRoute.TRUCE)));
        }
        assertFalse(StoryStageData.get(2, answered(StoryRoute.HYBRID))
                == StoryStageData.get(2, answered(StoryRoute.HOMECOMING)));
    }

    @Test
    void stageOneNeedsNoAnswer() {
        assertNotNull(StoryStageData.get(1, new StoryProgress()));
        assertThrows(IllegalStateException.class,
                () -> StoryStageData.get(2, new StoryProgress()));
    }

    @Test
    void rescueRouteStartsWithTheAsteroidField() {
        assertTrue(StoryStageData.get(2, answered(StoryRoute.HOMECOMING))
                .hasAsteroidField());
        assertFalse(StoryStageData.get(2, answered(StoryRoute.HYBRID))
                .hasAsteroidField());
    }

    @Test
    void escortRouteDefendsAmongObstacles() {
        StoryProgress escort = answered(StoryRoute.HOMECOMING);
        for (int stage = 4; stage <= 6; stage++)
            assertTrue(StoryStageData.get(stage, escort).hasAsteroidField());
    }

    @Test
    void onlyTheOldBossesAreStrongerOnTheFinalStage() {
        for (StoryRoute route : StoryRoute.values()) {
            StoryProgress progress = answered(route);
            for (int stage = 1; stage < StoryProgress.FINAL_BOSS_STAGE; stage++)
                assertEquals(1.0,
                        StoryStageData.get(stage, progress).getBossStrength(),
                        route + " stage " + stage);
        }
        assertEquals(1.5, StoryStageData.get(7,
                answered(StoryRoute.HUMAN_VICTORY)).getBossStrength());
        assertEquals(1.5, StoryStageData.get(7,
                answered(StoryRoute.HOMECOMING)).getBossStrength());
        assertEquals(1.0, StoryStageData.get(7,
                answered(StoryRoute.HYBRID)).getBossStrength());
        assertEquals(1.0, StoryStageData.get(7,
                answered(StoryRoute.TRUCE)).getBossStrength());
    }

    @Test
    void finalStageHasOneWaveSoTheBossFightIsNotLong() {
        for (StoryRoute route : StoryRoute.values())
            assertEquals(1, StoryStageData.get(7, answered(route))
                    .getWaveCount(), route.toString());
    }

    @Test
    void invalidInputIsRejected() {
        StoryProgress progress = answered(StoryRoute.HYBRID);
        assertThrows(IllegalArgumentException.class,
                () -> StoryStageData.get(0, progress));
        assertThrows(IllegalArgumentException.class,
                () -> StoryStageData.get(8, progress));
        assertThrows(IllegalArgumentException.class,
                () -> StoryStageData.get(1, null));
    }
}
