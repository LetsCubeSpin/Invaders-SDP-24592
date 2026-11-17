package engine;

import campaign.CampaignStageData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Checks that the game runs on the single Easy difficulty. */
class GameplayConfigTest {

    @Test
    void difficultyIsAlwaysEasy() {
        assertEquals(GameplayConfig.EASY, GameplayConfig.getDifficulty());
        assertEquals(0, GameplayConfig.EASY);
    }

    @Test
    void multiplierIsTheEasyMultiplier() {
        assertEquals(1.25, GameplayConfig.getMultiplier());
    }

    @Test
    void difficultyCannotBeChangedAnymore() {
        boolean hasSetter = Arrays.stream(GameplayConfig.class.getDeclaredMethods())
                .anyMatch(method -> Modifier.isPublic(method.getModifiers())
                        && method.getName().equals("setDifficulty"));
        assertFalse(hasSetter, "Difficulty is fixed, so there is no setter");
    }

    @Test
    void onlyEasyConstantRemains() {
        boolean hasOtherLevels = Arrays.stream(GameplayConfig.class.getFields())
                .anyMatch(field -> field.getName().equals("NORMAL")
                        || field.getName().equals("HARD"));
        assertFalse(hasOtherLevels, "Normal and Hard were removed");
    }

    @Test
    void everyStageIsSlowerAndLessAggressiveThanItsConfiguredValues() {
        // Easy multiplies the stage values by 1.25: enemies move and shoot less often.
        for (int stage = 1; stage <= CampaignStageData.getTotalStages(); stage++) {
            GameSettings configured = CampaignStageData.getStageSettings(stage);
            GameSettings applied = Core.applyDifficulty(configured,
                    GameplayConfig.getMultiplier());
            String context = "stage " + stage;

            assertEquals(Math.max(1, (int) Math.round(configured.getBaseSpeed() * 1.25)),
                    applied.getBaseSpeed(), context);
            assertEquals(Math.max(250,
                    (int) Math.round(configured.getShootingFrecuency() * 1.25)),
                    applied.getShootingFrecuency(), context);
            assertEquals(configured.getFormationWidth(),
                    applied.getFormationWidth(), context);
            assertEquals(configured.getFormationHeight(),
                    applied.getFormationHeight(), context);
        }
    }
}
