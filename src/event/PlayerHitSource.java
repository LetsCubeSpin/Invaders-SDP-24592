package event;

/**
 * What hit the player, carried by {@link PlayerHitEvent}.
 */
public enum PlayerHitSource {
    /** Bullet fired by a normal enemy. */
    ENEMY_BULLET,
    /** Bullet fired by a boss. */
    BOSS_BULLET,
    /** The player ship crashed into an enemy. */
    ENEMY_COLLISION,
    /** No source was supplied by the caller. */
    UNKNOWN
}
