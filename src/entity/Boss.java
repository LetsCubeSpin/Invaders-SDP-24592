package entity;

import java.awt.Color;

import engine.DrawManager.SpriteType;
import engine.GameColors;
import engine.StoryDebugModifiers;

/**
 * Implements the boss, a strong enemy that moves left and right
 * and changes phase as its health goes down.
 *
 * Boss Developer 1: structure, health, damage, state, movement, phase, defeat.
 * Boss Developer 2: uses the getters below for attacks and events.
 */
public class Boss extends Entity {

    /** Width of the boss in pixels. */
    public static final int WIDTH = 16 * 2;
    /** Height of the boss in pixels. */
    public static final int HEIGHT = 7 * 2;

    /** States the boss can be in. */
    public enum State {
        IDLE, MOVING, ATTACKING, HIT, DEFEATED
    }

    /** Kinds of bosses and their base values. */
    public enum BossType {
        /** Slow boss. */
        BOSS_A(850, 1, 0.5, 0.25, 500, GameColors.BOSS_A),
        /** Fast boss, gets stronger earlier. */
        BOSS_B(800, 3, 0.7, 0.4, 800, GameColors.BOSS_B),
        /** VESPERA, summons minions **/
        BOSS_C(800, 3, 0.7, 0.4, 1000, GameColors.BOSS_C),
		/** OBELISK, periodic shield in phase 2 and laser beam in phase 3. */
		BOSS_D(1000, 2, 0.7, 0.4, 1500, GameColors.BOSS_D);
        /** Maximum health of this kind of boss. */
        private final int maxHealth;
        /** Base horizontal speed in pixels per frame. */
        private final int speed;
        /** Phase 2 starts below this share of max health. */
        private final double phase2Threshold;
        /** Phase 3 starts below this share of max health. */
        private final double phase3Threshold;
        /** Score given to the player for defeating this boss (temporary). */
        private final int pointValue;
        /** Base color shared by the sprite and health bar. */
        private final Color color;

        BossType(final int maxHealth, final int speed,
                 final double phase2Threshold, final double phase3Threshold,
                 final int pointValue, final Color color) {
            this.maxHealth = maxHealth;
            this.speed = speed;
            this.phase2Threshold = phase2Threshold;
            this.phase3Threshold = phase3Threshold;
            this.pointValue = pointValue;
            this.color = color;
        }

        public int getMaxHealth() {
            return this.maxHealth;
        }

        public int getSpeed() {
            return this.speed;
        }

        public double getPhase2Threshold() {
            return this.phase2Threshold;
        }

        public double getPhase3Threshold() {
            return this.phase3Threshold;
        }

        /** @return Score given for defeating this kind of boss. */
        public int getPointValue() {
            return this.pointValue;
        }

        public Color getColor() {
            return this.color;
        }
    }

    /** Extra speed added in phase 2. */
    private static final int PHASE_2_SPEED_BONUS = 1;
    /** Extra speed added in phase 3. */
    private static final int PHASE_3_SPEED_BONUS = 2;

    /** Kind of this boss. */
    private final BossType type;
    /** Maximum health of the boss. */
    private final int maxHealth;
    /** Current health of the boss. */
    private int currentHealth;
    /** Horizontal speed in pixels per frame. */
    private int speed;
    private final double movementMultiplier;
    private double movementTicks;
    /** Moving direction: 1 is right, -1 is left. */
    private int direction;
    /** Current state of the boss. */
    private State state;
    /** Current phase: 1, 2 or 3. */
    private int phase;
	/** True while the boss energy shield is blocking player attacks. */
	private boolean shieldActive;

    /**
     * Constructor, creates the boss and resets its health and state.
     *
     * @param positionX
     *            Initial position of the boss in the X axis.
     * @param positionY
     *            Initial position of the boss in the Y axis.
     * @param type
     *            Kind of boss to create.
     */
    public Boss(final int positionX, final int positionY, final BossType type) {
        this(positionX, positionY, type, StoryDebugModifiers.defaults());
    }

