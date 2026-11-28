package screen;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import story.StorySpeaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first story question is a conversation at the top of the board: the
 * woman at the control tower on the left and her line to its right, instead
 * of the wide box below the banner.
 */
class StoryQuestionDialogueTest {

    /** Colour of the borders of the dialogue boxes. */
    private static final int GREEN = Color.GREEN.getRGB() & 0xFFFFFF;
    /** Line under the HUD, where the dialogue starts. */
    private static final int HUD_LINE = 40;
    /** Top of the dialogue, a few pixels under the HUD line. */
    private static final int DIALOGUE_TOP = HUD_LINE + 4;
    /** Top of the old wide box, just under the lower line of the banner. */
    private static final int OLD_BOX_TOP = 520 / 2 + 520 / 12 + 19;
    /** Left edge of the dialogue portraits. */
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

    private static GameScreen clearedStory(final int stage) {
        return clearedStory(stage, StoryRoute.HYBRID);
    }

    private static GameScreen clearedStory(final int stage,
            final StoryRoute route) {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(route.getFirstChoice());
        progress.chooseSecond(route.getSecondChoice());
        GameScreen screen = GameScreen.forStoryStage(
                new GameState(stage, 0, 3, 0, 0), progress,
                UnaryOperator.identity(), false, 448, 520, 60,
                ShipType.STANDARD, null);
        audio.AudioManager.setMuted(true);
        screen.initialize();
        screen.setStoryChoiceEnabled(true);
        // The stage was played: its own intro is long over.
        write(screen, "storyIntro", enumConstant("storyIntro", "NONE"));
        write(screen, "stageCleared", true);
        write(screen, "levelFinished", true);
        return screen;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(final String field, final String name) {
        try {
            Class<?> type = GameScreen.class.getDeclaredField(field).getType();
            return Enum.valueOf((Class) type, name);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void write(final Object target, final String name,
            final Object value) {
        try {
            Field field = GameScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    /** Draws the whole screen and returns a copy of what was drawn. */
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

    /** Pure green pixels of one row between two columns. */
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

    /** Starts typing the question, then finishes the typing and starts choosing. */
    private static void startTyping(final GameScreen screen) {
        screen.updateStoryQuestion(0, false, false, false, false);
    }

    private static void startChoosing(final GameScreen screen) {
        startTyping(screen);
        screen.updateStoryQuestion(10, true, false, false, false);
        screen.updateStoryQuestion(20, false, false, false, false);
        screen.updateStoryQuestion(30, true, false, false, false);
        screen.updateStoryQuestion(40, false, false, false, false);
    }

    @Test
    void theWomanAsksTheFirstQuestionAndTheSecondAfterTheAnswerZero() {
        assertEquals(StorySpeaker.CONTROL, StoryScript.questionSpeaker(
                StoryProgress.FIRST_QUESTION_STAGE, 0));
        assertEquals(StorySpeaker.CONTROL, StoryScript.questionSpeaker(
                StoryProgress.MID_BOSS_STAGE, 0));
    }

    @Test
    void saraAsksTheSecondQuestionAfterTheAnswerOne() {
        assertEquals(StorySpeaker.SERA, StoryScript.questionSpeaker(
                StoryProgress.MID_BOSS_STAGE, 1));
        assertEquals("SERA", StorySpeaker.SERA.displayName());
        assertEquals("CONTROL", StorySpeaker.CONTROL.displayName());
    }

    @Test
    void stagesWithoutAQuestionHaveNoSpeaker() {
        for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
            if (stage != StoryProgress.FIRST_QUESTION_STAGE
                    && stage != StoryProgress.MID_BOSS_STAGE) {
                for (int choice = 0; choice <= 1; choice++) {
                    assertNull(StoryScript.questionSpeaker(stage, choice),
                            "stage " + stage + ", answer " + choice);
                }
            }
        }
    }

    @Test
    void theFirstQuestionIsAConversationAtTheTopOfTheBoard() throws Exception {
        GameScreen screen = clearedStory(StoryProgress.FIRST_QUESTION_STAGE);
        startTyping(screen);
        BufferedImage image = draw(screen);

        // The portrait's border starts right under the HUD line, and the
        // text box next to it, as with the pilot's dialogue at the bottom.
        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT, LEFT + 110) > 100,
                "portrait border");
        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT + 130, 440) > 250,
                "text box border");
        // The wide box that used to sit below the banner is gone.
        assertTrue(greenInRow(image, OLD_BOX_TOP, LEFT, 442) < 20,
                "no wide box below the banner");
    }

