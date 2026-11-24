package screen;

import audio.AudioManager;
import engine.Core;
import engine.CustomLevelMenu.Action;
import engine.InputManager;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPointerNavigationTest {
    private final Canvas source = new Canvas();
    private final InputManager input = Core.getInputManager();
    private boolean previousMute;

    @BeforeEach
    void prepare() {
        previousMute = AudioManager.isMuted();
        AudioManager.setMuted(true);
        input.releaseAllInputs();
        move(-1, -1);
    }

    @AfterEach
    void cleanup() {
        input.releaseAllInputs();
        move(-1, -1);
        AudioManager.setMuted(previousMute);
    }

    private <T extends MenuScreen> T open(T screen) {
        screen.inputDelay = Core.getCooldown(0);
        screen.isRunning = true;
        screen.update();
        return screen;
    }

    private void move(int x, int y) {
        input.mouseMoved(new MouseEvent(source, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    private void press(int x, int y, int button) {
        input.mousePressed(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, button));
    }

    private void release(int x, int y, int button) {
        input.mouseReleased(new MouseEvent(source, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, button));
    }

    private void click(int x, int y) {
        press(x, y, MouseEvent.BUTTON1);
        release(x, y, MouseEvent.BUTTON1);
    }

    @Test
    void quickClickActivatesItsOriginalTargetEvenAfterPointerMoves() {
        GameModeScreen mode = open(new GameModeScreen(448, 520, 60));
        click(224, 348); // Custom Level
        move(224, 228); // Endless
        mode.update();
        assertFalse(mode.isRunning);
        assertEquals(16, mode.returnCode);
    }

    @Test
    void hoverThenUseKeyboardWithoutThePointerTakingFocusBack() throws Exception {
        CustomLevelScreen hub = open(new CustomLevelScreen(448, 520, 60, 1));
        move(224, 307);
        hub.update();
        assertEquals(Action.EXPORT, hub.getAction());
        var cooldown = CustomLevelScreen.class.getDeclaredField("cooldown");
        cooldown.setAccessible(true);
        ((engine.Cooldown) cooldown.get(hub)).setDuration(0);
        input.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DOWN, ' '));
        hub.update();
        input.keyReleased(new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_DOWN, ' '));
        hub.update();
        assertEquals(Action.BACK, hub.getAction());
    }

    @Test
    void emptyLibraryAndBlankSpaceCannotOpenAFileAction() {
        CustomLevelScreen hub = open(new CustomLevelScreen(448, 520, 60, 0));
        for (int y : new int[] {262, 305, 390}) {
            click(224, y);
            hub.update();
            assertTrue(hub.isRunning);
            assertEquals(Action.IMPORT, hub.getAction());
        }
        click(224, 349);
        hub.update();
        assertFalse(hub.isRunning);
        assertEquals(Action.BACK, hub.getAction());
    }

    @Test
    void aHeldPressCannotChooseTwoScreensAndAFreshPressCanChoosePlayerTwo() {
        GameModeScreen mode = open(new GameModeScreen(448, 520, 60));
        press(224, 229, MouseEvent.BUTTON1);
        mode.update();
        mode.update();
        assertTrue(mode.isRunning);
        release(224, 229, MouseEvent.BUTTON1);
        click(224, 269);
        mode.update();
        assertFalse(mode.isRunning);
        assertEquals(8, mode.returnCode);
        assertEquals(2, mode.getPlayerCount());
    }

    @Test
    void rightClickGoesBackExactlyOneStep() {
        GameModeScreen mode = open(new GameModeScreen(448, 520, 60));
        click(224, 269); // Campaign
        mode.update();
        press(5, 5, MouseEvent.BUTTON3);
        mode.update();
        mode.update();
        assertTrue(mode.isRunning);
        assertEquals(1, mode.getSelectedGameMode());
        release(5, 5, MouseEvent.BUTTON3);
        press(5, 5, MouseEvent.BUTTON3);
        release(5, 5, MouseEvent.BUTTON3);
        mode.update();
        assertFalse(mode.isRunning);
    }
}
