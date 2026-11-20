package engine;

import entity.ShipType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoryDebugModifiersTest {
    @Test
    void defaultsFollowTheStandardShipAndLeaveHpAndMotionAlone() {
        StoryDebugModifiers defaults = StoryDebugModifiers.defaults();
        assertFalse(defaults.invincible());
        assertEquals(ShipType.STANDARD.getMaxLives(), defaults.startingLives());
        assertEquals(1.0, defaults.enemySpeedMultiplier());
        assertEquals(800, defaults.scaleHp(800));
    }

    @Test
    void validationAcceptsInclusiveLimitsAndRejectsNonFiniteOrOutOfRangeValues() {
        for (int lives : new int[] {1, 4, 15, 99}) {
            assertEquals(lives, new StoryDebugModifiers(true, lives, 0.25, 4.0).startingLives());
        }
        for (int lives : new int[] {0, 100}) {
            assertThrows(IllegalArgumentException.class, () -> new StoryDebugModifiers(false, lives, 1, 1));
        }
        for (double invalid : new double[] {0.249, 4.001, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new StoryDebugModifiers(false, 1, invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> new StoryDebugModifiers(false, 1, 1, invalid));
        }
    }

    @Test
    void hpRoundsHalfUpWithALowerBoundOfOne() {
        StoryDebugModifiers half = new StoryDebugModifiers(false, 4, 0.5, 1);
        assertEquals(1, half.scaleHp(1));
        assertEquals(2, half.scaleHp(3));
        assertEquals(400, half.scaleHp(800));
        assertEquals(20, new StoryDebugModifiers(false, 4, 2.0, 1).scaleHp(10));
    }
}
