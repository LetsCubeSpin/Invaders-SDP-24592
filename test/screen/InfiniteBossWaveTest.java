package screen;

import audio.AudioManager;
import boss.BossManager;
import engine.Core;
import engine.GameState;
import entity.Boss;
import entity.BossBullet;
import entity.Bullet;
import entity.BulletPool;
import entity.Ship;
import entity.ZigzagEnemyShip;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import records.RunMode;
import records.RunSession;

import static org.junit.jupiter.api.Assertions.*;

class InfiniteBossWaveTest {
    @Test
    void bossWavesDrawIndependentlyAndResumeAfterEachDefeat() throws Exception {
        RunSession session = new RunSession("endless-waves", RunMode.INFINITE);
        InfiniteGameScreen game = new InfiniteGameScreen(new GameState(1, 0, 3, 0, 0),
                448, 520, 60, session, 2);
        boolean muted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        try {
            game.initialize();
            game.inputDelay = Core.getCooldown(0);
            List<ZigzagEnemyShip> enemies = fieldList(game, "enemies");
            List<BossBullet> hostile = fieldList(game, "bossBullets");
            int expectedScore = 0;
            // This seed covers all four bosses and consecutive encounters with C.
            ((Random) field(game, "bossRandom").get(game)).setSeed(33L);
            Boss.BossType[] expected = {Boss.BossType.BOSS_C, Boss.BossType.BOSS_C,
                    Boss.BossType.BOSS_D, Boss.BossType.BOSS_A, Boss.BossType.BOSS_B};
            for (int wave = 1; wave <= 25; wave++) {
                assertEquals(wave, game.getGameState().getLevel());
                assertEquals(wave, session.getStats().getWavesReached());
                if (wave % 5 != 0) {
                    for (int i = 1; i <= 9; i++) {
                        expire(game, "spawnCooldown");
                        game.update();
                        assertEquals(Math.min(i, 8), enemies.size());
                    }
                    assertEquals(wave, game.getGameState().getLevel());
                    for (ZigzagEnemyShip enemy : enemies)
                        enemy.setPositionY(game.getHeight() + 1);
                    game.update();
                    continue;
                }

                BossManager manager = (BossManager) field(game, "bossManager").get(game);
                Boss boss = manager.getBoss();
                assertEquals(expected[wave / 5 - 1], boss.getType());
                expire(game, "spawnCooldown");
                game.update();
                assertTrue(enemies.isEmpty());
                assertEquals(wave, game.getGameState().getLevel());

                if (wave == 5) {
                    Ship second = game.getPlayerShip(1);
                    hostile.add(new BossBullet(second.getPositionX() + second.getWidth() / 2,
                            second.getPositionY(), 0, 0, 1, false));
                    game.update();
                    assertFalse(game.getPlayerStatus(1).isAlive());
                    assertTrue(game.getPlayerStatus(0).isAlive());
                    assertEquals(2, game.getGameState().getLivesRemaining());
                }
                if (boss.getType() == Boss.BossType.BOSS_C) {
                    for (int i = 0; i < 2; i++) {
                        expire(manager.getAttackSystem(), "summonCooldown");
                        game.update();
                        assertEquals(2, enemies.size());
                    }
                }
                int hp = boss.getCurrentHealth();
                fireAtBoss(game, boss);
                game.update();
                assertEquals(hp - 50, manager.getHudInfo().getCurrentHealth());
                assertEquals(wave, game.getGameState().getLevel());
                if (boss.getType() == Boss.BossType.BOSS_D)
                    checkObeliskAttacks(game, manager);
                hostile.add(new BossBullet(20, 200, 0, 1, 1, false));
                for (int remaining = boss.getCurrentHealth(); remaining > 0; remaining -= 50)
                    fireAtBoss(game, boss);
                game.update();
                expectedScore += boss.getType().getPointValue();
                assertEquals(wave + 1, game.getGameState().getLevel());
                assertEquals(expectedScore, game.getGameState().getScore());
                assertEquals(wave / 5, game.getGameState().getShipsDestroyed());
                assertEquals(wave / 5, session.getStats().getBossesDefeated());
                assertTrue(enemies.isEmpty());
                assertTrue(hostile.isEmpty());
                assertNull(field(game, "bossManager").get(game));
            }
        } finally {
            AudioManager.stopBGM();
            AudioManager.setMuted(muted);
        }
    }

    private static void checkObeliskAttacks(InfiniteGameScreen game, BossManager manager) throws Exception {
        Boss boss = manager.getBoss();
        for (int hp = boss.getCurrentHealth(); hp > 650; hp -= 50)
            fireAtBoss(game, boss);
        game.update();
        expire(manager.getAttackSystem(), "shieldCooldown");
        fireAtBoss(game, boss);
        game.update();
        assertTrue(boss.isShieldActive());
        assertEquals(650, boss.getCurrentHealth());

        expire(manager.getAttackSystem(), "shieldDurationCooldown");
        for (int hp = 650; hp > 350; hp -= 50)
            fireAtBoss(game, boss);
        game.update();
        game.update();
        assertTrue(manager.getAttackSystem().isLaserCharging());
        int lives = game.getGameState().getLivesRemaining();
        game.getPlayerShip(0).setPositionX(boss.getPositionX());
        expire(manager.getAttackSystem(), "windupCooldown");
        game.update();
        assertTrue(manager.getAttackSystem().isLaserFiring());
        assertEquals(lives - 1, game.getGameState().getLivesRemaining());
    }

    @SuppressWarnings("unchecked")
    private static void fireAtBoss(InfiniteGameScreen game, Boss boss) throws Exception {
        Set<Bullet> bullets = (Set<Bullet>) field(game, "bullets").get(game);
        bullets.add(BulletPool.getBullet(boss.getPositionX() + boss.getWidth() / 2,
                boss.getPositionY(), -6, Bullet.Owner.PLAYER));
    }

    private static void expire(Object owner, String name) throws Exception {
        field(owner, name).set(owner, Core.getCooldown(0));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> fieldList(Object owner, String name) throws Exception {
        return (List<T>) field(owner, name).get(owner);
    }

    private static Field field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
