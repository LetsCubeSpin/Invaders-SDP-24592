package entity;

import static org.junit.jupiter.api.Assertions.*;

import engine.Cooldown;
import engine.Core;
import engine.GameSettings;
import engine.StoryDebugModifiers;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import level.EnemyCell;
import level.WaveDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import screen.Screen;

class StoryDebugFormationTest {
    private static final double[] PRESETS = {0.25, 0.5, 1.0, 2.0, 4.0};

    private static StoryDebugModifiers modifiers(double hp, double speed) {
        return new StoryDebugModifiers(false, StoryDebugModifiers.defaults().startingLives(), hp, speed);
    }

    @ParameterizedTest
    @CsvSource({"1,0.25,1", "3,0.5,2", "10,1.0,10", "10,2.0,20", "10,4.0,40"})
    void customWaveAppliesScaledHpToActualHits(int baseHp, double multiplier, int expected) {
        WaveDefinition wave = new WaveDefinition("one-ship",
                new EnemyCell[][] {{new EnemyCell("A", baseHp)}}, 20, 100, 60, 2000);
        EnemyShipFormation formation = new EnemyShipFormation(new GameSettings(1, 1, 60, 2000),
                1, wave, modifiers(multiplier, 1));
        EnemyShip ship = formation.iterator().next();
        assertEquals(expected, ship.getHealth());
        for (int health = expected; health > 1; health--) {
            assertFalse(formation.hit(ship));
            assertEquals(health - 1, ship.getHealth());
            assertEquals(1, formation.getRemainingCount());
        }
        assertTrue(formation.hit(ship));
        assertEquals(1, formation.getDestroyedCount());
        assertFalse(formation.hit(ship));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void movementTracksOriginalTrajectoryAcrossTurnsForEveryPreset(int level) {
        GameSettings settings = new GameSettings(1, 1, 60, 2000);
        Screen screen = new Screen(180, 260, 60);
        for (double multiplier : PRESETS) {
            EnemyShipFormation baseline = new EnemyShipFormation(settings, level);
            EnemyShipFormation scaled = new EnemyShipFormation(settings, level, null, modifiers(1, multiplier));
            baseline.attach(screen);
            scaled.attach(screen);
            EnemyShip reference = baseline.iterator().next();
            EnemyShip ship = scaled.iterator().next();
            int baselineFrames = 0;
            boolean movedLeft = false;
            boolean movedRight = false;
            for (int frame = 1; frame <= 1000; frame++) {
                while (baselineFrames < (int) (frame * multiplier)) {
                    baseline.update();
                    baselineFrames++;
                }
                int oldX = ship.getPositionX();
                scaled.update();
                movedLeft |= ship.getPositionX() < oldX;
                movedRight |= ship.getPositionX() > oldX;
                assertEquals(reference.getPositionX(), ship.getPositionX());
                assertEquals(reference.getPositionY(), ship.getPositionY());
                assertTrue(ship.getPositionX() >= 20);
                assertTrue(ship.getPositionX() + ship.getWidth() <= screen.getWidth() - 20);
            }
            assertTrue(movedLeft && movedRight, "level " + level + ", speed " + multiplier);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 0.5, 1.0, 2.0, 4.0})
    void measuredDistanceScalesEvenWhenNativeStepsHaveReachedTheirCap(double multiplier) {
        // At the opening pace a classic ship moves 2 px every 2 frames. With baseSpeed 1,
        // a diagonal ship already reaches the legacy 16 px cap. Flatten the diamond here
        // so both measurements cover a straight section before any turns.
        Screen wide = new Screen(10000, 196, 60);
        for (int level : new int[] {1, 3}) {
            int baseSpeed = level == 1 ? 60 : 1;
            int pixelsPerStep = level == 1 ? 2 : 16;
            EnemyShipFormation formation = new EnemyShipFormation(new GameSettings(1, 1, baseSpeed, 2000),
                    level, null, modifiers(1, multiplier));
            formation.attach(wide);
            EnemyShip ship = formation.iterator().next();
            int start = ship.getPositionX();
            for (int frame = 0; frame < 128; frame++) formation.update();
            assertEquals((int) (64 * pixelsPerStep * multiplier), ship.getPositionX() - start);
            assertEquals(100, ship.getPositionY());
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 0.5, 1.0, 2.0, 4.0})
    void formationFireRateAndBulletVelocityUseTheLevelSettings(double multiplier) throws Exception {
        EnemyShipFormation formation = new EnemyShipFormation(new GameSettings(1, 1, 60, 60000),
                1, null, modifiers(1, multiplier));
        formation.attach(new Screen(448, 520, 60));
        formation.update();
        EnemyShip ship = formation.iterator().next();
        Field timer = EnemyShipFormation.class.getDeclaredField("shootingCooldown");
        timer.setAccessible(true);
        Cooldown formationCooldown = (Cooldown) timer.get(formation);
        Field duration = Cooldown.class.getDeclaredField("milliseconds");
        duration.setAccessible(true);
        assertEquals(60000, duration.getInt(formationCooldown));
        assertEquals(30000, duration.getInt(ship.getShootingCooldown()));
        // Make the first shot due without sleeping or substituting either real timer.
        Field lastReset = Cooldown.class.getDeclaredField("time");
        lastReset.setAccessible(true);
        lastReset.setLong(formationCooldown, 0L);
        Set<Bullet> bullets = new HashSet<>();
        formation.shoot(bullets);
        assertEquals(1, bullets.size());
        for (int frame = 0; frame < 256; frame++) {
            formation.update();
            formation.shoot(bullets);
        }
        assertEquals(1, bullets.size());
        Bullet bullet = bullets.iterator().next();
        assertEquals((int) engine.DifficultyAdjustWhilePlaying.getInstance().adjustEnemyBulletSpeed(3),
                bullet.getSpeed());
    }

    @Test
    void bonusShipAccumulatesFractionalDistanceAndOnlyTheLethalHitDestroysIt() throws Exception {
        BonusEnemyManager manager = new BonusEnemyManager(modifiers(2, 0.25));
        Field spawn = BonusEnemyManager.class.getDeclaredField("spawnCooldown");
        spawn.setAccessible(true);
        spawn.set(manager, Core.getCooldown(60000));
        manager.update(448);
        EnemyShip bonus = manager.getShip();
        assertEquals(2, bonus.getHealth());
        int start = bonus.getPositionX();
        for (int frame = 1; frame <= 8; frame++) {
            manager.update(448);
            assertEquals(start + frame / 2, bonus.getPositionX());
        }
        assertFalse(manager.hit());
        assertTrue(manager.isActive());
        assertEquals(1, bonus.getHealth());
        assertTrue(manager.hit());
        assertTrue(bonus.isDestroyed());
        assertFalse(manager.hit());
        assertEquals(100, bonus.getPointValue());
    }
}
