package entity;

import engine.Cooldown;
import engine.Core;
import engine.DrawManager.SpriteType;
import java.awt.Color;
import java.util.Set;

/**
 * Implements a ship, to be controlled by the player.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class Ship extends Entity {
    private Color baseColor = DEFAULT_COLOR;

    /** Type of this ship, which supplies its speed, fire rate and lives. */
    private final ShipType shipType;

    /** Time between shots, taken from the ship type. */
    private int shootingInterval;

    /** Baseline time between shots, before any buff changes it. */
    private int baseShootingInterval;

    /** Bullet type the ship is currently firing. */
    private BulletType currentBulletType = BulletType.NORMAL;

    /** Bullets still owed by an in-flight RAPID burst. */
    private int remainingShots = 0;

    /** Time the next bullet of a burst may be fired. */
    private long nextShotTime = 0;

    /** Speed of the bullets shot by the ship, from the bullet type. */
    private int bulletSpeed = BulletType.NORMAL.getSpeed();

    /** Movement of the ship for each unit of time, from the ship type. */
    private int speed;

    /** Baseline movement speed, before any buff changes it. */
    private int baseSpeed;

    /** Number of times the current stats have explicitly been restored. */
    private long baseStatsRestoreVersion;

    /** Minimum time between two grenade launches. */
    private static final int GRENADE_COOLDOWN = 800;
    /** Maximum number of grenades the ship can carry. */
    private static final int MAX_GRENADES = 1;

    /** Grenades left (given by the Cluster Grenade item). */
    private int grenadeCount = 0;
    /** Minimum time between two grenade launches. */
    private Cooldown grenadeCooldown;

    /** Time of the last shot. */
    private long lastShotTime = 0;

    /** Time spent inactive between hits. */
    private Cooldown destructionCooldown;
    /**
     * Time during which a hit cannot land. It outlasts
     * {@link #destructionCooldown} on purpose: the player gets the tail of it
     * back in control, which is the only chance they have to leave the spot
     * the enemies aimed at while the ship could not move.
     */
    private Cooldown invulnerabilityCooldown;

    private boolean isShieldActive = false;

    /** Whether the player-status slow debuff is affecting ship movement. */
    private boolean slowDebuffActive;
    /** Fractional slow movement retained independently for each direction. */
    private final double[] slowMovementRemainders = new double[2];

    /** Duration of the damage flashing feedback in milliseconds (0.5s). */
    private static final int DAMAGE_FEEDBACK_DURATION = 500;
    /** Duration of the protection after a hit, in milliseconds (2s). */
    private static final int INVULNERABILITY_DURATION = 2000;
    /** Time the ship cannot move after a hit, in milliseconds (1s). */
    private static final int STUN_DURATION = 1000;
    /** Rate in ms at which the ship color alternates during damage feedback. */
    private static final int FLASH_INTERVAL = 100;
    /** Default normal color of the ship. */
    private static final Color DEFAULT_COLOR = Color.GREEN;

    /** Cooldown tracking the 0.5s damage feedback duration. */
    private Cooldown damageFeedbackCooldown;
    /** Timestamp tracking when the damage effect started. */
    private long damageFeedbackStartTime;

    /**
     * Constructor, establishes the ship's properties.
     *
     * @param positionX
     *                  Initial position of the ship in the X axis.
     * @param positionY
     *                  Initial position of the ship in the Y axis.
     */
    public Ship(final int positionX, final int positionY) {
        this(positionX, positionY, ShipType.STANDARD);
    }

    /**
     * Constructor, establishes the ship's properties from a selected type.
     *
     * @param positionX
     *                  Initial position of the ship in the X axis.
     * @param positionY
     *                  Initial position of the ship in the Y axis.
     * @param shipType
     *                  Selected ship type, source of speed, fire rate and lives.
     */
    public Ship(final int positionX, final int positionY,
            final ShipType shipType) {
        super(positionX, positionY, 13 * 2, 8 * 2, Color.GREEN);

        if (shipType == null)
            throw new IllegalArgumentException("Ship type cannot be null.");

        this.shipType = shipType;
        this.baseSpeed = shipType.getSpeed();
        this.speed = this.baseSpeed;
        this.baseShootingInterval = shipType.getShootingInterval();
        this.shootingInterval = this.baseShootingInterval;
        this.currentBulletType = shipType.getBulletType();
        this.bulletSpeed = this.currentBulletType.getSpeed();
        this.spriteType = SpriteType.Ship;
        this.destructionCooldown = Core.getCooldown(STUN_DURATION);
        this.invulnerabilityCooldown =
                Core.getCooldown(INVULNERABILITY_DURATION);
        this.damageFeedbackCooldown = Core.getCooldown(DAMAGE_FEEDBACK_DURATION);
        this.grenadeCooldown = Core.getCooldown(GRENADE_COOLDOWN);
    }

    /**
     * Moves the ship speed uni ts right, or until the right screen border is
     * reached.
     */
    public final void moveRight() {
        if (this.slowDebuffActive) {
            this.positionX += getSlowedMovementPixels(0);
            return;
        }
        this.positionX += this.speed;
    }

    /**
     * Moves the ship speed units left, or until the left screen border is
     * reached.
     */
    public final void moveLeft() {
        if (this.slowDebuffActive) {
            this.positionX -= getSlowedMovementPixels(1);
            return;
        }
        this.positionX -= this.speed;
    }

    /** Enables or disables the PlayerStatus-owned slow movement modifier. */
    final void setSlowDebuffActive(final boolean active) {
        if (this.slowDebuffActive != active)
            resetSlowMovementRemainders();
        this.slowDebuffActive = active;
    }

    /** Clears fractional movement after the effective speed changes. */
    final void resetSlowMovementRemainders() {
        for (int i = 0; i < this.slowMovementRemainders.length; i++)
            this.slowMovementRemainders[i] = 0;
    }

    /**
     * Applies half of the current movement distance while retaining fractional
     * pixels per direction. Separate remainders prevent a direction change
     * from converting a fraction in one direction into movement in the other.
     *
     * @param direction Direction slot, kept separate to avoid cross-direction
     *                  carry.
     * @return Whole pixels to move in this update.
     */
    private int getSlowedMovementPixels(final int direction) {
        double movement = getMovementSpeed() * 0.5
                + this.slowMovementRemainders[direction];
        int wholePixels = (int) Math.floor(movement + 1e-9);
        this.slowMovementRemainders[direction] = movement - wholePixels;
        return wholePixels;
    }

    /**
     * Shoots a bullet upwards.
     *
     * @param bullets
     *                List of bullets on screen, to add the new bullet.
     * @return Number of bullets added to the screen.
     */
    public final int shoot(final Set<Bullet> bullets) {
        long currentTime = System.currentTimeMillis();

        if (lastShotTime != 0 && currentTime - lastShotTime < this.shootingInterval) {
            return 0;
        }

        lastShotTime = currentTime;

        int x = this.positionX + this.width / 2;
        int y = this.positionY;

        // A MULTI shot spreads sideways and owes no follow-up bullets.
        if (this.currentBulletType == BulletType.MULTI) {
            int count = 0;
            if (bullets.add(BulletPool.getBullet(x, y, this.bulletSpeed, -2,
                    Bullet.Owner.PLAYER)))
                count++;
            if (bullets.add(BulletPool.getBullet(x, y, this.bulletSpeed, 0,
                    Bullet.Owner.PLAYER)))
                count++;
            if (bullets.add(BulletPool.getBullet(x, y, this.bulletSpeed, 2,
                    Bullet.Owner.PLAYER)))
                count++;
            return count;
        }

        int added = addPlayerBullet(bullets, x, y);

        this.remainingShots = this.currentBulletType.getShotCount() - 1;
        this.nextShotTime = currentTime
                + this.currentBulletType.getShotDelay();

        return added;
    }

    /**
     * Adds one straight player bullet.
     *
     * @param bullets
     *            Bullets on screen, to add the new bullets.
     * @param x
     *            Center of the shot on the X axis.
     * @param y
     *            Starting position of the shot on the Y axis.
     * @return Number of bullets added.
     */
    private int addPlayerBullet(final Set<Bullet> bullets, final int x,
            final int y) {
        return bullets.add(BulletPool.getBullet(x, y, this.bulletSpeed,
                Bullet.Owner.PLAYER)) ? 1 : 0;
    }

    /**
     * Fires the bullets still owed by a RAPID burst, one per shot delay.
     *
     * @param bullets
     *                List of bullets on screen, to add the new bullet.
     * @return Number of bullets added to the screen.
     */
    public final int updateBurst(final Set<Bullet> bullets) {
        if (this.remainingShots <= 0 || this.isDestroyed()) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime < this.nextShotTime) {
            return 0;
        }

        int added = addPlayerBullet(bullets,
                this.positionX + this.width / 2, this.positionY);

        this.remainingShots--;
        this.nextShotTime = currentTime
                + this.currentBulletType.getShotDelay();

        return added;
    }

    /**
     * Adds grenades to the ship's stock.
     *
     * @param amount
     *            Number of grenades to add.
     */
    public final void addGrenades(final int amount) {
        this.grenadeCount = Math.min(MAX_GRENADES,
                this.grenadeCount + amount);
    }

    /**
     * Getter for the number of grenades left.
     *
     * @return Number of grenades left.
     */
    public final int getGrenadeCount() {
        return this.grenadeCount;
    }

    /**
     * Launches a grenade upwards if the ship has one and the launcher is
     * ready.
     *
     * @param grenades
     *            Set of grenades on screen, to add the new grenade.
     * @param detonationY
     *            Altitude at which the grenade explodes if it hits nothing.
     * @return True if a grenade was launched.
     */
    public final boolean launchGrenade(final Set<Grenade> grenades,
            final int detonationY) {
        if (isDestroyed()) {
            return false;
        }
        if (this.grenadeCount > 0 && this.grenadeCooldown.checkFinished()) {
            this.grenadeCooldown.reset();
            this.grenadeCount--;
            grenades.add(new Grenade(this.positionX + this.width / 2,
                    this.positionY, detonationY));
            return true;
        }
        return false;
    }

    /**
     * Updates status and visual feedback of the ship.
     */
    public final void update() {
        if (this.damageFeedbackCooldown != null
                && !this.damageFeedbackCooldown.checkFinished()) {
            // Alternate red and normal for 0.5s immediately after damage
            long elapsed = System.currentTimeMillis() - this.damageFeedbackStartTime;
            if ((elapsed / FLASH_INTERVAL) % 2 == 0) {
                setColor(Color.RED);
            } else {
                setColor(this.baseColor);
            }
        } else {
            // Restore default color once the feedback is over
            setColor(this.baseColor);
        }

        if (!this.destructionCooldown.checkFinished())
            this.spriteType = SpriteType.ShipDestroyed;
        else
            this.spriteType = SpriteType.Ship;
    }

    /**
     * Switches the ship to its destroyed state.
     */
    public final void destroy() {
        this.destructionCooldown.reset();
        this.invulnerabilityCooldown.reset();
    }

    /**
     * Checks if the ship is destroyed.
     *
     * @return True if the ship is currently destroyed.
     */
    public final boolean isDestroyed() {
        return !this.destructionCooldown.checkFinished();
    }

    /**
     * Checks if the ship is still protected by the recovery from an earlier
     * hit. The protection lasts longer than {@link #isDestroyed()}, so a ship
     * that has its controls back can still be immune for a moment.
     *
     * @return True if a hit cannot land on the ship right now.
     */
    public final boolean isInvulnerable() {
        return !this.invulnerabilityCooldown.checkFinished();
    }

    /**
     * Getter for the ship's speed.
     *
     * @return Speed of the ship.
     */
    public final int getSpeed() {
        return this.speed;
    }

    /**
     * Getter for the type this ship was created from.
     *
     * @return Selected ship type.
     */
    public final ShipType getShipType() {
        return this.shipType;
    }

    /**
     * Changes the bullet type and cancels any burst still in flight.
     *
     * @param bulletType
     *                   New bullet type.
     */
    public final void setBulletType(final BulletType bulletType) {
        if (bulletType == null)
            throw new IllegalArgumentException("Bullet type cannot be null.");
        this.currentBulletType = bulletType;
        this.bulletSpeed = bulletType.getSpeed();
        this.remainingShots = 0;
        this.nextShotTime = 0;
    }

    /**
     * Returns the bullet type the ship is firing now. It can differ from the
     * ship type's default after setBulletType().
     *
     * @return Current bullet type.
     */
    public final BulletType getCurrentBulletType() {
        return this.currentBulletType;
    }

    /** @return Time between shots in milliseconds. */
    public final int getShootingInterval() {
        return this.shootingInterval;
    }

    /** @return Baseline movement speed. */
    public final int getBaseSpeed() {
        return this.baseSpeed;
    }

    /** @return Baseline time between shots in milliseconds. */
    public final int getBaseShootingInterval() {
        return this.baseShootingInterval;
    }

    /**
     * Changes the ship's current movement speed. Zero stops movement.
     *
     * @param speed
     *              Non-negative movement distance per update.
     * @throws IllegalArgumentException
     *                                  If the speed is negative.
     */
    public final void setSpeed(final int speed) {
        if (speed < 0)
            throw new IllegalArgumentException(
                    "Ship speed must be non-negative.");
        this.speed = speed;
    }

    /**
     * Sets the baseline movement speed without changing the current speed.
     *
     * @param speed
     *              Positive movement distance per update.
     * @throws IllegalArgumentException
     *                                  If the baseline speed is not positive.
     */
    public final void setBaseSpeed(final int speed) {
        if (speed <= 0)
            throw new IllegalArgumentException(
                    "Base ship speed must be positive.");
        this.baseSpeed = speed;
    }

    /**
     * Changes the firing interval. The upstream ship measures the interval
     * from the time of the last shot, so there is no cooldown to restart.
     *
     * @param shootingInterval
     *                         Positive interval in milliseconds.
     * @throws IllegalArgumentException
     *                                  If the interval is not positive.
     */
    public final void setShootingInterval(final int shootingInterval) {
        if (shootingInterval <= 0)
            throw new IllegalArgumentException(
                    "Shooting interval must be positive (milliseconds).");
        this.shootingInterval = shootingInterval;
    }

    /**
     * Sets the baseline shooting interval without changing the current one.
     *
     * @param shootingInterval
     *                         Positive interval in milliseconds.
     * @throws IllegalArgumentException
     *                                  If the baseline interval is not positive.
     */
    public final void setBaseShootingInterval(final int shootingInterval) {
        if (shootingInterval <= 0)
            throw new IllegalArgumentException(
                    "Base shooting interval must be positive (milliseconds).");
        this.baseShootingInterval = shootingInterval;
    }

    /** Restores the current speed and shooting interval to their baselines. */
    public final void restoreBaseStats() {
        this.speed = this.baseSpeed;
        setShootingInterval(this.baseShootingInterval);
        this.baseStatsRestoreVersion++;
    }

    /** @return Version used internally to detect an explicit base-stat restore. */
    final long getBaseStatsRestoreVersion() {
        return this.baseStatsRestoreVersion;
    }

    public boolean isShieldActive() {
        return this.isShieldActive;
    }

    /**
     * Turns the Energy Shield on or off. A ship that is not shield-capable
     * ignores a request to turn it on, so any item or buff that raises the
     * shield through here follows the same rule (R1). Turning it off always
     * works.
     *
     * @param active
     *            Whether the Energy Shield should be up.
     */
    public void setShieldActive(final boolean active) {
        if (active && !this.shipType.isShieldCapable())
            return;
        this.isShieldActive = active;
    }

    /**
     * Drops the bullets a burst still owes. Used when the ship stops being
     * allowed to shoot part-way through one, so the remaining shots are
     * lost rather than fired once shooting is allowed again.
     */
    public final void cancelBurst() {
        this.remainingShots = 0;
        this.nextShotTime = 0;
    }

    public final double getMovementSpeed() {
        return this.speed;
    }

    /**
     * Triggers the 0.5-second red flashing damage feedback.
     */
    public final void triggerDamageFeedback() {
        if (this.isShieldActive) {
            return;
        }
        this.damageFeedbackStartTime = System.currentTimeMillis();
        this.damageFeedbackCooldown.reset();
    }
    public final void setBaseColor(final Color color) {
        if (color == null) {
            throw new IllegalArgumentException("Base color cannot be null.");
        }

        this.baseColor = color;
        setColor(color);
    }
}
