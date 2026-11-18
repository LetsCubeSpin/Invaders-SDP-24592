package event;

import engine.GameplayConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks the values LevelCompletedEvent accepts now that Easy is the only difficulty. */
class LevelCompletedEventValidationTest {

    @Test
    void easyDifficultyIsAccepted() {
        LevelCompletedEvent completed =
                new LevelCompletedEvent(3, GameplayConfig.EASY);

        assertEquals(3, completed.getLevelId());
        assertEquals(GameplayConfig.EASY, completed.getDifficulty());
    }

    @Test
    void formerNormalAndHardValuesAreRejected() {
        for (int difficulty : new int[] {1, 2}) {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> new LevelCompletedEvent(1, difficulty),
                    "Difficulty " + difficulty + " no longer exists");
            assertEquals("difficulty must be 0 (Easy).", error.getMessage());
        }
    }

    @Test
    void outOfRangeDifficultiesAreRejected() {
        for (int difficulty : new int[] {-1, 3, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LevelCompletedEvent(1, difficulty));
        }
    }

    @Test
    void levelStillHasToBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new LevelCompletedEvent(0, GameplayConfig.EASY));
    }
}
