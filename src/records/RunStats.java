package records;

/**
 * Immutable play statistics of one run, collected by {@link RunSession}
 * from the gameplay events.
 */
public final class RunStats {

	/** Statistics of a run that recorded none. */
	public static final RunStats EMPTY = new RunStats(0, 0, 0, 0, 0, 0, 0);

	/** Highest stage started; 0 for modes without stages. */
	private final int stageReached;
	/** Waves started over the whole run; 0 for modes without waves. */
	private final int wavesReached;
	/** Player bullets fired. */
	private final int shotsFired;
	/** Player bullets that hit an enemy ship. */
	private final int hits;
	/** Hits that cost the player a life. */
	private final int timesHit;
	/** Bosses defeated. */
	private final int bossesDefeated;
	/** Items used. */
	private final int itemsUsed;

	/**
	 * @param stageReached
	 *            Highest stage started, 0 or more.
	 * @param wavesReached
	 *            Waves started over the whole run, 0 or more.
	 * @param shotsFired
	 *            Player bullets fired, 0 or more.
	 * @param hits
	 *            Player bullets that hit an enemy ship, 0 or more.
	 * @param timesHit
	 *            Hits that cost the player a life, 0 or more.
	 * @param bossesDefeated
	 *            Bosses defeated, 0 or more.
	 * @param itemsUsed
	 *            Items used, 0 or more.
	 */
	public RunStats(final int stageReached, final int wavesReached,
			final int shotsFired, final int hits, final int timesHit,
			final int bossesDefeated, final int itemsUsed) {
		requireNotNegative(stageReached, "stageReached");
		requireNotNegative(wavesReached, "wavesReached");
		requireNotNegative(shotsFired, "shotsFired");
		requireNotNegative(hits, "hits");
		requireNotNegative(timesHit, "timesHit");
		requireNotNegative(bossesDefeated, "bossesDefeated");
		requireNotNegative(itemsUsed, "itemsUsed");
		this.stageReached = stageReached;
		this.wavesReached = wavesReached;
		this.shotsFired = shotsFired;
		this.hits = hits;
		this.timesHit = timesHit;
		this.bossesDefeated = bossesDefeated;
		this.itemsUsed = itemsUsed;
	}

	private static void requireNotNegative(final int value, final String name) {
		if (value < 0)
			throw new IllegalArgumentException(name + " must not be negative.");
	}

	/** @return the highest stage started; 0 for modes without stages. */
	public int getStageReached() {
		return this.stageReached;
	}

	/** @return the waves started over the whole run. */
	public int getWavesReached() {
		return this.wavesReached;
	}

	/** @return the player bullets fired. */
	public int getShotsFired() {
		return this.shotsFired;
	}

	/**
	 * @return the player bullets that hit an enemy ship: formation enemies,
	 *         bonus ships, summoned minions and bosses, whether or not the
	 *         hit defeated them.
	 */
	public int getHits() {
		return this.hits;
	}

	/** @return the hits that cost the player a life. */
	public int getTimesHit() {
		return this.timesHit;
	}

	/** @return the bosses defeated. */
	public int getBossesDefeated() {
		return this.bossesDefeated;
	}

	/** @return the items used. */
	public int getItemsUsed() {
		return this.itemsUsed;
	}

	/**
	 * Bullets that hit an enemy ship divided by bullets fired. The ratio is
	 * capped at 1 in case hit events arrive without their shots. A run
	 * without shots has an accuracy of 0, so it cannot meet an accuracy
	 * condition by never shooting.
	 *
	 * @return accuracy from 0 to 1.
	 */
	public double getAccuracy() {
		return accuracy(this.hits, this.shotsFired);
	}

	/** Shared by the live counts of {@link RunSession}. */
	static double accuracy(final int hits, final int shotsFired) {
		if (shotsFired == 0)
			return 0;
		return Math.min(1.0, (double) hits / shotsFired);
	}
}
