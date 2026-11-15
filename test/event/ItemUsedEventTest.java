package event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemUsedEventTest {

    @Test
    void carriesTheItemTypeItWasGiven() {
        for (UsedItemType type : UsedItemType.values()) {
            ItemUsedEvent event = new ItemUsedEvent("run", "use", type);
            assertEquals("run", event.getRunId());
            assertEquals("use", event.getEventId());
            assertSame(type, event.getItemType(), "item " + type);
        }
    }

    @Test
    void rejectsAMissingItemType() {
        assertThrows(IllegalArgumentException.class,
                () -> new ItemUsedEvent("run", "use", null));
    }

    @Test
    void requiresBothIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ItemUsedEvent(null, "use", UsedItemType.CLUSTER_GRENADE));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemUsedEvent("run", null, UsedItemType.CLUSTER_GRENADE));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemUsedEvent(" ", "use", UsedItemType.CLUSTER_GRENADE));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemUsedEvent("run", " ", UsedItemType.CLUSTER_GRENADE));
    }

    @Test
    void coversEveryUsableItem() {
        assertSame(UsedItemType.CLUSTER_GRENADE, UsedItemType.valueOf("CLUSTER_GRENADE"));
        assertSame(UsedItemType.ENERGY_SHIELD, UsedItemType.valueOf("ENERGY_SHIELD"));
        assertEquals(2, UsedItemType.values().length,
                "a new usable item needs a publish site in the game screens");
    }

    @Test
    void reachesItsListenerThroughThePublisher() {
        GameplayEventPublisher publisher = new GameplayEventPublisher();
        ItemUsedEvent[] received = new ItemUsedEvent[1];
        publisher.subscribe(ItemUsedEvent.class, event -> received[0] = event);

        ItemUsedEvent sent = new ItemUsedEvent("run", "use", UsedItemType.ENERGY_SHIELD);
        publisher.publish(sent);

        assertSame(sent, received[0]);
    }
}