    @Test
    void theConversationStaysWhileThePlayerChooses() throws Exception {
        GameScreen screen = clearedStory(StoryProgress.FIRST_QUESTION_STAGE);
        startChoosing(screen);
        BufferedImage image = draw(screen);

        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT, LEFT + 110) > 100,
                "portrait border while choosing");
        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT + 130, 440) > 250,
                "text box border while choosing");
        // The choices are in the banner under the dialogue, unchanged.
        int upperBanner = 520 / 2 - 520 / 12;
        assertTrue(greenInRow(image, upperBanner, 0, 447) > 400, "banner line");
    }

    @Test
    void theSecondQuestionAfterTheAnswerZeroIsAlsoAConversationAtTheTop()
            throws Exception {
        // HYBRID answered the first question with 0.
        GameScreen screen = clearedStory(StoryProgress.MID_BOSS_STAGE,
                StoryRoute.HYBRID);
        startTyping(screen);
        BufferedImage image = draw(screen);

        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT, LEFT + 110) > 100,
                "portrait border");
        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT + 130, 440) > 250,
                "text box border");
        assertTrue(greenInRow(image, OLD_BOX_TOP, LEFT, 442) < 20,
                "no wide box below the banner");
    }

    @Test
    void theSecondQuestionAfterTheAnswerOneStartsWithANoticeInAWideBoxAtTheTop()
            throws Exception {
        // HOMECOMING answered the first question with 1: Sera was rescued.
        GameScreen screen = clearedStory(StoryProgress.MID_BOSS_STAGE,
                StoryRoute.HOMECOMING);
        startTyping(screen);
        BufferedImage image = draw(screen);

        // One wide box where Sera will speak, right under the HUD line.
        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT, 442) > 400,
                "the notice is in one wide box at the top");
        assertTrue(greenInRow(image, OLD_BOX_TOP, LEFT, 442) < 20,
                "nothing below the banner");
        // No portrait yet: the border where the portrait would end is not there.
        assertNotEquals(GREEN, image.getRGB(LEFT + 114, DIALOGUE_TOP + 120) & 0xFFFFFF,
                "no portrait frame inside the box");
    }

    @Test
    void thenSeraSpeaksAtTheTopLikeTheWomanAtTheControlTower() throws Exception {
        GameScreen screen = clearedStory(StoryProgress.MID_BOSS_STAGE,
                StoryRoute.HOMECOMING);
        startTyping(screen);
        // Space shows the whole notice, and Space again moves on to Sera.
        screen.updateStoryQuestion(10, true, false, false, false);
        screen.updateStoryQuestion(20, false, false, false, false);
        screen.updateStoryQuestion(30, true, false, false, false);
        BufferedImage image = draw(screen);

        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT, LEFT + 110) > 100,
                "portrait border");
        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT + 130, 440) > 250,
                "text box border");
        assertTrue(greenInRow(image, OLD_BOX_TOP, LEFT, 442) < 20,
                "no wide box below the banner");
        // Her portrait is framed: the frame ends where the notice had none.
        assertEquals(GREEN, image.getRGB(LEFT + 114, DIALOGUE_TOP + 120) & 0xFFFFFF,
                "portrait frame");
    }

    @Test
    void saraIsNotTheWomanAtTheControlTower() throws Exception {
        GameScreen sera = clearedStory(StoryProgress.MID_BOSS_STAGE,
                StoryRoute.HOMECOMING);
        GameScreen control = clearedStory(StoryProgress.MID_BOSS_STAGE,
                StoryRoute.HYBRID);
        startTyping(sera);
        sera.updateStoryQuestion(10, true, false, false, false);
        sera.updateStoryQuestion(20, false, false, false, false);
        sera.updateStoryQuestion(30, true, false, false, false);
        startTyping(control);
        BufferedImage first = draw(sera);
        BufferedImage second = draw(control);
        int[] a = first.getRGB(LEFT, DIALOGUE_TOP, 115, 160, null, 0, 115);
        int[] b = second.getRGB(LEFT, DIALOGUE_TOP, 115, 160, null, 0, 115);
        assertFalse(java.util.Arrays.equals(a, b), "different portraits");
    }

    @Test
    void afterHerLineTheChoicesCome() throws Exception {
        GameScreen screen = clearedStory(StoryProgress.MID_BOSS_STAGE,
                StoryRoute.HOMECOMING);
        startTyping(screen);
        // The notice: show it all, then go on to Sera.
        screen.updateStoryQuestion(10, true, false, false, false);
        screen.updateStoryQuestion(20, false, false, false, false);
        screen.updateStoryQuestion(30, true, false, false, false);
        screen.updateStoryQuestion(40, false, false, false, false);
        // Sera's line: show it all, then start choosing.
        screen.updateStoryQuestion(50, true, false, false, false);
        screen.updateStoryQuestion(60, false, false, false, false);
        screen.updateStoryQuestion(70, true, false, false, false);
        screen.updateStoryQuestion(80, false, false, false, false);
        BufferedImage image = draw(screen);

        // She stays on screen while the player chooses, and so do the choices.
        assertTrue(greenInRow(image, DIALOGUE_TOP, LEFT, LEFT + 110) > 100,
                "portrait border while choosing");
        int upperBanner = 520 / 2 - 520 / 12;
        assertTrue(greenInRow(image, upperBanner, 0, 447) > 400, "banner line");
    }

    @Test
    void everyLineOfTheTopConversationsFitsTheBoxInBothLanguages() {
        DrawManager draw = Core.getDrawManager();
        Screen screen = new Screen(448, 520, 60);
        int portrait = (int) Math.round(160 * story.ControllerPortrait.SRC_W
                / story.ControllerPortrait.SRC_H);
        for (LanguageManager.Language language : LanguageManager.Language.values()) {
            LanguageManager.setCurrentLanguage(language);
            draw.initDrawing(screen);
            for (int stage : new int[] {StoryProgress.FIRST_QUESTION_STAGE,
                    StoryProgress.MID_BOSS_STAGE}) {
                for (int first = 0; first < 2; first++) {
                    String line = StoryScript.question(stage, first).getText();
                    int lines = draw.countDialogueLines(screen, line, portrait);
                    assertTrue(lines <= 5, language + ", stage " + stage
                            + ", answer " + first + ": " + lines + " lines");
                }
            }
        }
    }

    @Test
    void herMouthMovesWhileSheTalksAndOnlyThePortraitChanges() throws Exception {
        DrawManager draw = Core.getDrawManager();
        Screen screen = new Screen(448, 520, 60);
        Field buffer = DrawManager.class.getDeclaredField("backBuffer");
        buffer.setAccessible(true);
        // A moment when the mouth is wide open.
        long millis = 0;
        for (long m = 300; m < 20000; m += 5) {
            if (story.ControllerPortrait.mouthOpening(m / 1000.0, true) > 0.9) {
                millis = m;
                break;
            }
        }
        assertTrue(millis > 0, "the mouth opens wide at some time");

        BufferedImage[] images = new BufferedImage[2];
        for (int i = 0; i < 2; i++) {
            draw.initDrawing(screen);
            draw.drawStoryQuestionDialogue(screen, StorySpeaker.CONTROL,
                    "Two signals at once.",
                    20, i == 1, false, HUD_LINE, millis);
            BufferedImage image = (BufferedImage) buffer.get(null);
            images[i] = new BufferedImage(image.getWidth(), image.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            images[i].getGraphics().drawImage(image, 0, 0, null);
        }
        int portraitWidth = (int) Math.round(160 * story.ControllerPortrait.SRC_W
                / story.ControllerPortrait.SRC_H);
        int changedInPortrait = 0;
        for (int y = 0; y < 520; y++) {
            for (int x = 0; x < 448; x++) {
                boolean same = images[0].getRGB(x, y) == images[1].getRGB(x, y);
                boolean inPortrait = x >= LEFT && x < LEFT + portraitWidth
                        && y >= DIALOGUE_TOP && y < DIALOGUE_TOP + 160;
                if (inPortrait && !same) {
                    changedInPortrait++;
                } else if (!inPortrait) {
                    assertTrue(same, "pixel (" + x + "," + y + ") changed outside the portrait");
                }
            }
        }
        assertTrue(changedInPortrait > 20,
                "her mouth moves inside the portrait: " + changedInPortrait);
    }
}
