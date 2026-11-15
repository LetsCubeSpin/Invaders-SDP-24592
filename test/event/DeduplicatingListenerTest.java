package event;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeduplicatingListenerTest {

    @Test
    void aRedeliveredActionIsHandledOnce() {
        List<PlayerHitEvent> handled = new ArrayList<PlayerHitEvent>();
        DeduplicatingListener<PlayerHitEvent> listener =
                new DeduplicatingListener<PlayerHitEvent>(handled::add);
        PlayerHitEvent hit = new PlayerHitEvent("run", "hit", PlayerHitSource.ENEMY_BULLET, 1, 2);

        listener.onEvent(hit);
        listener.onEvent(hit);

        assertEquals(1, handled.size());
        assertSame(hit, handled.get(0));
    }

    @Test
    void aCopyWithTheSameIdsIsTheSameAction() {
        List<ShotFiredEvent> handled = new ArrayList<ShotFiredEvent>();
        DeduplicatingListener<ShotFiredEvent> listener =
                new DeduplicatingListener<ShotFiredEvent>(handled::add);

        listener.onEvent(new ShotFiredEvent("run", "shot", 1));
        listener.onEvent(new ShotFiredEvent("run", "shot", 1));

        assertEquals(1, handled.size(), "the IDs decide, not the event object");
    }

    @Test
    void distinctActionsAreAllHandled() {
        List<ItemUsedEvent> handled = new ArrayList<ItemUsedEvent>();
        DeduplicatingListener<ItemUsedEvent> listener =
                new DeduplicatingListener<ItemUsedEvent>(handled::add);

        listener.onEvent(new ItemUsedEvent("run", "use-1", UsedItemType.CLUSTER_GRENADE));
        listener.onEvent(new ItemUsedEvent("run", "use-2", UsedItemType.CLUSTER_GRENADE));

        assertEquals(2, handled.size());
    }

    @Test
    void theSameEventIdInAnotherRunIsAnotherAction() {
        List<BossDefeatedEvent> handled = new ArrayList<BossDefeatedEvent>();
        DeduplicatingListener<BossDefeatedEvent> listener =
                new DeduplicatingListener<BossDefeatedEvent>(handled::add);

        listener.onEvent(new BossDefeatedEvent("run-1", "boss", DefeatedEnemyType.BOSS_A, 1L));
        listener.onEvent(new BossDefeatedEvent("run-2", "boss", DefeatedEnemyType.BOSS_A, 1L));
        listener.onEvent(new BossDefeatedEvent("run-1", "boss", DefeatedEnemyType.BOSS_A, 1L));

        assertEquals(2, handled.size());
        assertEquals("run-1", handled.get(0).getRunId());
        assertEquals("run-2", handled.get(1).getRunId());
    }

    @Test
    void aFailedHandlingIsNotRetriedByARedelivery() {
        int[] calls = new int[1];
        DeduplicatingListener<ShotFiredEvent> listener =
                new DeduplicatingListener<ShotFiredEvent>(event -> {
                    calls[0]++;
                    throw new IllegalStateException("listener failed");
                });
        GameplayEventPublisher publisher = new GameplayEventPublisher();
        publisher.subscribe(ShotFiredEvent.class, listener);
        ShotFiredEvent shot = new ShotFiredEvent("run", "shot", 1);

        // The publisher logs the failure and keeps going.
        publisher.publish(shot);
        publisher.publish(shot);

        assertEquals(1, calls[0]);
    }

    @Test
    void worksThroughThePublisherForEveryNewEvent() {
        GameplayEventPublisher publisher = new GameplayEventPublisher();
        List<IdentifiedGameplayEvent> handled = new ArrayList<IdentifiedGameplayEvent>();
        publisher.subscribe(BossDefeatedEvent.class,
                new DeduplicatingListener<BossDefeatedEvent>(handled::add));
        publisher.subscribe(PlayerHitEvent.class,
                new DeduplicatingListener<PlayerHitEvent>(handled::add));
        publisher.subscribe(ItemUsedEvent.class,
                new DeduplicatingListener<ItemUsedEvent>(handled::add));
        publisher.subscribe(ShotFiredEvent.class,
                new DeduplicatingListener<ShotFiredEvent>(handled::add));

        IdentifiedGameplayEvent[] events = {
            new BossDefeatedEvent("run", "a", DefeatedEnemyType.BOSS_B, 5L),
            new PlayerHitEvent("run", "a", PlayerHitSource.BOSS_BULLET, 1, 1),
            new ItemUsedEvent("run", "a", UsedItemType.ENERGY_SHIELD),
            new ShotFiredEvent("run", "a", 2) };
        for (int round = 0; round < 2; round++)
            for (IdentifiedGameplayEvent event : events)
                publisher.publish(event);

        assertEquals(4, handled.size(),
                "each event type keeps its own IDs, and each is handled once");
    }

    @Test
    void rejectsAMissingDelegate() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeduplicatingListener<ShotFiredEvent>(null));
    }
}
