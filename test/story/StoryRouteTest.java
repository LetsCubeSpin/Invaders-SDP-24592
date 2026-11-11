package story;

import entity.Boss;
import entity.BulletType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryRouteTest {

    @Test
    void eachAnswerPairLeadsToItsRoute() {
        assertEquals(StoryRoute.HYBRID, StoryRoute.of(0, 0));
        assertEquals(StoryRoute.HUMAN_VICTORY, StoryRoute.of(0, 1));
        assertEquals(StoryRoute.HOMECOMING, StoryRoute.of(1, 0));
        assertEquals(StoryRoute.TRUCE, StoryRoute.of(1, 1));
    }

    @Test
    void routesKeepTheAnswersTheyWereFoundWith() {
        for (StoryRoute route : StoryRoute.values())
            assertEquals(route, StoryRoute.of(route.getFirstChoice(),
                    route.getSecondChoice()));
    }

    @Test
    void endingNumbersAreOneToFourWithoutRepeats() {
        Set<Integer> numbers = new HashSet<Integer>();
        for (StoryRoute route : StoryRoute.values()) {
            int number = route.getEndingNumber();
            assertTrue(number >= 1 && number <= 4);
            assertTrue(numbers.add(number), "Repeated ending " + number);
        }
        assertEquals(4, numbers.size());
    }

    @Test
    void stageThreeBossDependsOnlyOnTheFirstAnswer() {
        assertEquals(Boss.BossType.BOSS_A, StoryRoute.stageThreeBoss(0));
        assertEquals(Boss.BossType.BOSS_B, StoryRoute.stageThreeBoss(1));
        assertEquals(StoryRoute.HYBRID.getStageThreeBoss(),
                StoryRoute.HUMAN_VICTORY.getStageThreeBoss());
        assertEquals(StoryRoute.HOMECOMING.getStageThreeBoss(),
                StoryRoute.TRUCE.getStageThreeBoss());
    }

    @Test
    void routeWeaponsMatchTheStory() {
        assertEquals(BulletType.MULTI, StoryRoute.HYBRID.getWeapon());
        assertEquals(BulletType.RAPID, StoryRoute.HUMAN_VICTORY.getWeapon());
        assertEquals(BulletType.NORMAL, StoryRoute.HOMECOMING.getWeapon());
    }

    @Test
    void onlyTheEscortRouteGivesGrenades() {
        assertTrue(StoryRoute.HOMECOMING.givesGrenades());
        assertFalse(StoryRoute.HYBRID.givesGrenades());
        assertFalse(StoryRoute.HUMAN_VICTORY.givesGrenades());
        assertFalse(StoryRoute.TRUCE.givesGrenades());
    }

    @Test
    void choicesOtherThanZeroOrOneAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> StoryRoute.of(2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StoryRoute.of(0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> StoryRoute.stageThreeBoss(5));
    }
}
