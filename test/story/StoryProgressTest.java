package story;

import entity.Boss;
import entity.BulletType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryProgressTest {

    private static StoryProgress answered(final int first, final int second) {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(first);
        progress.chooseSecond(second);
        return progress;
    }

    @Test
    void newProgressHasNoAnswers() {
        StoryProgress progress = new StoryProgress();
        assertFalse(progress.hasFirstChoice());
        assertFalse(progress.hasSecondChoice());
        assertThrows(IllegalStateException.class, progress::getRoute);
    }

    @Test
    void bothAnswersDecideTheRoute() {
        assertEquals(StoryRoute.HYBRID, answered(0, 0).getRoute());
        assertEquals(StoryRoute.HUMAN_VICTORY, answered(0, 1).getRoute());
        assertEquals(StoryRoute.HOMECOMING, answered(1, 0).getRoute());
        assertEquals(StoryRoute.TRUCE, answered(1, 1).getRoute());
    }

    @Test
    void secondQuestionNeedsTheFirstAnswer() {
        StoryProgress progress = new StoryProgress();
        assertThrows(IllegalStateException.class,
                () -> progress.chooseSecond(0));
        assertFalse(progress.hasSecondChoice());
    }

    @Test
    void eachQuestionCanBeAnsweredOnlyOnce() {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(0);
        assertThrows(IllegalStateException.class,
                () -> progress.chooseFirst(1));
        progress.chooseSecond(1);
        assertThrows(IllegalStateException.class,
                () -> progress.chooseSecond(0));
        assertEquals(StoryRoute.HUMAN_VICTORY, progress.getRoute());
    }

    @Test
    void invalidAnswersAreRejectedAndNotSaved() {
        StoryProgress progress = new StoryProgress();
        assertThrows(IllegalArgumentException.class,
                () -> progress.chooseFirst(2));
        assertFalse(progress.hasFirstChoice());

        progress.chooseFirst(1);
        assertThrows(IllegalArgumentException.class,
                () -> progress.chooseSecond(-1));
        assertFalse(progress.hasSecondChoice());
    }

    @Test
    void onlyStagesThreeAndSevenHaveBosses() {
        for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++)
            assertEquals(stage == 3 || stage == 7,
                    StoryProgress.isBossStage(stage), "stage " + stage);
    }

    @Test
    void stageThreeBossIsKnownBeforeTheSecondQuestion() {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(1);
        assertEquals(Boss.BossType.BOSS_B, progress.bossForStage(3));
        assertNull(progress.bossForStage(2));
    }

    @Test
    void finalBossComesFromTheRoute() {
        for (StoryRoute route : StoryRoute.values()) {
            StoryProgress progress = answered(route.getFirstChoice(),
                    route.getSecondChoice());
            assertEquals(route.getStageThreeBoss(), progress.bossForStage(3));
            assertEquals(route.getFinalBoss(), progress.bossForStage(7));
        }
    }

    @Test
    void firstThreeStagesKeepTheShipWeapon() {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(0);
        for (int stage = 1; stage <= 3; stage++)
            assertEquals(BulletType.RAPID,
                    progress.weaponForStage(stage, BulletType.RAPID));
    }

    @Test
    void laterStagesUseTheRouteWeapon() {
        StoryProgress progress = answered(0, 0);
        for (int stage = 4; stage <= 7; stage++)
            assertEquals(StoryRoute.HYBRID.getWeapon(),
                    progress.weaponForStage(stage, BulletType.NORMAL));
    }

    @Test
    void grenadesOnlyFromStageFourOnTheEscortRoute() {
        StoryProgress escort = answered(1, 0);
        assertFalse(escort.givesGrenadesOnStage(3));
        for (int stage = 4; stage <= 7; stage++)
            assertTrue(escort.givesGrenadesOnStage(stage));

        StoryProgress other = answered(0, 1);
        for (int stage = 4; stage <= 7; stage++)
            assertFalse(other.givesGrenadesOnStage(stage));
    }

    @Test
    void stagesOutsideOneToSevenAreRejected() {
        StoryProgress progress = answered(0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> StoryProgress.isBossStage(0));
        assertThrows(IllegalArgumentException.class,
                () -> progress.bossForStage(8));
        assertThrows(IllegalArgumentException.class,
                () -> progress.weaponForStage(0, BulletType.NORMAL));
    }

    @Test
    void shipWeaponMustBeGiven() {
        StoryProgress progress = answered(0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> progress.weaponForStage(1, null));
    }
}
