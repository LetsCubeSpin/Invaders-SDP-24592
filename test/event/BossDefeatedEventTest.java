package event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BossDefeatedEventTest {

    @Test
    void carriesTheValuesItWasGiven() {
        BossDefeatedEvent event =
                new BossDefeatedEvent("run", "boss", DefeatedEnemyType.BOSS_B, 42_000L);

        assertEquals("run", event.getRunId());
        assertEquals("boss", event.getEventId());
        assertSame(DefeatedEnemyType.BOSS_B, event.getBossType());
        assertEquals(42_000L, event.getBattleDurationMillis());
    }

    @Test
    void acceptsEveryBossClassification() {
        DefeatedEnemyType[] bosses = {
            DefeatedEnemyType.BOSS_A, DefeatedEnemyType.BOSS_B, DefeatedEnemyType.BOSS_C,
            DefeatedEnemyType.UNKNOWN };
        for (DefeatedEnemyType type : bosses)
            assertSame(type, new BossDefeatedEvent("run", "boss", type, 0L).getBossType());
    }

    @Test
    void rejectsANormalEnemyClassification() {
        DefeatedEnemyType[] normals = {
            DefeatedEnemyType.A, DefeatedEnemyType.B, DefeatedEnemyType.C,
            DefeatedEnemyType.BONUS };
        for (DefeatedEnemyType type : normals)
            assertThrows(IllegalArgumentException.class,
                    () -> new BossDefeatedEvent("run", "boss", type, 0L), type.name());
    }

    @Test
    void rejectsAMissingBossType() {
        assertThrows(IllegalArgumentException.class,
                () -> new BossDefeatedEvent("run", "boss", null, 0L));
    }

    @Test
    void acceptsAZeroDurationButNotANegativeOne() {
        assertEquals(0L, new BossDefeatedEvent("run", "boss",
                DefeatedEnemyType.BOSS_A, 0L).getBattleDurationMillis());
        assertThrows(IllegalArgumentException.class,
                () -> new BossDefeatedEvent("run", "boss", DefeatedEnemyType.BOSS_A, -1L));
    }

    @Test
    void requiresBothIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new BossDefeatedEvent(null, "boss", DefeatedEnemyType.BOSS_A, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new BossDefeatedEvent("run", null, DefeatedEnemyType.BOSS_A, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new BossDefeatedEvent(" ", "boss", DefeatedEnemyType.BOSS_A, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new BossDefeatedEvent("run", " ", DefeatedEnemyType.BOSS_A, 0L));
    }

    @Test
    void reachesOnlyItsOwnListeners() {
        GameplayEventPublisher publisher = new GameplayEventPublisher();
        int[] bossDefeats = new int[1];
        int[] enemyDefeats = new int[1];
        publisher.subscribe(BossDefeatedEvent.class, event -> bossDefeats[0]++);
        publisher.subscribe(EnemyDefeatedEvent.class, event -> enemyDefeats[0]++);

        publisher.publish(new BossDefeatedEvent("run", "boss", DefeatedEnemyType.BOSS_A, 1L));

        assertEquals(1, bossDefeats[0]);
        assertEquals(0, enemyDefeats[0], "a boss defeat event is not an enemy defeat event");
    }
}
