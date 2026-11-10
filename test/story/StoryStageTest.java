package story;

import engine.GameSettings;
import level.WaveDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryStageTest {

    private static final GameSettings SETTINGS =
            new GameSettings(5, 4, 60, 2000, 20, 100);

    @Test
    void keepsTheGivenValues() {
        StoryStage stage = new StoryStage(SETTINGS, "CBAA", 2, true, 1.5);
        assertEquals(SETTINGS, stage.getSettings());
        assertEquals("CBAA", stage.getRows());
        assertEquals(2, stage.getWaveCount());
        assertTrue(stage.hasAsteroidField());
        assertEquals(1.5, stage.getBossStrength());
    }

    @Test
    void waveUsesTheEnemyTypeOfEachRow() {
        WaveDefinition wave = new StoryStage(SETTINGS, "CBAA", 1, false, 1.0)
                .createWave(1, 1);
        for (int column = 0; column < 5; column++) {
            assertEquals("C", wave.getCell(0, column).getEnemyType());
            assertEquals("B", wave.getCell(1, column).getEnemyType());
            assertEquals("A", wave.getCell(2, column).getEnemyType());
            assertEquals("A", wave.getCell(3, column).getEnemyType());
        }
        assertEquals(20, wave.getEnemyCount());
    }

    @Test
    void waveMatchesTheSettingsSoTheFormationAcceptsIt() {
        WaveDefinition wave = new StoryStage(SETTINGS, "AAAA", 1, false, 1.0)
                .createWave(1, 1);
        assertEquals(SETTINGS.getFormationHeight(), wave.getRows());
        assertEquals(SETTINGS.getFormationWidth(), wave.getColumns());
        assertEquals(SETTINGS.getInitialX(), wave.getInitialX());
        assertEquals(SETTINGS.getInitialY(), wave.getInitialY());
        assertEquals(SETTINGS.getBaseSpeed(), wave.getMovementBaseFrames());
        assertEquals(SETTINGS.getShootingFrecuency(),
                wave.getShootingIntervalMs());
    }

    @Test
    void waveIdNamesTheStageAndWave() {
        StoryStage stage = new StoryStage(SETTINGS, "AAAA", 2, false, 1.0);
        assertEquals("story-3-1", stage.createWave(3, 1).getId());
        assertEquals("story-3-2", stage.createWave(3, 2).getId());
    }

    @Test
    void waveNumberMustBeInRange() {
        StoryStage stage = new StoryStage(SETTINGS, "AAAA", 2, false, 1.0);
        assertThrows(IllegalArgumentException.class,
                () -> stage.createWave(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> stage.createWave(1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> stage.createWave(0, 1));
    }

    @Test
    void rowsMustMatchTheFormationHeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new StoryStage(SETTINGS, "AAA", 1, false, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryStage(SETTINGS, "AAAAA", 1, false, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryStage(SETTINGS, null, 1, false, 1.0));
    }

    @Test
    void rowsMayOnlyUseKnownEnemyTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new StoryStage(SETTINGS, "AAAD", 1, false, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryStage(SETTINGS, "aaaa", 1, false, 1.0));
    }

    @Test
    void otherInvalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StoryStage(null, "AAAA", 1, false, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryStage(SETTINGS, "AAAA", 0, false, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryStage(SETTINGS, "AAAA", 1, false, 0.9));
    }

    @Test
    void normalStageHasNoAsteroidFieldAndNormalBoss() {
        StoryStage stage = new StoryStage(SETTINGS, "AAAA", 1, false, 1.0);
        assertFalse(stage.hasAsteroidField());
        assertEquals(1.0, stage.getBossStrength());
    }
}
