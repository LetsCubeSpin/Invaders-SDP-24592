package screen;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import audio.AudioManager;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import entity.Bullet;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.PlayerStatus;
import entity.Ship;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class TimeAttackTest {
    private final AtomicLong now = new AtomicLong();
    private final TimeAttackClock clock = new TimeAttackClock(now::get);
    private GameScreen game;
    private boolean wasMuted;

    @BeforeEach
    void initialize() {
        wasMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        game = GameScreen.forTimeAttack(new GameState(1, 0, 4, 0, 0),
                new GameSettings(5, 4, 60, 2000, 20, 100), 448, 520, 60, clock);
        game.initialize();
    }

    @AfterEach
    void restoreAudio() {
        AudioManager.setMuted(wasMuted);
    }

    @Test
    void clockWaitsForCountdownThenCountsElapsedTime() {
        now.set(SECONDS.toNanos(20));
        game.update();
        assertEquals(90, clock.remainingSeconds());
        game.inputDelay = Core.getCooldown(0);
        game.update();
        now.addAndGet(SECONDS.toNanos(3));
        game.update();
        assertEquals(87, clock.remainingSeconds());
    }

    @Test
    void damagingHitCostsTimeAndKeepsTheShipAlive() throws Exception {
        assertFalse(game.damagePlayer(1, "during countdown"));
        game.inputDelay = Core.getCooldown(0);
        int originalLives = game.getGameState().getLivesRemaining();
        assertTrue(game.damagePlayer(2, "test hit"));
        assertEquals(85, clock.remainingSeconds());
        assertEquals(originalLives, game.getGameState().getLivesRemaining());
        assertFalse(((Ship) field(game, "ship").get(game)).isDestroyed());
    }

    @Test
    void countdownShowsSkippedMilestonesAndCurrentFinalSeconds() {
        clock.start();
        now.set(SECONDS.toNanos(27));
        assertEquals(0, clock.countdownSeconds());
        clock.deductHit();
        assertEquals(58, clock.remainingSeconds());
        assertEquals(60, clock.countdownSeconds());
        now.addAndGet(SECONDS.toNanos(1));
        assertEquals(60, clock.countdownSeconds());
        now.addAndGet(SECONDS.toNanos(1));
        assertEquals(0, clock.countdownSeconds());

        now.set(SECONDS.toNanos(53));
        assertEquals(0, clock.countdownSeconds());
        clock.deductHit();
        assertEquals(30, clock.countdownSeconds());

        now.set(SECONDS.toNanos(68));
        assertEquals(0, clock.countdownSeconds());
        clock.deductHit();
        assertEquals(7, clock.countdownSeconds());
        now.addAndGet(SECONDS.toNanos(1));
        assertEquals(6, clock.countdownSeconds());
    }

    @Test
    void shieldBlocksTimePenalty() throws Exception {
        game.inputDelay = Core.getCooldown(0);
        PlayerStatus status = (PlayerStatus) field(game, "playerStatus").get(game);
        assertTrue(status.activateShield());
        assertFalse(game.damagePlayer(1, "blocked hit"));
        assertEquals(90, clock.remainingSeconds());
    }

    @Test
    void expiryWinsOverClearingInTheSameUpdate() throws Exception {
        emptyFormation();
        clock.start();
        now.set(SECONDS.toNanos(90));
        game.advanceStageIfReady();
        assertTrue(field(game, "levelFinished").getBoolean(game));
        assertFalse(game.isStageCleared());
    }

    @Test
    void lastHitCanExpireClockAndNextStageHasAFreshBudget() throws Exception {
        game.inputDelay = Core.getCooldown(0);
        clock.start();
        now.set(SECONDS.toNanos(87));
        assertTrue(game.damagePlayer(1, "last hit"));
        assertEquals(0, clock.remainingSeconds());
        assertTrue(field(game, "levelFinished").getBoolean(game));
        assertFalse(game.isStageCleared());
        assertEquals(90, new TimeAttackClock(now::get).remainingSeconds());
    }

    @Test
    void playerBulletsCannotScoreAfterFatalHitInSameCollisionPass() throws Exception {
        game.inputDelay = Core.getCooldown(0);
        clock.start();
        now.set(SECONDS.toNanos(87));
        Ship ship = (Ship) field(game, "ship").get(game);
        EnemyShipFormation formation = (EnemyShipFormation) field(game, "enemyShipFormation").get(game);
        EnemyShip enemy = formation.iterator().next();
        Set<Bullet> bullets = new LinkedHashSet<>();
        bullets.add(new Bullet(ship.getPositionX(), ship.getPositionY(), 1));
        bullets.add(new Bullet(enemy.getPositionX(), enemy.getPositionY(), -1));
        field(game, "bullets").set(game, bullets);

        var collisions = GameScreen.class.getDeclaredMethod("manageCollisions");
        collisions.setAccessible(true);
        collisions.invoke(game);

        assertEquals(0, clock.remainingSeconds());
        assertFalse(enemy.isDestroyed());
        assertEquals(0, game.getGameState().getScore());
    }

    @Test
    void clearingBeforeDeadlineSucceeds() throws Exception {
        emptyFormation();
        clock.start();
        now.set(SECONDS.toNanos(89));
        game.advanceStageIfReady();
        assertTrue(game.isStageCleared());
    }

    private void emptyFormation() throws Exception {
        EnemyShipFormation formation = (EnemyShipFormation) field(game, "enemyShipFormation").get(game);
        field(formation, "shipCount").setInt(formation, 0);
    }

    private static Field field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
