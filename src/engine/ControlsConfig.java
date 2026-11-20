package engine;

import java.awt.event.KeyEvent;
import java.util.prefs.Preferences;

public final class ControlsConfig {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int FIRE = 2;
    public static final int BUFF = 3;
    /** Action used to launch a grenade in Campaign mode. */
    public static final int GRENADE = 4;

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(ControlsConfig.class);

    private ControlsConfig() {
    }

    public static int getKey(final int action) {
        switch (action) {
        case LEFT:
            return PREFS.getInt("left", KeyEvent.VK_LEFT);
        case RIGHT:
            return PREFS.getInt("right", KeyEvent.VK_RIGHT);
        case FIRE:
            return PREFS.getInt("fire", KeyEvent.VK_SPACE);
        default:
            return KeyEvent.VK_UNDEFINED;
        }
    }

    /**
     * Resolves an action to the key assigned to one player.
     *
     * <p>Player 1 keeps the existing configurable bindings so this addition
     * does not change one-player controls. Player 2 has a separate fixed
     * layout: A/D to move, W to fire, and E to launch a Campaign grenade.
     *
     * @param playerIndex zero-based player number (0 for Player 1, 1 for Player 2)
     * @param action one of the movement, fire, or grenade action constants
     * @return the AWT key code assigned to the action, or
     *         {@link KeyEvent#VK_UNDEFINED} when the action is unsupported
     * @throws IllegalArgumentException if the player index is not 0 or 1
     */
    public static int getPlayerKey(final int playerIndex, final int action) {
        // Keep Player 1's remappable controls and existing G grenade key intact.
        if (playerIndex == 0) {
            if (action == GRENADE)
                return KeyEvent.VK_G;
            return getKey(action);
        }
        if (playerIndex != 1)
            throw new IllegalArgumentException("Player index must be 0 or 1.");

        // Player 2's bindings are separate from Player 1's saved preferences.
        switch (action) {
        case LEFT:
            return KeyEvent.VK_A;
        case RIGHT:
            return KeyEvent.VK_D;
        case FIRE:
            return KeyEvent.VK_W;
        case GRENADE:
            return KeyEvent.VK_E;
        default:
            return KeyEvent.VK_UNDEFINED;
        }
    }

    public static void setKey(final int action, final int keyCode) {
        switch (action) {
        case LEFT:
            PREFS.putInt("left", keyCode);
            break;
        case RIGHT:
            PREFS.putInt("right", keyCode);
            break;
        case FIRE:
            PREFS.putInt("fire", keyCode);
            break;
        default:
            break;
        }
    }
}
