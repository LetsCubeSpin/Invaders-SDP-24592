package screen;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import engine.Core;
import engine.DrawManager;
import engine.GameState;
import engine.LanguageManager;
import entity.ShipType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import story.StoryProgress;
import story.StoryRoute;
import story.StoryScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boss of stage 3 says its line when it appears: its portrait on the
 * left and the line to its right, at the top of the board like the woman
 * at the control tower.
 */
class StoryBossDialogueTest {

    private static final int GREEN = Color.GREEN.getRGB() & 0xFFFFFF;
    /** Line under the HUD, where the dialogue at the top starts. */
    private static final int HUD_LINE = 40;
    /** Top of the dialogue at the top of the board. */
    private static final int DIALOGUE_TOP = HUD_LINE + 4;
    /** Top of the pilot's dialogue, just under the lower line of the banner. */
    private static final int PILOT_TOP = 520 / 2 + 520 / 12 + 19;
    private static final int LEFT = 5;

    private LanguageManager.Language previousLanguage;

    @BeforeEach
    void useEnglish() {
        // The texts and the font follow the language the player chose.
        previousLanguage = LanguageManager.getCurrentLanguage();
        LanguageManager.setCurrentLanguage(LanguageManager.Language.ENGLISH);
    }

    @AfterEach
    void stopAudioAndRestoreLanguage() {
        audio.AudioManager.stopBGM();
        LanguageManager.setCurrentLanguage(previousLanguage);
    }

    private static GameScreen stage(final int stage, final StoryRoute route) {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(route.getFirstChoice());
        progress.chooseSecond(route.getSecondChoice());
        GameScreen screen = GameScreen.forStoryStage(
                new GameState(stage, 0, 3, 0, 0), progress,
                UnaryOperator.identity(), false, 448, 520, 60,
                ShipType.STANDARD, null);
        audio.AudioManager.setMuted(true);
        screen.initialize();
        return screen;
    }

    private static BufferedImage draw(final GameScreen screen) throws Exception {
        Method draw = GameScreen.class.getDeclaredMethod("draw");
        draw.setAccessible(true);
        draw.invoke(screen);
        Field buffer = DrawManager.class.getDeclaredField("backBuffer");
        buffer.setAccessible(true);
        BufferedImage image = (BufferedImage) buffer.get(null);
        BufferedImage copy = new BufferedImage(image.getWidth(),
                image.getHeight(), BufferedImage.TYPE_INT_RGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        return copy;
    }

    private static int greenInRow(final BufferedImage image, final int y,
            final int from, final int to) {
        int count = 0;
        for (int x = from; x <= to; x++) {
            if ((image.getRGB(x, y) & 0xFFFFFF) == GREEN) {
                count++;
            }
        }
        return count;
    }

    @Test
    void theBossStageStartsWithTheBossesLineAndHoldsTheStage() {
        for (StoryRoute route : new StoryRoute[] {StoryRoute.HYBRID, StoryRoute.HOMECOMING}) {
            GameScreen screen = stage(StoryProgress.MID_BOSS_STAGE, route);
            assertTrue(screen.isStoryIntroHolding(), route + " waits for the line");
        }
    }

    @Test
    void spaceShowsTheWholeLineThenStartsTheStage() {
        GameScreen screen = stage(StoryProgress.MID_BOSS_STAGE, StoryRoute.HYBRID);
        long now = System.currentTimeMillis();
        screen.updateStoryIntro(false, now);
        // Space while typing shows the rest of the line but does not start.
        screen.updateStoryIntro(true, now + 100);
        assertTrue(screen.isStoryIntroHolding());
        screen.updateStoryIntro(false, now + 150);
        // Space once the line is complete starts the stage.
        screen.updateStoryIntro(true, now + 200);
        assertFalse(screen.isStoryIntroHolding());
    }

    @Test
    void theBossPortraitIsAtTheTopLeftWithTheLineNextToIt() throws Exception {
        for (StoryRoute route : new StoryRoute[] {StoryRoute.HYBRID, StoryRoute.HOMECOMING}) {
            GameScreen screen = stage(StoryProgress.MID_BOSS_STAGE, route);
            screen.updateStoryIntro(false, System.currentTimeMillis());
            BufferedImage image = draw(screen);

            // A square portrait, 160 wide, then the text box, just under the HUD.
            assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT, LEFT + 159) > 100,
                    route + ": portrait border");
            assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT + 175, 442) > 200,
                    route + ": text box border");
            // Nothing of it is under the banner, where the pilot speaks.
            assertTrue(greenInRow(image, PILOT_TOP, LEFT, 442) < 20,
                    route + ": no dialogue below the banner");
        }
    }

    @Test
    void theTwoBossesHaveDifferentPortraits() throws Exception {
        BufferedImage first = draw(stage(StoryProgress.MID_BOSS_STAGE, StoryRoute.HYBRID));
        BufferedImage second = draw(stage(StoryProgress.MID_BOSS_STAGE, StoryRoute.HOMECOMING));
        int[] a = first.getRGB(LEFT, DIALOGUE_TOP, 160, 160, null, 0, 160);
        int[] b = second.getRGB(LEFT, DIALOGUE_TOP, 160, 160, null, 0, 160);
        assertFalse(Arrays.equals(a, b), "different bosses, different pictures");
    }

    @Test
    void thePilotStillSpeaksAtStageOne() throws Exception {
        GameScreen screen = stage(1, StoryRoute.HYBRID);
        screen.updateStoryIntro(false, System.currentTimeMillis());
        BufferedImage image = draw(screen);
        // The pilot's portrait is 115 wide, so its text box starts at x = 134.
        assertTrue(greenInRow(image, PILOT_TOP, LEFT, LEFT + 110) > 100, "pilot portrait");
        assertTrue(greenInRow(image, PILOT_TOP, LEFT + 130, 442) > 250,
                "wide text box next to the pilot");
        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT, LEFT + 159) < 20,
                "nothing at the top");
    }

    @Test
    void everyBossLineFitsInTwoLinesOfTheBoxInBothLanguages() {
        DrawManager draw = Core.getDrawManager();
        Screen screen = new Screen(448, 520, 60);
        for (LanguageManager.Language language : LanguageManager.Language.values()) {
            LanguageManager.setCurrentLanguage(language);
            draw.initDrawing(screen);
            int portrait = draw.bossPortraitWidth(screen, HUD_LINE);
            assertEquals(160, portrait);
            for (int boss = 0; boss <= 1; boss++) {
                String line = StoryScript.bossIntroLine(StoryProgress.MID_BOSS_STAGE, boss);
                assertEquals(2, draw.countDialogueLines(screen, line, portrait),
                        language + ", boss " + boss + ": '" + line.replace("\n", " / ") + "'");
            }
        }
    }
}
