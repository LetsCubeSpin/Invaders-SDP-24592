package boss;

import java.util.*;
import java.util.function.LongSupplier;

import engine.Cooldown;
import entity.Boss;
import entity.BossBullet;
import entity.Entity;
import entity.EnemyType;

/**
 * Boss attack system: attack interval and cooldowns, attack patterns per
 * phase, and the special attack (PRD 2.3 - 2.5).
 *
 * The phase itself is decided by Boss Developer 1. This class is only told
 * about it through {@link #setPhase(int)}.
 *
 * Boss Developer 2.
 */
public final class BossAttackSystem {

    /** Shortest allowed time between two attacks, in milliseconds. */
    private static final int MIN_INTERVAL = 150;

    /**
     * What happened in one call to {@link BossAttackSystem#update}.
     */
    public static final class AttackResult {

        /** Name of the pattern or special attack. */
        private final String attackType;
        /** True for a special attack. */
        private final boolean special;
        /** True for the warning before a special attack. */
        private final boolean windup;
        /** Bullets fired. Empty during windup. */
        private final List<BossBullet> bullets;
        /** Enemies spawned */
        private final List<SummonRequest> summons;

        AttackResult(final String attackType, final boolean special,
                     final boolean windup, final List<BossBullet> bullets) {
            this(attackType, special, windup, bullets,
                    Collections.<SummonRequest>emptyList());
        }

        /**
         * Constructor.
         *
         * @param attackType
         *            Name of the pattern.
         * @param special
         *            True for a special attack.
         * @param windup
         *            True for the warning before a special attack.
         * @param bullets
         *            Bullets fired.
         * @param summons
         *            Enemies spawned.
         */

        AttackResult(final String attackType, final boolean special,
                     final boolean windup, final List<BossBullet> bullets,
                     final List<SummonRequest> summons) {
            this.attackType = attackType;
            this.special = special;
            this.windup = windup;
            this.bullets = Collections.unmodifiableList(bullets);
            this.summons = Collections.unmodifiableList(summons);
        }

        public String getAttackType() {
            return this.attackType;
        }

        public boolean isSpecial() {
            return this.special;
        }

        public boolean isWindup() {
            return this.windup;
        }

        public List<BossBullet> getBullets() {
            return this.bullets;
        }
        public List<SummonRequest> getSummons() {
            return this.summons;
        }
    }

    /** Attack data of the boss. */
    private final BossAttackConfig config;
    /** Random generator used to pick patterns. */
    private final Random random;
	/** Clock source in milliseconds. */
	private final LongSupplier clock;
    /** Current phase. */
    private int phase;
    /** False after the boss is defeated. */
    private boolean active;
    /** Time until the next attack. */
    private Cooldown attackCooldown;
    /** Special attack waiting for its windup to end, or null. */
    private SpecialAttack pendingSpecial;
    /** Windup time of the pending special attack. */
    private Cooldown windupCooldown;
    /** Current attack interval multiplier from rapid fire. */
    private double buffMultiplier;
    /** Time until the rapid fire effect ends, or null. */
    private Cooldown buffCooldown;
    /** True after the first update, when the boss battle really begins. */
    private boolean started;
    /** Time until the next minion summon, or null if disabled in current phase. */
    private Cooldown summonCooldown;
	/** Time until the next periodic shield activation, or null if disabled. */
	private Cooldown shieldCooldown;
	/** Remaining active time of the current shield, or null if inactive. */
	private Cooldown shieldDurationCooldown;
	/** True while the boss shield is currently active. */
	private boolean shieldActive;
	/** Timestamp when the current special attack windup started. */
	private long windupStartMillis;
	/** Total windup duration of the pending special attack. */
	private int windupDurationMillis;
	/** Remaining active time of a firing laser beam, or null if not firing. */
	private Cooldown beamCooldown;
	/** Width of the currently firing laser beam in pixels. */
	private int activeBeamWidth;
    /**
     * True when a phase that allows the special attack has just begun. The
     * first special attack of the phase then starts right away, without
     * waiting for the attack interval or the special cooldown.
     */
    private boolean specialDueAtPhaseStart;

