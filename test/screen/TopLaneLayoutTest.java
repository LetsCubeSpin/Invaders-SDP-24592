package screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import campaign.CampaignStageData;
import engine.DrawManager;
import engine.GameSettings;
import entity.Boss;
import entity.EnemyShip;

/**
 * The bands at the top of the play area stack without overlapping: the
 * status strip under the HUD, the bonus ship's path, the boss, and the
 * enemy formation.
 */
class TopLaneLayoutTest {

    @Test
    void theStatusStripEndsAboveTheBonusShip() {
        EnemyShip bonus = new EnemyShip();
        // GameScreen draws the HUD separator on y 39.
        assertTrue(DrawManager.STATUS_STRIP_TOP > 39, "the strip starts under the HUD separator");
        assertTrue(DrawManager.STATUS_STRIP_TOP + DrawManager.STATUS_STRIP_HEIGHT
                <= bonus.getPositionY(), "the strip ends above the bonus ship");
        assertTrue(DrawManager.STATUS_STRIP_HEIGHT >= 26,
                "the strip is tall enough for a 26 pixel notice: " + DrawManager.STATUS_STRIP_HEIGHT);
    }

    @Test
    void theBossAppearsBetweenTheBonusShipAndTheFormation() {
        EnemyShip bonus = new EnemyShip();
        assertTrue(bonus.getPositionY() + bonus.getHeight() <= GameScreen.BOSS_SPAWN_Y,
                "a bonus ship passing the boss does not overlap it");
        for (GameSettings stage : CampaignStageData.getCampaignSettings())
            assertTrue(GameScreen.BOSS_SPAWN_Y + Boss.HEIGHT <= stage.getInitialY(),
                    "the boss stays above a formation starting at y " + stage.getInitialY());
    }
}
