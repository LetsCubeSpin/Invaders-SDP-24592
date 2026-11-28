package screen;

import audio.AudioManager;
import engine.Core;
import engine.LanguageManager;
import engine.LanguageManager.Language;
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

/** Checks the Settings menu and its language selection. */
class SettingsScreenTest {

    /** Return codes the Core loop maps to the settings screens. */
    private static final int AUDIO = 5;
    private static final int CONTROLS = 6;
    private static final int COLOR = 7;
    private static final int MAIN_MENU = 1;

    private final Canvas inputSource = new Canvas();
    private SettingsScreen screen;
    private boolean previousMute;
    private Language previousLanguage;

    @BeforeEach
    void setUp() {
        previousMute = AudioManager.isMuted();
        previousLanguage = LanguageManager.getCurrentLanguage();
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        AudioManager.setMuted(true);
        releaseInput();
        openScreen();
    }

    @AfterEach
    void cleanUp() {
        releaseInput();
        AudioManager.setMuted(previousMute);
        LanguageManager.setCurrentLanguage(previousLanguage);
    }

    @Test
    void languageOptionTogglesLanguageWithoutLeavingSettings() throws Exception {
        for (int i = 0; i < 3; i++) {
            tap(KeyEvent.VK_DOWN);
        }
        tap(KeyEvent.VK_SPACE);
        assertTrue(screen.isRunning);
        assertEquals(Language.KOREAN, LanguageManager.getCurrentLanguage());
        assertEquals("설정", LanguageManager.get("settings.title"));

        tap(KeyEvent.VK_SPACE);
        assertTrue(screen.isRunning);
        assertEquals(Language.ENGLISH, LanguageManager.getCurrentLanguage());
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
    void fifthOptionGoesBackToTheMainMenu() throws Exception {
        for (int i = 0; i < 4; i++) {
            tap(KeyEvent.VK_DOWN);
        }
        tap(KeyEvent.VK_SPACE);
        assertFinishedWith(MAIN_MENU);
    }

    @Test
    void selectionWrapsAroundInBothDirections() throws Exception {
        // Down from Back (the fifth option) wraps to Audio.
        for (int i = 0; i < 5; i++) {
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
    void nonLanguageOptionsOpenTheirExpectedScreensOrReturnToMenu() throws Exception {
        int[] options = {0, 1, 2, 4};
        int[] seen = new int[options.length];
        for (int i = 0; i < options.length; i++) {
            openScreen();
            for (int j = 0; j < options[i]; j++) {
                tap(KeyEvent.VK_DOWN);
            }
            tap(KeyEvent.VK_SPACE);
            seen[i] = screen.returnCode;
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
