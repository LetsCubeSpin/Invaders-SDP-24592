package records;

/**
 * Immutable summary of one completed game run.
 */
public final class RunRecord {

	/** Unique identifier for the run. */
	private final String runId;
	/** Final score. */
	private final int score;
	/** Total number of enemies defeated. */
	private final int enemiesDefeated;
	/** Active play duration, excluding inactive segments. */
	private final long activeDurationMillis;
	/** Time at which the run completed, in Unix epoch milliseconds. */
	private final long completionTimeMillis;
	/** Game mode of the run. */
	private final RunMode mode;
	/** Play statistics of the run. */
	private final RunStats stats;

	/**
	 * Creates a record without a mode or play statistics, as version 1
	 * profiles stored it: the mode is {@link RunMode#UNKNOWN} and every
	 * statistic is 0.
	 *
	 * @param runId
	 *            Unique, non-blank run identifier.
	 * @param score
	 *            Final score.
	 * @param enemiesDefeated
	 *            Total number of enemies defeated.
	 * @param activeDurationMillis
	 *            Active play duration in milliseconds.
	 * @param completionTimeMillis
	 *            Completion time in Unix epoch milliseconds.
	 */
	public RunRecord(final String runId, final int score,
			final int enemiesDefeated, final long activeDurationMillis,
			final long completionTimeMillis) {
		this(runId, RunMode.UNKNOWN, score, enemiesDefeated,
				activeDurationMillis, completionTimeMillis, RunStats.EMPTY);
	}

	/**
	 * Creates a completed run record.
	 *
	 * @param runId
	 *            Unique, non-blank run identifier.
	 * @param mode
	 *            Game mode of the run.
	 * @param score
	 *            Final score.
	 * @param enemiesDefeated
	 *            Total number of enemies defeated.
	 * @param activeDurationMillis
	 *            Active play duration in milliseconds.
	 * @param completionTimeMillis
	 *            Completion time in Unix epoch milliseconds.
	 * @param stats
	 *            Play statistics of the run.
	 */
	public RunRecord(final String runId, final RunMode mode, final int score,
			final int enemiesDefeated, final long activeDurationMillis,
			final long completionTimeMillis, final RunStats stats) {
		if (runId == null || runId.trim().length() == 0)
			throw new IllegalArgumentException("runId must not be blank.");
		if (enemiesDefeated < 0)
			throw new IllegalArgumentException(
					"enemiesDefeated must not be negative.");
		if (activeDurationMillis < 0)
			throw new IllegalArgumentException(
					"activeDurationMillis must not be negative.");
		if (completionTimeMillis <= 0)
			throw new IllegalArgumentException(
					"completionTimeMillis must be positive.");
		if (mode == null)
			throw new IllegalArgumentException("mode must not be null.");
		if (stats == null)
			throw new IllegalArgumentException("stats must not be null.");

		this.runId = runId;
		this.score = score;
		this.enemiesDefeated = enemiesDefeated;
		this.activeDurationMillis = activeDurationMillis;
		this.completionTimeMillis = completionTimeMillis;
		this.mode = mode;
		this.stats = stats;
	}

	/** @return the unique run identifier. */
	public String getRunId() {
		return this.runId;
	}

	/** @return the final score. */
	public int getScore() {
		return this.score;
	}

	/** @return the total number of enemies defeated. */
	public int getEnemiesDefeated() {
		return this.enemiesDefeated;
	}

	/** @return the active play duration in milliseconds. */
	public long getActiveDurationMillis() {
		return this.activeDurationMillis;
	}

	/** @return the completion time in Unix epoch milliseconds. */
	public long getCompletionTimeMillis() {
		return this.completionTimeMillis;
	}

	/** @return the game mode of the run. */
	public RunMode getMode() {
		return this.mode;
	}

	/** @return the play statistics of the run. */
	public RunStats getStats() {
		return this.stats;
	}
}
