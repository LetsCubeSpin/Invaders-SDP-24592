package screen;

import engine.GameSettings;
import engine.GameState;
import entity.Grenade;
import entity.Ship;
import entity.ShipType;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks Player 2's Campaign bindings and confirms they affect only P2. */
class PlayerTwoControlsTest {
    private static final Canvas KEY_SOURCE = new Canvas();

    @Test
    void campaignPlayerTwoCanMoveFireAndLaunchGrenades() throws Exception {
        GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0),
                Collections.singletonList(new GameSettings(1, 1, 60, 2000)),
                false, 448, 520, 60, ShipType.STANDARD, null,
                2);
        game.initialize();

        Ship secondPlayer = game.getPlayerShip(1);
        int initialX = secondPlayer.getPositionX();
        press(game, KeyEvent.VK_A);
        assertEquals(initialX - secondPlayer.getSpeed(),
                secondPlayer.getPositionX(), "A moves Player 2 left");
        press(game, KeyEvent.VK_D);
        assertEquals(initialX, secondPlayer.getPositionX(),
                "D moves Player 2 right");

        press(game, KeyEvent.VK_W);
        assertEquals(1, game.getGameState().getBulletsShot(),
                "W fires for Player 2");

        secondPlayer.addGrenades(1);
        press(game, KeyEvent.VK_E);
        @SuppressWarnings("unchecked")
        Set<Grenade> launched = (Set<Grenade>) getField(game, "grenades");
        assertEquals(1, launched.size(), "E launches Player 2's grenade");
        assertEquals(0, secondPlayer.getGrenadeCount(),
                "Launching spends Player 2's grenade");
    }

    private static void press(final GameScreen game, final int keyCode)
            throws Exception {
        key(game, keyCode, true);
        try {
            Method updateControls = GameScreen.class
                    .getDeclaredMethod("updateSecondaryPlayerControls");
            updateControls.setAccessible(true);
            updateControls.invoke(game);
        } finally {
            key(game, keyCode, false);
        }
    }

    private static void key(final GameScreen game, final int keyCode,
            final boolean pressed) {
        KeyEvent event = new KeyEvent(KEY_SOURCE,
                pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
        if (pressed)
            game.inputManager.keyPressed(event);
        else
            game.inputManager.keyReleased(event);
    }

    private static Object getField(final GameScreen game, final String name)
            throws Exception {
        Field field = GameScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(game);
    }
}
