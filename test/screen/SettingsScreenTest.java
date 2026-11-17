package screen;

import audio.AudioManager;
import engine.Core;
import engine.DrawManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the Settings menu: Audio, Controls, Color and Back. */
class SettingsScreenTest {

    /** Return codes the Core loop maps to the settings screens. */
    private static final int AUDIO = 5;
    private static final int CONTROLS = 6;
    private static final int COLOR = 7;
    private static final int MAIN_MENU = 1;

    private final Canvas inputSource = new Canvas();
    private SettingsScreen screen;
    private boolean previousMute;

    @BeforeEach
    void setUp() {
        previousMute = AudioManager.isMuted();
        AudioManager.setMuted(true);
        releaseInput();
        openScreen();
    }

    @AfterEach
    void cleanUp() {
        releaseInput();
        AudioManager.setMuted(previousMute);
    }

    @Test
    void menuListsAudioControlsColorAndBackWithoutGameplay() throws Exception {
        Field options = DrawManager.class.getDeclaredField("SETTINGS_MENU_OPTIONS");
        options.setAccessible(true);
        assertArrayEquals(new String[] {"Audio", "Controls", "Color", "Back"},
                (String[]) options.get(null));
    }

    @Test
    void firstOptionOpensTheAudioSettings() throws Exception {
        tap(KeyEvent.VK_SPACE);
        assertFinishedWith(AUDIO);
    }

    @Test
    void secondOptionOpensTheControlSettings() throws Exception {
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_SPACE);
        assertFinishedWith(CONTROLS);
    }

    @Test
    void thirdOptionOpensTheShipColorScreen() throws Exception {
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_SPACE);
        assertFinishedWith(COLOR);
    }

    @Test
    void fourthOptionGoesBackToTheMainMenu() throws Exception {
        for (int i = 0; i < 3; i++) {
            tap(KeyEvent.VK_DOWN);
        }
        tap(KeyEvent.VK_SPACE);
        assertFinishedWith(MAIN_MENU);
    }

    @Test
    void selectionWrapsAroundInBothDirections() throws Exception {
        // Down from Back (the fourth option) wraps to Audio.
        for (int i = 0; i < 4; i++) {
            tap(KeyEvent.VK_DOWN);
        }
        tap(KeyEvent.VK_SPACE);
        assertFinishedWith(AUDIO);

        // Up from Audio wraps to Back.
        openScreen();
        tap(KeyEvent.VK_UP);
        tap(KeyEvent.VK_SPACE);
        assertFinishedWith(MAIN_MENU);
    }

    @Test
    void wasdKeysMoveTheSelectionToo() throws Exception {
        tap(KeyEvent.VK_S);
        tap(KeyEvent.VK_S);
        tap(KeyEvent.VK_SPACE);
        assertFinishedWith(COLOR);
    }

    @Test
    void escapeReturnsToTheMainMenu() throws Exception {
        tap(KeyEvent.VK_ESCAPE);
        assertFinishedWith(MAIN_MENU);
    }

    @Test
    void colorScreenReturnsToTheSettingsMenu() {
        // Core maps return code 4 to this menu, so leaving the color screen lands here.
        assertEquals(4, ShipColorScreen.RETURN_TO_SETTINGS);
    }

    @Test
    void noOptionLeadsToTheRemovedGameplaySettings() throws Exception {
        // The old Gameplay option returned 7 and was the third entry; now that code is the
        // ship color screen, and the menu only has four entries.
        int[] seen = new int[4];
        for (int option = 0; option < seen.length; option++) {
            openScreen();
            for (int i = 0; i < option; i++) {
                tap(KeyEvent.VK_DOWN);
            }
            tap(KeyEvent.VK_SPACE);
            seen[option] = screen.returnCode;
            assertFalse(screen.isRunning);
        }
        assertArrayEquals(new int[] {AUDIO, CONTROLS, COLOR, MAIN_MENU}, seen);
    }

    private void assertFinishedWith(final int code) {
        assertFalse(screen.isRunning, "The screen should have closed");
        assertEquals(code, screen.returnCode);
    }

    private void openScreen() {
        screen = new SettingsScreen(448, 520, 60);
        screen.inputDelay = Core.getCooldown(0);
        screen.isRunning = true;
        screen.update();
        assertTrue(screen.isRunning);
    }

    private void tap(final int code) throws Exception {
        Field field = SettingsScreen.class.getDeclaredField("selectionCooldown");
        field.setAccessible(true);
        Object cooldown = field.get(screen);
        Field time = cooldown.getClass().getDeclaredField("time");
        time.setAccessible(true);
        time.setLong(cooldown, 0);
        key(code, true);
        screen.update();
        key(code, false);
        screen.update();
    }

    private void key(final int code, final boolean pressed) {
        KeyEvent event = new KeyEvent(inputSource,
                pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (pressed) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    private void mouse(final int id, final int x, final int y) {
        MouseEvent event = new MouseEvent(inputSource, id, 0, 0, x, y, 1, false,
                MouseEvent.BUTTON1);
        if (id == MouseEvent.MOUSE_MOVED) Core.getInputManager().mouseMoved(event);
        else Core.getInputManager().mouseReleased(event);
    }

    private void releaseInput() {
        for (int code = 0; code < 256; code++) key(code, false);
        mouse(MouseEvent.MOUSE_RELEASED, -1, -1);
        mouse(MouseEvent.MOUSE_MOVED, -1, -1);
        Core.getInputManager().clearLastPressedKey();
    }
}
