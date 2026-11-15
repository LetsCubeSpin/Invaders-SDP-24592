package event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShotFiredEventTest {

    @Test
    void carriesTheValuesItWasGiven() {
        ShotFiredEvent event = new ShotFiredEvent("run", "shot", 3);

        assertEquals("run", event.getRunId());
        assertEquals("shot", event.getEventId());
        assertEquals(3, event.getBulletCount());
    }

    @Test
    void aShotFiresAtLeastOneBullet() {
        assertEquals(1, new ShotFiredEvent("run", "shot", 1).getBulletCount());
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFiredEvent("run", "shot", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFiredEvent("run", "shot", -1));
    }

    @Test
    void requiresBothIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFiredEvent(null, "shot", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFiredEvent("run", null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFiredEvent(" ", "shot", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ShotFiredEvent("run", " ", 1));
    }

    @Test
    void bulletCountsAddUpToTheShotTotal() {
        GameplayEventPublisher publisher = new GameplayEventPublisher();
        int[] bulletsShot = new int[1];
        publisher.subscribe(ShotFiredEvent.class,
                event -> bulletsShot[0] += event.getBulletCount());

        publisher.publish(new ShotFiredEvent("run", "shot-1", 1));
        publisher.publish(new ShotFiredEvent("run", "shot-2", 3));

        assertEquals(4, bulletsShot[0]);
    }

    @Test
    void reachesItsListenerThroughThePublisher() {
        GameplayEventPublisher publisher = new GameplayEventPublisher();
        ShotFiredEvent[] received = new ShotFiredEvent[1];
        publisher.subscribe(ShotFiredEvent.class, event -> received[0] = event);

        ShotFiredEvent sent = new ShotFiredEvent("run", "shot", 2);
        publisher.publish(sent);

        assertSame(sent, received[0]);
    }
}
