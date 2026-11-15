package event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerHitEventTest {

    @Test
    void carriesTheValuesItWasGiven() {
        for (PlayerHitSource source : PlayerHitSource.values()) {
            PlayerHitEvent event = new PlayerHitEvent("run", "hit", source, 2, 1);
            assertEquals("run", event.getRunId());
            assertEquals("hit", event.getEventId());
            assertSame(source, event.getSource(), "source " + source);
            assertEquals(2, event.getDamage());
            assertEquals(1, event.getLivesRemaining());
        }
    }

    @Test
    void rejectsAMissingSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerHitEvent("run", "hit", null, 1, 2));
    }

    @Test
    void aHitTakesAtLeastOneLife() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerHitEvent("run", "hit", PlayerHitSource.ENEMY_BULLET, 0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerHitEvent("run", "hit", PlayerHitSource.ENEMY_BULLET, -1, 2));
    }

    @Test
    void aFinalHitLeavesZeroLivesButNeverFewer() {
        assertEquals(0, new PlayerHitEvent("run", "hit",
                PlayerHitSource.BOSS_BULLET, 1, 0).getLivesRemaining());
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerHitEvent("run", "hit", PlayerHitSource.BOSS_BULLET, 1, -1));
    }

    @Test
    void requiresBothIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerHitEvent(null, "hit", PlayerHitSource.UNKNOWN, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerHitEvent("run", null, PlayerHitSource.UNKNOWN, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerHitEvent(" ", "hit", PlayerHitSource.UNKNOWN, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerHitEvent("run", " ", PlayerHitSource.UNKNOWN, 1, 0));
    }

    @Test
    void coversEveryHitSourceTheScreensReport() {
        assertSame(PlayerHitSource.ENEMY_BULLET, PlayerHitSource.valueOf("ENEMY_BULLET"));
        assertSame(PlayerHitSource.BOSS_BULLET, PlayerHitSource.valueOf("BOSS_BULLET"));
        assertSame(PlayerHitSource.ENEMY_COLLISION, PlayerHitSource.valueOf("ENEMY_COLLISION"));
        assertSame(PlayerHitSource.UNKNOWN, PlayerHitSource.valueOf("UNKNOWN"));
        assertEquals(4, PlayerHitSource.values().length);
    }

    @Test
    void reachesItsListenerThroughThePublisher() {
        GameplayEventPublisher publisher = new GameplayEventPublisher();
        PlayerHitEvent[] received = new PlayerHitEvent[1];
        publisher.subscribe(PlayerHitEvent.class, event -> received[0] = event);

        PlayerHitEvent sent = new PlayerHitEvent("run", "hit", PlayerHitSource.ENEMY_COLLISION, 1, 2);
        publisher.publish(sent);

        assertSame(sent, received[0]);
    }
}