	/** True on the frame the periodic shield just turned on. */
	private boolean shieldActivatedThisFrame;

    /**
     * Constructor. Cooldowns start on the first {@link #update} call,
     * so the first attack comes one interval after the battle begins.
     *
     * @param config
     *            Attack data of the boss.
     * @param random
     *            Random generator used to pick patterns.
     */
    public BossAttackSystem(final BossAttackConfig config, final Random random) {
		this(config, random, System::currentTimeMillis);
	}

	/**
	 * Package-private constructor with an injectable clock for deterministic tests.
	 */
	BossAttackSystem(final BossAttackConfig config,
					 final Random random, final LongSupplier clock) {
        this.config = config;
        this.random = random;
		this.clock = Objects.requireNonNull(clock, "clock");
        this.phase = 1;
        this.active = true;
        this.buffMultiplier = 1.0;
        restartAttackCooldown();
    }

    /**
     * Changes the attack setup to the given phase (PRD 2.4).
     * Any special attack being prepared is cancelled.
     *
     * @param newPhase
     *            New phase number.
     */
	public void setPhase(final int newPhase) {
		if (newPhase == this.phase)
			return;
		boolean specialWasEnabled = this.config.getPhase(this.phase)
				.isSpecialEnabled();
		this.phase = newPhase;
		this.pendingSpecial = null;
		this.beamCooldown = null;
		this.activeBeamWidth = 0;
		restartAttackCooldown();
		restartSummonCooldown();
		restartShieldCooldown();
		// The first special attack of the phase is used right away (PRD 2.5).
		if (!specialWasEnabled)
			markSpecialDueIfEnabled();
	}

	/** Stops all attacks, used when the boss is defeated. */
	public void stop() {
		this.shieldActivatedThisFrame = false;
		this.active = false;
		this.pendingSpecial = null;
		this.shieldActive = false;
		this.shieldDurationCooldown = null;
		this.beamCooldown = null;
		this.activeBeamWidth = 0;
	}

