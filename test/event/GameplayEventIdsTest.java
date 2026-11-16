package event;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayEventIdsTest {

    @Test
    void anIdStartsWithItsKindAndTheDefeatIdSeparator() {
        String id = GameplayEventIds.newId("ENEMY_BULLET");

        assertTrue(id.startsWith("ENEMY_BULLET" + GameplayEventIds.SEPARATOR), id);
        assertTrue(id.length() > "ENEMY_BULLET:".length(), "the random part follows the kind");
    }

    @Test
    void usesTheSameSeparatorAsTheEnemyDefeatIds() {
        assertEquals(":", GameplayEventIds.SEPARATOR);
    }

    @Test
    void everyCallGivesADistinctId() {
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < 1000; i++)
            ids.add(GameplayEventIds.newId("SHOT"));

        assertEquals(1000, ids.size(), "two actions of the same kind never share an ID");
    }

    @Test
    void rejectsAMissingKind() {
        assertThrows(IllegalArgumentException.class, () -> GameplayEventIds.newId(null));
        assertThrows(IllegalArgumentException.class, () -> GameplayEventIds.newId(" "));
    }

    @Test
    void anIdIsAcceptedByEveryNewEvent() {
        String id = GameplayEventIds.newId("KIND");

        assertEquals(id, new BossDefeatedEvent("run", id, DefeatedEnemyType.BOSS_A, 0L).getEventId());
        assertEquals(id, new PlayerHitEvent("run", id, PlayerHitSource.UNKNOWN, 1, 0).getEventId());
        assertEquals(id, new ItemUsedEvent("run", id, UsedItemType.ENERGY_SHIELD).getEventId());
        assertEquals(id, new ShotFiredEvent("run", id, 1).getEventId());
    }
}
