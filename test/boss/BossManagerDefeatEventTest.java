package boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import entity.Boss;
import entity.BossBullet;
import event.BossDefeatedEvent;
import event.DefeatedEnemyType;
import event.GameplayEventPublisher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the BossDefeatedEvent that BossManager puts on the gameplay bus. */
class BossManagerDefeatEventTest {

    private final long[] now = { 10_000L };
    private final GameplayEventPublisher bus = new GameplayEventPublisher();
    private final List<BossDefeatedEvent> defeats = new ArrayList<BossDefeatedEvent>();

    private BossManager manager(final String runId) {
        BossManager manager = new BossManager(new Random(1), () -> now[0]);
        manager.connectGameplayEvents(bus, runId);
        bus.subscribe(BossDefeatedEvent.class, defeats::add);
        return manager;
    }

    private static Boss spawn(final BossManager manager, final Boss.BossType type) {
        return manager.spawn(new BossSpawnRequest(type, 100, 78, 3, 0));
    }

    /** One frame of the battle, as GameScreen runs it once the countdown is over. */
    private static void frame(final BossManager manager) {
        manager.update(0, 500, new ArrayList<BossBullet>());
    }

    @Test
    void aDefeatIsPublishedOnceWithItsTypeAndBattleDuration() {
        BossManager manager = manager("run");
        Boss boss = spawn(manager, Boss.BossType.BOSS_B);
        frame(manager);

        now[0] += 1_500L;
        boss.takeDamage(boss.getMaxHealth() - 1);
        manager.sync();
        assertTrue(defeats.isEmpty(), "a living boss is not a defeat");

        now[0] += 2_500L;
        boss.takeDamage(1);
        manager.sync();
        manager.sync();
        boss.takeDamage(boss.getMaxHealth());
        manager.sync();

        assertEquals(1, defeats.size(), "the defeat is published exactly once");
        BossDefeatedEvent event = defeats.get(0);
        assertEquals("run", event.getRunId());
        assertSame(DefeatedEnemyType.BOSS_B, event.getBossType());
        assertEquals(4_000L, event.getBattleDurationMillis());
        assertTrue(event.getEventId().startsWith("BOSS_B:"),
                "the defeat ID is grouped under its boss type");
    }

    @Test
    void eachBossGetsItsOwnEventAndDuration() {
        BossManager manager = manager("run");
        Boss first = spawn(manager, Boss.BossType.BOSS_A);
        frame(manager);
        now[0] += 1_000L;
        first.takeDamage(first.getMaxHealth());
        manager.sync();

        Boss second = manager.spawn(new BossSpawnRequest(Boss.BossType.BOSS_B, 100, 78, 5, 0));
        frame(manager);
        now[0] += 3_000L;
        second.takeDamage(second.getMaxHealth());
        manager.sync();

        assertEquals(2, defeats.size());
        assertSame(DefeatedEnemyType.BOSS_A, defeats.get(0).getBossType());
        assertEquals(1_000L, defeats.get(0).getBattleDurationMillis());
        assertSame(DefeatedEnemyType.BOSS_B, defeats.get(1).getBossType());
        assertEquals(3_000L, defeats.get(1).getBattleDurationMillis());
        assertTrue(!defeats.get(0).getEventId().equals(defeats.get(1).getEventId()),
                "two bosses are two distinct defeats");
    }

    @Test
    void theDefeatIsStillAnnouncedAsABossEvent() {
        BossManager manager = manager("run");
        List<BossEvent.Type> bossEvents = new ArrayList<BossEvent.Type>();
        manager.getEventManager().addListener(event -> bossEvents.add(event.getType()));
        Boss boss = spawn(manager, Boss.BossType.BOSS_A);

        boss.takeDamage(boss.getMaxHealth());
        manager.sync();

        assertTrue(bossEvents.contains(BossEvent.Type.DEFEATED));
        assertEquals(1, defeats.size());
    }