    /**
     * Runs the attack logic. Call once per frame.
     *
     * @param originX
     *            Horizontal center of the boss.
     * @param originY
     *            Bottom of the boss.
     * @param targetX
     *            Current X of the player.
     * @param targetY
     *            Current Y of the player.
     * @return What happened this frame, or null if nothing.
     */
    public AttackResult update(final int originX, final int originY,
            final int targetX, final int targetY) {


        if (!this.active)
            return null;

		this.shieldActivatedThisFrame = false;

        // GameScreen calls this only after its input delay, like
        // EnemyShipFormation, so the countdown does not use up the first
        // attack interval.
		if (!this.started) {
			this.started = true;
			restartAttackCooldown();
			restartSummonCooldown();
			restartShieldCooldown();
			markSpecialDueIfEnabled();
			return null;
		}

		updateShieldState();

		if (this.beamCooldown != null && this.beamCooldown.checkFinished()) {
			this.beamCooldown = null;
			this.activeBeamWidth = 0;
		}

        if (this.buffCooldown != null && this.buffCooldown.checkFinished()) {
            this.buffCooldown = null;
            this.buffMultiplier = 1.0;
            // The running wait was shortened by the buff, so start it again
            // with the normal interval.
            restartAttackCooldown();
        }

        // 탄막 쿨타임과 무관하게 매 프레임 소환 타이머 체크
        List<SummonRequest> summons = checkAndCreateSummons(originX, originY);

		// 1. 특수 공격 준비 중
		if (this.pendingSpecial != null) {
			if (!this.windupCooldown.checkFinished())
				return summons.isEmpty() ? null
						: new AttackResult("SUMMON", false, false,
						Collections.<BossBullet>emptyList(), summons);
			SpecialAttack special = this.pendingSpecial;
			this.pendingSpecial = null;
			List<BossBullet> bullets = special.execute(originX, originY,
					targetX, targetY, this.config.getDamage());
			if (special.getBuffMilliseconds() > 0) {
				this.buffMultiplier = special.getIntervalMultiplier();
				this.buffCooldown = new Cooldown(
						special.getBuffMilliseconds(), this.clock);
				this.buffCooldown.reset();
			}
			if (special.getBeamMilliseconds() > 0) {
				this.beamCooldown = new Cooldown(
						special.getBeamMilliseconds(), this.clock);
				this.beamCooldown.reset();
				this.activeBeamWidth = special.getBeamWidth();
			}
			restartAttackCooldown();
			return new AttackResult(special.getName(), true, false, bullets,
					summons);
		}

        // 2. 공격 쿨타임 대기 중이어도 소환이 발동되었으면 결과 반환
        if (!this.specialDueAtPhaseStart
                && !this.attackCooldown.checkFinished()) {
            return summons.isEmpty() ? null
                    : new AttackResult("SUMMON", false, false,
                    Collections.<BossBullet>emptyList(), summons);
        }

		// 3. 특수 공격 발동
		BossAttackConfig.PhaseConfig phaseConfig = this.config
				.getPhase(this.phase);
		SpecialAttack special = this.config.getSpecialAttack();
		if (phaseConfig.isSpecialEnabled() && special != null
				&& (this.specialDueAtPhaseStart || special.isReady())) {
			this.specialDueAtPhaseStart = false;
			this.pendingSpecial = special;
			this.windupDurationMillis = Math.max(1,
					special.getWindupMilliseconds());
			this.windupStartMillis = this.clock.getAsLong();
			this.windupCooldown = new Cooldown(this.windupDurationMillis, this.clock);
			this.windupCooldown.reset();
			return new AttackResult(special.getName(), true, true,
					new ArrayList<BossBullet>(), summons);
		}

        // 4. 일반 탄막 공격 (+ 동일 프레임에 소환이 겹쳤다면 함께 포함)
        AttackPattern pattern = phaseConfig.pick(this.random);
        List<BossBullet> bullets = pattern.fire(originX, originY, targetX,
                targetY, this.config.getDamage());
        restartAttackCooldown();
        return new AttackResult(pattern.getName(), false, false, bullets,
                summons);
    }

    private List<SummonRequest> checkAndCreateSummons(final int originX,
                                                      final int originY) {
        if (this.summonCooldown == null || !this.summonCooldown.checkFinished())
            return Collections.emptyList();

        BossAttackConfig.PhaseConfig phaseConfig = this.config
                .getPhase(this.phase);
        if (!phaseConfig.isSummonEnabled())
            return Collections.emptyList();

        this.summonCooldown.reset();
        int count = phaseConfig.getSummonCount();
        int spacing = 44; // 소환 적 가로 간격 (EnemyShip 폭 24px 기준)
        List<SummonRequest> summons = new ArrayList<SummonRequest>(count);

        for (int i = 0; i < count; i++) {
            int offsetX = (i * 2 - (count - 1)) * (spacing / 2);
            summons.add(new SummonRequest(originX + offsetX - 12, originY + 4,
                    phaseConfig.getSummonEnemyType()));
        }
        return summons;
    }

    private void restartSummonCooldown() {
        BossAttackConfig.PhaseConfig phaseConfig = this.config
                .getPhase(this.phase);
        if (phaseConfig.isSummonEnabled()) {
            this.summonCooldown = new Cooldown(
                    phaseConfig.getSummonIntervalMilliseconds(), this.clock);
            this.summonCooldown.reset();
        } else {
            this.summonCooldown = null;
        }
    }

	private void updateShieldState() {
		if (this.shieldActive) {
			if (this.shieldDurationCooldown != null
					&& this.shieldDurationCooldown.checkFinished()) {
				this.shieldActive = false;
				this.shieldDurationCooldown = null;
				restartShieldCooldown();
			}
			return;
		}

		BossAttackConfig.PhaseConfig phaseConfig = this.config
				.getPhase(this.phase);
		if (!phaseConfig.isShieldEnabled())
			return;

		if (this.shieldCooldown != null && this.shieldCooldown.checkFinished()) {
			this.shieldActive = true;
			this.shieldActivatedThisFrame = true;
			this.shieldDurationCooldown = new Cooldown(
					phaseConfig.getShieldDurationMilliseconds(), this.clock);
			this.shieldDurationCooldown.reset();
		}
	}