    /** Scales only this boss's maximum health and movement. */
    public Boss(final int positionX, final int positionY, final BossType type,
            final StoryDebugModifiers modifiers) {
        // Keeps the 16x7 cell bounds; DrawManager chooses the type's sprite.
        super(positionX, positionY, WIDTH, HEIGHT, type.getColor());
        this.spriteType = SpriteType.EnemyShipSpecial;

        this.type = type;
        this.maxHealth = modifiers.scaleHp(type.getMaxHealth());
        this.movementMultiplier = modifiers.enemySpeedMultiplier();
        this.currentHealth = this.maxHealth;
        this.speed = type.getSpeed();
        this.direction = 1;
        this.state = State.MOVING;
        this.phase = 1;
    }

    /**
     * Moves the boss and turns it around at the borders of the play area.
     * Call once per frame.
     *
     * @param leftLimit
     *            Smallest X position the boss may reach.
     * @param rightLimit
     *            Largest X position the right edge of the boss may reach.
     */
    public final void update(final int leftLimit, final int rightLimit) {
        if (this.state == State.DEFEATED)
            return;

        // A hit only lasts one frame, then the boss keeps moving.
        if (this.state == State.HIT)
            this.state = State.MOVING;

        // Keep fractional frames and run each original boundary check, even at 4x.
        this.movementTicks += this.movementMultiplier;
        while (this.movementTicks >= 1.0) {
            moveOneTick(leftLimit, rightLimit);
            this.movementTicks -= 1.0;
        }
    }

    private void moveOneTick(final int leftLimit, final int rightLimit) {
        this.positionX += this.speed * this.direction;

        if (this.positionX < leftLimit) {
            this.positionX = leftLimit;
            this.direction = 1;
        } else if (this.positionX + this.width > rightLimit) {
            this.positionX = rightLimit - this.width;
            this.direction = -1;
        }
    }

    /**
     * Applies damage from a player attack.
     *
     * To detect events, compare getPhase() before and after this call
     * (phase changed) and check isDefeated() after it (defeated).
     *
     * @param damage
     *            Amount of health to remove.
     */
    public final void takeDamage(final int damage) {
		if (this.state == State.DEFEATED || this.shieldActive)
			return;

        this.currentHealth -= damage;
        if (this.currentHealth < 0)
            this.currentHealth = 0;
        if (this.currentHealth > this.maxHealth)
            this.currentHealth = this.maxHealth;

        this.state = State.HIT;

        // A defeated boss does not change phase.
        if (this.currentHealth > 0)
            updatePhase();
        else
            defeat();
    }

    /**
     * Updates the phase from the current health ratio, using the
     * thresholds of this kind of boss.
     * Each threshold is applied only once and the phase never goes back.
     */
    private void updatePhase() {
        double healthRatio = (double) this.currentHealth / this.maxHealth;

        if (this.phase < 3 && healthRatio < this.type.getPhase3Threshold()) {
            this.phase = 3;
            this.speed = this.type.getSpeed() + PHASE_3_SPEED_BONUS;
        } else if (this.phase < 2 && healthRatio < this.type.getPhase2Threshold()) {
            this.phase = 2;
            this.speed = this.type.getSpeed() + PHASE_2_SPEED_BONUS;
        }
    }

    /**
     * Marks the boss as defeated and stops its movement.
     */
	private void defeat() {
		this.state = State.DEFEATED;
		this.shieldActive = false;
		this.spriteType = SpriteType.Explosion;
	}

	public final boolean isShieldActive() {
		return this.shieldActive;
	}

	public final void setShieldActive(final boolean shieldActive) {
		this.shieldActive = shieldActive && this.state != State.DEFEATED;
	}

    public final BossType getType() {
        return this.type;
    }

    public final int getMaxHealth() {
        return this.maxHealth;
    }

    public final int getCurrentHealth() {
        return this.currentHealth;
    }

    public final int getSpeed() {
        return this.speed;
    }

    public final int getDirection() {
        return this.direction;
    }

    public final State getState() {
        return this.state;
    }

    /** Lets the attack system mark the boss as attacking or moving. */
    public final void setState(final State state) {
        if (this.state != State.DEFEATED)
            this.state = state;
    }

    /** @return Current phase: 1, 2 or 3. */
    public final int getPhase() {
        return this.phase;
    }

    /** @return True when the boss has been defeated. */
    public final boolean isDefeated() {
        return this.state == State.DEFEATED;
    }
}
