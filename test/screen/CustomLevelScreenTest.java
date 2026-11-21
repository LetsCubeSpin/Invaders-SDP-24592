package screen;

import engine.Core;
import engine.CustomLevelMenu.Action;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual keyboard events and update(), including disabled-row navigation. */
class CustomLevelScreenTest {
    private final Canvas input = new Canvas();
    private CustomLevelScreen screen;

    @BeforeEach
    void setup() { Core.getInputManager().releaseAllInputs(); }

    @AfterEach
    void cleanup() { Core.getInputManager().releaseAllInputs(); }

    private void open(boolean loaded) {
        screen = new CustomLevelScreen(448, 520, 60, loaded ? 2 : 0);
        screen.inputDelay = Core.getCooldown(0);
        screen.isRunning = true;
    }

    private void tap(int code) throws Exception {
        Field field = CustomLevelScreen.class.getDeclaredField("cooldown");
        field.setAccessible(true);
        Object cooldown = field.get(screen);
        Field time = cooldown.getClass().getDeclaredField("time");
        time.setAccessible(true);
        time.setLong(cooldown, 0);
        Core.getInputManager().keyPressed(new KeyEvent(input, KeyEvent.KEY_PRESSED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED));
        screen.update();
        Core.getInputManager().keyReleased(new KeyEvent(input, KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void emptyLibrarySkipsBothDisabledRowsInBothDirections() throws Exception {
        open(false);
        assertEquals(Action.IMPORT, screen.getAction());
        tap(KeyEvent.VK_DOWN);
        assertEquals(Action.BACK, screen.getAction());
        tap(KeyEvent.VK_DOWN);
        assertEquals(Action.IMPORT, screen.getAction());
        tap(KeyEvent.VK_UP);
        assertEquals(Action.BACK, screen.getAction());
        tap(KeyEvent.VK_UP);
        assertEquals(Action.IMPORT, screen.getAction());
    }

    @Test
    void wasdSkipsDisabledRowsAndSpaceActivatesTheVisibleSelection() throws Exception {
        open(false);
        tap(KeyEvent.VK_S);
        assertEquals(Action.BACK, screen.getAction());
        tap(KeyEvent.VK_W);
        assertEquals(Action.IMPORT, screen.getAction());
        tap(KeyEvent.VK_S);
        tap(KeyEvent.VK_SPACE);
        assertFalse(screen.isRunning);
        assertEquals(Action.BACK, screen.getAction());
    }

    @Test
    void loadedLibraryVisitsAllEnabledRowsAndWraps() throws Exception {
        open(true);
        for (Action action : new Action[] {Action.PLAY, Action.EXPORT, Action.BACK, Action.IMPORT}) {
            tap(KeyEvent.VK_DOWN);
            assertEquals(action, screen.getAction());
        }
        tap(KeyEvent.VK_UP);
        assertEquals(Action.BACK, screen.getAction());
    }
}