	private void restartShieldCooldown() {
		this.shieldActive = false;
		this.shieldDurationCooldown = null;
		BossAttackConfig.PhaseConfig phaseConfig = this.config
				.getPhase(this.phase);
		if (phaseConfig.isShieldEnabled()) {
			this.shieldCooldown = new Cooldown(
					phaseConfig.getShieldIntervalMilliseconds(), this.clock);
			this.shieldCooldown.reset();
		} else {
			this.shieldCooldown = null;
		}
	}

	public boolean isShieldActive() {
		return this.shieldActive;
	}

	public boolean isLaserCharging() {
		return this.pendingSpecial != null
				&& this.pendingSpecial.getBeamMilliseconds() > 0;
	}

	public boolean isLaserFiring() {
		return this.beamCooldown != null && !this.beamCooldown.checkFinished();
	}

	/** True when the active beam overlaps the target below the boss. */
	public boolean laserHits(final Boss boss, final Entity target) {
		int centerX = boss.getPositionX() + boss.getWidth() / 2;
		int halfWidth = this.activeBeamWidth / 2;
		return isLaserFiring()
				&& target.getPositionX() < centerX + halfWidth
				&& target.getPositionX() + target.getWidth() > centerX - halfWidth
				&& target.getPositionY() + target.getHeight()
						> boss.getPositionY() + boss.getHeight();
	}

	public int getActiveBeamWidth() {
		return this.activeBeamWidth;
	}

	public float getLaserChargeProgress() {
		if (!isLaserCharging())
			return isLaserFiring() ? 1.0f : 0.0f;
		float progress = (this.clock.getAsLong() - this.windupStartMillis)
				/ (float) this.windupDurationMillis;
		return Math.max(0f, Math.min(1f, progress));
	}

    /** @return Current time between attacks, in milliseconds. */
    public int getCurrentIntervalMilliseconds() {
        double interval = this.config.getBaseIntervalMilliseconds()
                * this.config.getPhase(this.phase).getIntervalMultiplier()
                * this.buffMultiplier;
        return Math.max(MIN_INTERVAL, (int) Math.round(interval));
    }

    /**
     * Marks the first special attack of the phase as due if the current
     * phase allows it. Later uses wait for the special cooldown.
     */
    private void markSpecialDueIfEnabled() {
        this.specialDueAtPhaseStart = this.config.getSpecialAttack() != null
                && this.config.getPhase(this.phase).isSpecialEnabled();
    }

    /** Starts waiting for the next attack with the current interval. */
    private void restartAttackCooldown() {
        this.attackCooldown = new Cooldown(getCurrentIntervalMilliseconds(), this.clock);
        this.attackCooldown.reset();
    }

	/** Package-private check for BossManager to emit SHIELD_ACTIVATED event. */
	boolean consumeShieldActivated() {
		boolean activated = this.shieldActivatedThisFrame;
		this.shieldActivatedThisFrame = false;
		return activated;
	}

    public static final class SummonRequest {
        private final int positionX;
        private final int positionY;
        private final EnemyType enemyType;

        public SummonRequest(final int positionX, final int positionY,
                             final EnemyType enemyType) {
            this.positionX = positionX;
            this.positionY = positionY;
            this.enemyType = enemyType;
        }

        public int getPositionX() {
            return this.positionX;
        }

        public int getPositionY() {
            return this.positionY;
        }

        public EnemyType getEnemyType() {
            return this.enemyType;
        }
    }

    public int getPhase() {
        return this.phase;
    }

    public boolean isActive() {
        return this.active;
    }

    /** @return True while a special attack is being prepared. */
    public boolean isWindingUp() {
        return this.pendingSpecial != null;
    }
}