    @Test
    void theStartCountdownIsNotPartOfTheBattle() {
        BossManager manager = manager("run");
        Boss boss = spawn(manager, Boss.BossType.BOSS_A);

        // GameScreen spawns the boss, then runs no boss frame until its
        // start countdown is over.
        now[0] += 6_000L;
        frame(manager);
        now[0] += 500L;
        frame(manager);
        now[0] += 1_000L;
        boss.takeDamage(boss.getMaxHealth());
        manager.sync();

        assertEquals(1, defeats.size());
        assertEquals(1_500L, defeats.get(0).getBattleDurationMillis(),
                "the battle runs from the first boss frame, not from the spawn");
    }

    @Test
    void aBossDefeatedBeforeItsBattleStartsHasNoDuration() {
        BossManager manager = manager("run");
        Boss boss = spawn(manager, Boss.BossType.BOSS_A);

        now[0] += 6_000L;
        boss.takeDamage(boss.getMaxHealth());
        manager.sync();

        assertEquals(1, defeats.size());
        assertEquals(0L, defeats.get(0).getBattleDurationMillis());
    }

    @Test
    void everyBossTypeHasItsOwnClassification() {
        BossManager manager = manager("run");
        int level = 1;
        for (Boss.BossType type : Boss.BossType.values()) {
            Boss boss = manager.spawn(new BossSpawnRequest(type, 100, 78, level++, 0));
            boss.takeDamage(boss.getMaxHealth());
            manager.sync();
        }

        assertEquals(Boss.BossType.values().length, defeats.size());
        for (int i = 0; i < defeats.size(); i++) {
            String expected = Boss.BossType.values()[i].name();
            assertEquals(expected, defeats.get(i).getBossType().name(),
                    "no boss type is reported as UNKNOWN");
            assertTrue(defeats.get(i).getEventId().startsWith(expected + ":"));
        }
    }

    @Test
    void aBossSpawnedByADefeatListenerDoesNotReplaceTheDefeatedOne() {
        BossManager manager = manager("run");
        Boss[] next = new Boss[1];
        manager.getEventManager().addListener(event -> {
            if (event.getType() == BossEvent.Type.DEFEATED && next[0] == null)
                next[0] = manager.spawn(
                        new BossSpawnRequest(Boss.BossType.BOSS_B, 100, 78, 5, 0));
        });
        Boss first = spawn(manager, Boss.BossType.BOSS_A);
        frame(manager);

        now[0] += 1_000L;
        first.takeDamage(first.getMaxHealth());
        manager.sync();

        assertTrue(next[0] != null && !next[0].isDefeated(),
                "the listener spawned a living boss");
        assertEquals(1, defeats.size());
        assertSame(DefeatedEnemyType.BOSS_A, defeats.get(0).getBossType());
        assertEquals(1_000L, defeats.get(0).getBattleDurationMillis());
        assertTrue(defeats.get(0).getEventId().startsWith("BOSS_A:"));

        frame(manager);
        now[0] += 2_000L;
        next[0].takeDamage(next[0].getMaxHealth());
        manager.sync();

        assertEquals(2, defeats.size());
        assertSame(DefeatedEnemyType.BOSS_B, defeats.get(1).getBossType());
        assertEquals(2_000L, defeats.get(1).getBattleDurationMillis());
        assertTrue(!defeats.get(0).getEventId().equals(defeats.get(1).getEventId()),
                "the spawned boss gets its own defeat ID");
    }

    @Test
    void nothingIsPublishedOutsideARecordedRun() {
        BossManager unrecorded = manager(null);
        Boss boss = spawn(unrecorded, Boss.BossType.BOSS_A);
        boss.takeDamage(boss.getMaxHealth());
        unrecorded.sync();

        BossManager unconnected = new BossManager(new Random(1), () -> now[0]);
        Boss other = spawn(unconnected, Boss.BossType.BOSS_A);
        other.takeDamage(other.getMaxHealth());
        unconnected.sync();

        assertTrue(defeats.isEmpty());
    }
}
