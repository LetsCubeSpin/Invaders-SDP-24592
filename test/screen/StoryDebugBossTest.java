package screen;

import audio.AudioManager;
import boss.BossEvent;
import boss.BossManager;
import boss.BossSpawnRequest;
import engine.Cooldown;
import engine.GameState;
import engine.StoryDebugModifiers;
import entity.Boss;
import entity.BossBullet;
import entity.EnemyShip;
import event.DefeatedEnemyType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import story.StoryProgress;

import static org.junit.jupiter.api.Assertions.*;

class StoryDebugBossTest {
    @Test
    void scaledHealthDrivesHudPhasesAndOneDefeat() {
        BossManager manager = new BossManager();
        List<BossEvent.Type> events = new ArrayList<>();
        manager.getEventManager().addListener(event -> events.add(event.getType()));
        Boss boss = manager.spawn(new BossSpawnRequest(Boss.BossType.BOSS_B, 100, 78, 3, 0),
                new StoryDebugModifiers(false, 3, 0.5, 1));
        assertEquals(400, manager.getHudInfo().getMaxHealth());
        assertEquals(400, manager.getHudInfo().getCurrentHealth());
        for (int[] boundary : new int[][] {{280, 1}, {279, 2}, {160, 2}, {159, 3}}) {
            boss.takeDamage(boss.getCurrentHealth() - boundary[0]);
            manager.sync();
            assertEquals(boundary[0], manager.getHudInfo().getCurrentHealth());
            assertEquals(boundary[1], manager.getHudInfo().getCurrentPhase());
            assertEquals(boundary[1], manager.getAttackSystem().getPhase());
        }
        boss.takeDamage(159);
        manager.sync();
        manager.sync();
        assertFalse(manager.getHudInfo().isVisible());
        assertFalse(manager.getAttackSystem().isActive());
        assertEquals(2, events.stream().filter(type -> type == BossEvent.Type.PHASE_CHANGED).count());
        assertEquals(1, events.stream().filter(type -> type == BossEvent.Type.DEFEATED).count());
        assertEquals(800, Boss.BossType.BOSS_B.getMaxHealth());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 0.5, 1, 2, 4})
    void movementScalesAtEveryPhaseAndFollowsOriginalBounces(double speed) {
        StoryDebugModifiers modifiers = new StoryDebugModifiers(false, 3, 1, speed);
        for (int phase = 1; phase <= 3; phase++) {
            Boss boss = new Boss(100, 78, Boss.BossType.BOSS_B, modifiers);
            Boss reference = new Boss(100, 78, Boss.BossType.BOSS_B);
            int damage = new int[] {0, 300, 600}[phase - 1];
            boss.takeDamage(damage);
            reference.takeDamage(damage);
            assertEquals(phase, boss.getPhase());
            for (int frame = 0; frame < 80; frame++) boss.update(0, 5000);
            assertEquals(80 * (2 + phase) * speed, boss.getPositionX() - 100);

            // An integral window ends with no fractional tick outstanding.
            boss.setPositionX(100);
            int referenceTicks = 0;
            for (int frame = 1; frame <= 160; frame++) {
                boss.update(20, 148);
                while (referenceTicks < (int) (frame * speed)) {
                    reference.update(20, 148);
                    referenceTicks++;
                }
                assertEquals(reference.getPositionX(), boss.getPositionX());
                assertEquals(reference.getDirection(), boss.getDirection());
                assertTrue(boss.getPositionX() >= 20 && boss.getPositionX() + Boss.WIDTH <= 148);
            }
        }
    }

    @Test
    void movementChangesNeitherAttackIntervalNorProjectileVelocity() throws Exception {
        BossSpawnRequest request = new BossSpawnRequest(Boss.BossType.BOSS_B, 100, 78, 3, 0);
        BossManager reference = new BossManager(new Random(4));
        reference.spawn(request);
        List<BossBullet> expected = firstVolley(reference);
        assertFalse(expected.isEmpty());
        for (double speed : new double[] {0.25, 0.5, 1, 2, 4}) {
            BossManager scaled = new BossManager(new Random(4));
            scaled.spawn(request, new StoryDebugModifiers(false, 3, 1, speed));
            assertEquals(reference.getAttackSystem().getCurrentIntervalMilliseconds(),
                    scaled.getAttackSystem().getCurrentIntervalMilliseconds());
            List<BossBullet> actual = firstVolley(scaled);
            assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).getSpeedX(), actual.get(i).getSpeedX());
                assertEquals(expected.get(i).getSpeedY(), actual.get(i).getSpeedY());
            }
        }
    }

    private static List<BossBullet> firstVolley(BossManager manager) throws Exception {
        List<BossBullet> bullets = new ArrayList<>();
        manager.update(180, 400, bullets);
        Boss boss = manager.getBoss();
        for (int frame = 0; frame < 8; frame++) boss.update(0, 1000);
        expireCooldown(manager.getAttackSystem(), "attackCooldown");
        manager.update(boss.getPositionX() + 25, 400, bullets);
        return bullets;
    }

    @Test
    void realSummonRequestsCreateScaledMinionsInEveryPhase() throws Exception {
        boolean wasMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        try {
            StoryProgress progress = new StoryProgress();
            progress.chooseFirst(1);
            GameScreen screen = GameScreen.forStoryDebugStage(new GameState(3, 0, 3, 0, 0),
                    progress, settings -> settings, 448, 520, 60,
                    new StoryDebugModifiers(false, 3, 2, 0.25));
            // No current Story route selects C. Substitute only its type before normal spawning.
            field(GameScreen.class, "storyBoss").set(screen, Boss.BossType.BOSS_C);
            screen.initialize();
            BossManager manager = (BossManager) field(GameScreen.class, "bossManager").get(screen);
            Boss boss = manager.getBoss();
            assertEquals(1600, boss.getCurrentHealth());
            expire(screen.inputDelay);
            screen.update();
            @SuppressWarnings("unchecked")
            List<EnemyShip> minions = (List<EnemyShip>) field(GameScreen.class, "summonedEnemies").get(screen);
            for (int phase = 1; phase <= 3; phase++) {
                if (phase > 1) boss.takeDamage(500);
                manager.sync();
                assertEquals(phase, boss.getPhase());
                int previous = minions.size();
                expireCooldown(manager.getAttackSystem(), "summonCooldown");
                screen.update();
                assertEquals(previous + (phase == 2 ? 3 : 2), minions.size());
                DefeatedEnemyType expected = List.of(DefeatedEnemyType.A, DefeatedEnemyType.B,
                        DefeatedEnemyType.C).get(phase - 1);
                for (EnemyShip minion : minions.subList(previous, minions.size())) {
                    assertEquals(expected, minion.getDefeatedType());
                    assertEquals(2, minion.getHealth());
                }
                EnemyShip minion = minions.get(previous);
                int startY = minion.getPositionY();
                for (int frame = 0; frame < 4; frame++) screen.update();
                assertEquals(2, minion.getPositionY() - startY);
            }
        } finally {
            AudioManager.stopBGM();
            AudioManager.setMuted(wasMuted);
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void expireCooldown(Object owner, String name) throws Exception {
        expire((Cooldown) field(owner.getClass(), name).get(owner));
    }

    private static void expire(Cooldown cooldown) throws Exception {
        field(Cooldown.class, "time").setLong(cooldown, 0L);
    }
}
