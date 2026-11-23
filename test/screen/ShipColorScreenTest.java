package screen;

import audio.AudioManager;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import entity.Ship;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipColorScreenTest {
    private final Canvas inputSource = new Canvas();
    private ShipColorScreen screen;
    private String previousSelection;
    private float previousHue;
    private String previousSecondPlayerSelection;
    private float previousSecondPlayerHue;
    private boolean previousMute;

    @BeforeEach
    void setUp() {
        previousSelection = ShipCustomization.getColorName();
        previousHue = ShipCustomization.getCustomHue();
        previousSecondPlayerSelection = ShipCustomization.getColorName(1);
        previousSecondPlayerHue = ShipCustomization.getCustomHue(1);
        previousMute = AudioManager.isMuted();
        AudioManager.setMuted(true);
        releaseInput();
        select(0, "Green");
        select(1, "Blue");
        ShipCustomization.setCustomHue(0, 0f);
        ShipCustomization.setCustomHue(1, 0f);
        openScreen();
    }

    @AfterEach
    void cleanUp() {
        releaseInput();
        select(0, previousSelection);
        select(1, previousSecondPlayerSelection);
        ShipCustomization.setCustomHue(0, previousHue);
        ShipCustomization.setCustomHue(1, previousSecondPlayerHue);
        AudioManager.setMuted(previousMute);
    }

    @Test
    void presetsKeepTheirOrderAndWrapThroughCustom() throws Exception {
        String[] names = {"Cyan", "Magenta", "Yellow", "Orange", "Blue", "Custom", "Green"};
        Color[] colors = {Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.ORANGE,
            Color.BLUE, Color.RED, Color.GREEN};
        for (int i = 0; i < names.length; i++) {
            tap(KeyEvent.VK_RIGHT);
            assertEquals(names[i], ShipCustomization.getColorName());
            assertEquals(colors[i], ShipCustomization.getColor());
        }
        tap(KeyEvent.VK_LEFT);
        assertTrue(ShipCustomization.isCustomColor());
    }

    @Test
    void playerColorKeepsPlayerOneSelectionAndDefaultsPlayerTwoToBlue() {
        ShipCustomization.changeColor(1);
        assertEquals(ShipCustomization.getColor(),
                ShipCustomization.getColor(0));
        assertEquals(Color.BLUE, ShipCustomization.getColor(1));
        assertThrows(IllegalArgumentException.class,
                () -> ShipCustomization.getColor(2));
    }

    @Test
    void colorPreviewPlacesPlayerOneLeftOfBluePlayerTwo() throws Exception {
        Field firstField = ShipColorScreen.class.getDeclaredField("previewShip");
        Field secondField = ShipColorScreen.class
                .getDeclaredField("secondPlayerPreviewShip");
        firstField.setAccessible(true);
        secondField.setAccessible(true);

        Ship firstPlayerShip = (Ship) firstField.get(screen);
        Ship secondPlayerShip = (Ship) secondField.get(screen);

        assertTrue(firstPlayerShip.getPositionX()
                < secondPlayerShip.getPositionX());
        assertEquals(ShipCustomization.getColor(), firstPlayerShip.getColor());
        assertEquals(Color.BLUE, secondPlayerShip.getColor());
    }

    @Test
    void upDownChoosePlayerAndLeftRightChangesOnlyThatPlayersColor()
            throws Exception {
        tap(KeyEvent.VK_DOWN);
        assertEquals(1, selectedPlayerIndex());
        tap(KeyEvent.VK_RIGHT);
        assertEquals("Custom", ShipCustomization.getColorName(1));
        assertEquals("Green", ShipCustomization.getColorName(0));

        tap(KeyEvent.VK_ENTER);
        tap(KeyEvent.VK_RIGHT);
        assertTrue(ShipCustomization.getCustomHue(1) > 0f);
        assertEquals(0f, ShipCustomization.getCustomHue(0));

        tap(KeyEvent.VK_UP);
        assertEquals(0, selectedPlayerIndex());
        tap(KeyEvent.VK_RIGHT);
        assertEquals("Cyan", ShipCustomization.getColorName(0));
        assertEquals("Custom", ShipCustomization.getColorName(1));
    }

    @Test
    void keyboardEditingChangesHueWithoutCyclingPresets() throws Exception {
        tap(KeyEvent.VK_LEFT);
        tap(KeyEvent.VK_ENTER);
        tap(KeyEvent.VK_RIGHT);
        float hue = ShipCustomization.getCustomHue();
        assertTrue(hue > 0f);
        assertTrue(ShipCustomization.isCustomColor());
        tap(KeyEvent.VK_D);
        assertTrue(ShipCustomization.getCustomHue() > hue);
        tap(KeyEvent.VK_A);
        assertEquals(hue, ShipCustomization.getCustomHue(), 0.00001f);
        tap(KeyEvent.VK_ENTER);
        tap(KeyEvent.VK_RIGHT);
        assertEquals("Green", ShipCustomization.getColorName());
        tap(KeyEvent.VK_LEFT);
        assertEquals(hue, ShipCustomization.getCustomHue());
    }

    @Test
    void heldConfirmDoesNotRepeatedlyToggleEditing() throws Exception {
        tap(KeyEvent.VK_LEFT);
        key(KeyEvent.VK_ENTER, true);
        screen.update();
        screen.update();
        key(KeyEvent.VK_ENTER, false);
        screen.update();
        tap(KeyEvent.VK_D);
        assertTrue(ShipCustomization.isCustomColor());
        assertTrue(ShipCustomization.getCustomHue() > 0f);
    }

    @Test
    void draggingPicksHueClampsAtEndsAndStopsOnRelease() throws Exception {
        tap(KeyEvent.VK_LEFT);
        mouse(MouseEvent.MOUSE_MOVED, 224, 297);
        mouse(MouseEvent.MOUSE_PRESSED, 224, 297);
        screen.update();
        assertEquals(120f / 239f, ShipCustomization.getCustomHue(), 0.00001f);
        mouse(MouseEvent.MOUSE_DRAGGED, 400, 297);
        screen.update();
        assertEquals(1f, ShipCustomization.getCustomHue());
        mouse(MouseEvent.MOUSE_DRAGGED, 40, 297);
        screen.update();
        assertEquals(0f, ShipCustomization.getCustomHue());
        mouse(MouseEvent.MOUSE_RELEASED, 40, 297);
        screen.update();
        mouse(MouseEvent.MOUSE_MOVED, 224, 297);
        screen.update();
        assertEquals(0f, ShipCustomization.getCustomHue());
    }

    @Test
    void clickHeldWhenScreenOpensDoesNotStartDragging() {
        select("Custom");
        mouse(MouseEvent.MOUSE_MOVED, 224, 297);
        mouse(MouseEvent.MOUSE_PRESSED, 224, 297);
        openScreen();
        assertEquals(0f, ShipCustomization.getCustomHue());
        mouse(MouseEvent.MOUSE_DRAGGED, 300, 297);
        screen.update();
        assertEquals(0f, ShipCustomization.getCustomHue());
    }

    @Test
    void pressingOutsideTheBarThenDraggingInDoesNotChangeColor() throws Exception {
        tap(KeyEvent.VK_LEFT);
        mouse(MouseEvent.MOUSE_MOVED, 40, 297);
        mouse(MouseEvent.MOUSE_PRESSED, 40, 297);
        screen.update();
        mouse(MouseEvent.MOUSE_DRAGGED, 224, 297);
        screen.update();
        assertEquals(0f, ShipCustomization.getCustomHue());
    }

    @Test
    void customColorSurvivesEscapeReentryAndGameplayUpdate() throws Exception {
        select("Custom");
        ShipCustomization.setCustomHue(0.58f);
        Color chosen = ShipCustomization.getColor();
        tap(KeyEvent.VK_ESCAPE);
        assertFalse(screen.isRunning);
        assertEquals(ShipColorScreen.RETURN_TO_SETTINGS, screen.returnCode);
        openScreen();
        Field preview = ShipColorScreen.class.getDeclaredField("previewShip");
        preview.setAccessible(true);
        assertEquals(chosen, ((Ship) preview.get(screen)).getColor());

        GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
                new GameSettings(5, 4, 60, 2000), false, 448, 520, 60);
        game.initialize();
        Field shipField = GameScreen.class.getDeclaredField("ship");
        shipField.setAccessible(true);
        Ship ship = (Ship) shipField.get(game);
        assertEquals(chosen, ship.getColor());
        ship.update();
        assertEquals(chosen, ship.getColor());
    }

    @Test
    void keyboardAdjustmentStopsAtSpectrumEndpoints() throws Exception {
        tap(KeyEvent.VK_LEFT);
        tap(KeyEvent.VK_SPACE);
        tap(KeyEvent.VK_LEFT);
        assertEquals(0f, ShipCustomization.getCustomHue());
        ShipCustomization.setCustomHue(1f);
        tap(KeyEvent.VK_RIGHT);
        assertEquals(1f, ShipCustomization.getCustomHue());
    }

    @Test
    void invalidHueDoesNotReplaceTheCurrentColor() {
        select("Custom");
        ShipCustomization.setCustomHue(0.3f);
        for (float hue : new float[] {-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> ShipCustomization.setCustomHue(hue));
            assertEquals(0.3f, ShipCustomization.getCustomHue());
        }
    }

    private void openScreen() {
        screen = new ShipColorScreen(448, 520, 60);
        screen.inputDelay = Core.getCooldown(0);
        screen.isRunning = true;
        screen.update();
    }

    private static void select(String name) {
        select(0, name);
    }

    private static void select(final int playerIndex, final String name) {
        for (int i = 0; i < 7; i++) {
            if (name.equals(ShipCustomization.getColorName(playerIndex))) return;
            ShipCustomization.changeColor(playerIndex, 1);
        }
        fail("Unknown color: " + name);
    }

    private int selectedPlayerIndex() throws Exception {
        Field field = ShipColorScreen.class
                .getDeclaredField("selectedPlayerIndex");
        field.setAccessible(true);
        return field.getInt(screen);
    }

    private void tap(int code) throws Exception {
        Field field = ShipColorScreen.class.getDeclaredField("selectionCooldown");
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

    private void key(int code, boolean pressed) {
        KeyEvent event = new KeyEvent(inputSource,
                pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (pressed) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    private void mouse(int id, int x, int y) {
        MouseEvent event = new MouseEvent(inputSource, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1);
        if (id == MouseEvent.MOUSE_MOVED) Core.getInputManager().mouseMoved(event);
        else if (id == MouseEvent.MOUSE_DRAGGED) Core.getInputManager().mouseDragged(event);
        else if (id == MouseEvent.MOUSE_PRESSED) Core.getInputManager().mousePressed(event);
        else Core.getInputManager().mouseReleased(event);
    }

    private void releaseInput() {
        for (int code = 0; code < 256; code++) key(code, false);
        mouse(MouseEvent.MOUSE_RELEASED, -1, -1);
        mouse(MouseEvent.MOUSE_MOVED, -1, -1);
        Core.getInputManager().clearLastPressedKey();
    }
}
