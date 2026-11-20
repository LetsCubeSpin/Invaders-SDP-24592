package entity;

import engine.LanguageManager;

/**
 * Defines the available player ship types and their gameplay attributes.
 */
public enum ShipType {

    STANDARD("standard", 2, 750, 4, false),
    SPEEDSTER("speedster", 4, 400, 3, false),
    TANK("tank", 1, 1000, 5, true);

    /** Translation key suffix for the ship name. */
    private final String nameKey;

    /** Horizontal movement speed. */
    private final int speed;

    /** Minimum time between shots in milliseconds. */
    private final int shootingInterval;

    /** Maximum number of lives. */
    private final int maxLives;

    /** Whether this ship supports a shield. */
    private final boolean shieldCapable;

    ShipType(final String nameKey,
            final int speed,
            final int shootingInterval,
            final int maxLives,
            final boolean shieldCapable) {

        this.nameKey = nameKey;
        this.speed = speed;
        this.shootingInterval = shootingInterval;
        this.maxLives = maxLives;
        this.shieldCapable = shieldCapable;
    }

    public String getName() {
        return LanguageManager.get("ship." + this.nameKey);
    }

    public int getSpeed() {
        return this.speed;
    }

    public int getShootCooldown() {
        return this.shootingInterval;
    }

    public int getShootingInterval() {
        return this.shootingInterval;
    }

    public int getMaxLives() {
        return this.maxLives;
    }

    public boolean hasShield() {
        return this.shieldCapable;
    }

    public boolean isShieldCapable() {
        return this.shieldCapable;
    }

    public BulletType getBulletType() {
        switch (this) {
            case SPEEDSTER:
                return BulletType.RAPID;

            case TANK:
                return BulletType.MULTI;

            case STANDARD:
            default:
                return BulletType.NORMAL;
        }
    }

}
