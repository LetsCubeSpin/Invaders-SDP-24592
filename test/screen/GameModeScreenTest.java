package screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameModeScreenTest {

    @Test
    void modeStepHasStoryModeBeforeBack() {
        assertEquals(4, GameModeScreen.choiceCount(0));
        assertEquals(2, GameModeScreen.STORY_MODE);
        assertEquals(3, GameModeScreen.MODE_BACK);
    }

    @Test
    void playerStepKeepsItsThreeChoices() {
        assertEquals(3, GameModeScreen.choiceCount(1));
    }

    @Test
    void eachModeStartsItsGame() {
        assertEquals(8, GameModeScreen.returnCodeFor(0));
        assertEquals(2, GameModeScreen.returnCodeFor(1));
        assertEquals(15, GameModeScreen.returnCodeFor(GameModeScreen.STORY_MODE));
    }

    @Test
    void backIsNotAGameMode() {
        assertThrows(IllegalArgumentException.class,
                () -> GameModeScreen.returnCodeFor(GameModeScreen.MODE_BACK));
        assertThrows(IllegalArgumentException.class,
                () -> GameModeScreen.returnCodeFor(-1));
    }
}
