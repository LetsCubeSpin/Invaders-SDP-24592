package screen;

import entity.PlayerStatus;
import entity.Ship;
import engine.ControlsConfig;

/** Keeps one player's ship and personal status together within a game screen. */
final class PlayerSlot {
    /** Zero-based player number used to resolve this slot's input bindings. */
    private final int playerIndex;
    private Ship ship;
    private PlayerStatus status;
    /** Tracks key-down state so holding the grenade key launches only once. */
    private boolean grenadeKeyPressed;

    PlayerSlot(final int playerIndex, final Ship ship,
            final PlayerStatus status) {
        this.playerIndex = playerIndex;
        this.ship = ship;
        this.status = status;
    }

    /**
     * Gets this player's key binding for a game action; the control layout is
     * chosen by the slot, so screens do not accidentally read Player 1's keys.
     */
    int getKey(final int action) {
        return ControlsConfig.getPlayerKey(this.playerIndex, action);
    }

    /** Whether the grenade key was down on the preceding control update. */
    boolean isGrenadeKeyPressed() {
        return this.grenadeKeyPressed;
    }

    /** Records the current grenade-key state to detect a new press next frame. */
    void setGrenadeKeyPressed(final boolean pressed) {
        this.grenadeKeyPressed = pressed;
    }

    Ship getShip() {
        return this.ship;
    }

    PlayerStatus getStatus() {
        return this.status;
    }

    boolean isAlive() {
        return this.status.isAlive();
    }

    /**
     * Replaces the ship and personal status after a shared-life respawn while
     * keeping the slot's player number and key bindings unchanged.
     */
    void respawn(final Ship ship, final PlayerStatus status) {
        this.ship = ship;
        this.status = status;
    }
}
