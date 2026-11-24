package boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import engine.StoryDebugModifiers;
import entity.Boss;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks the longer boss attack interval used in story mode. */
class BossAttackIntervalScaleTest {

    private static BossSpawnRequest request(final Boss.BossType type) {
        return new BossSpawnRequest(type, 100, 78, 3, 0);
    }

    @ParameterizedTest
    @EnumSource(Boss.BossType.class)
    void scaleMultipliesOnlyTheBaseInterval(final Boss.BossType type) {
        BossAttackConfig original = BossAttackConfig.forType(type);
        BossAttackConfig scaled = original.withIntervalScale(1.2);

        assertEquals(Math.round(original.getBaseIntervalMilliseconds() * 1.2),
                scaled.getBaseIntervalMilliseconds());
        assertEquals(original.getDamage(), scaled.getDamage());
        assertSame(original.getSpecialAttack(), scaled.getSpecialAttack());
        for (int phase = 1; phase <= 3; phase++)
            assertSame(original.getPhase(phase), scaled.getPhase(phase));
    }

    @Test
    void scaleOfOneKeepsTheSameSetup() {
        BossAttackConfig original = BossAttackConfig.forType(Boss.BossType.BOSS_A);
        assertSame(original, original.withIntervalScale(1.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidScalesAreRejected(final double scale) {
        BossAttackConfig original = BossAttackConfig.forType(Boss.BossType.BOSS_B);
        assertThrows(IllegalArgumentException.class,
                () -> original.withIntervalScale(scale));
    }

    @ParameterizedTest
    @EnumSource(Boss.BossType.class)
    void spawnedBossUsesTheScaledInterval(final Boss.BossType type) {
        BossManager normal = new BossManager(new Random(1));
        BossManager slower = new BossManager(new Random(1));
        normal.spawn(request(type), StoryDebugModifiers.defaults());
        slower.spawn(request(type), StoryDebugModifiers.defaults(), 1.2);

        int expected = new BossAttackSystem(
                BossAttackConfig.forType(type).withIntervalScale(1.2), new Random(1))
                .getCurrentIntervalMilliseconds();
        assertEquals(expected, slower.getAttackSystem().getCurrentIntervalMilliseconds());
        assertEquals(new BossAttackSystem(BossAttackConfig.forType(type), new Random(1))
                .getCurrentIntervalMilliseconds(),
                normal.getAttackSystem().getCurrentIntervalMilliseconds());
    }

    @Test
    void invalidScaleLeavesTheManagerWithoutABoss() {
        BossManager manager = new BossManager(new Random(1));
        assertThrows(IllegalArgumentException.class,
                () -> manager.spawn(request(Boss.BossType.BOSS_A),
                        StoryDebugModifiers.defaults(), 0.0));
        assertNull(manager.getBoss());
        assertNotNull(manager.spawn(request(Boss.BossType.BOSS_A),
                StoryDebugModifiers.defaults()));
    }
}
