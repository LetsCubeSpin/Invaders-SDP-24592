package records;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import event.BossDefeatedEvent;
import event.BulletHitEvent;
import event.DeduplicatingListener;
import event.EnemyDefeatedEvent;
import event.GameplayEventListener;
import event.GameplayEventPublisher;
import event.ItemUsedEvent;
import event.PlayerHitEvent;
import event.ShotFiredEvent;

/**
 * Tracks active play segments and play statistics, and creates one record
 * when a run finishes.
 *
 * <p>The statistics come from the gameplay events of every screen the run
 * passes through; see {@link #listenTo}. They can be read while the run is
 * still playing, so an achievement checked at a stage clear sees the same
 * counts the record will keep.</p>
 */
public final class RunSession {

	/** Unique identifier shared by every level in this run. */
	private final String runId;
	/** Game mode of the run. */
	private final RunMode mode;
	/** Highest stage started. */
	private int stageReached;
	/** Waves started over the whole run. */
	private int wavesReached;
	/** Player bullets fired. */
	private int shotsFired;
	/** Player bullets that hit an enemy ship. */
	private int hits;
	/** Hits that cost the player a life. */
	private int timesHit;
	/** Bosses defeated. */
	private int bossesDefeated;
	/** Items used. */
	private int itemsUsed;
	/** Enemies defeated; only used for the values of a stage. */
	private int enemiesDefeated;
	/** Number of the stage being played, or 0 before the first. */
	private int currentStage;
	/**
	 * Counts when the current stage started: shots, hits, times hit,
	 * bosses, items, enemies and waves, in that order.
	 */
	private final int[] stageStartCounts = new int[7];
	/** Active duration in nanoseconds when the current stage started. */
	private long stageStartNanos;
	/** Whether the current stage was already cleared. */
	private boolean stageCleared;
	/** Stages cleared in this run, saved with its record. */
	private final List<StageClear> stagesCleared = new ArrayList<StageClear>();
	/** Accumulated active duration in nanoseconds. */
	private long activeDurationNanos;
	/** Start time of the current active segment. */
	private long activeSegmentStartNanos;
	/** Whether an active segment is currently being timed. */
	private boolean active;
	/** Completed record, retained so finish is idempotent. */
	private RunRecord completedRecord;

	/**
	 * Creates a session whose record has the {@link RunMode#UNKNOWN} mode.
	 *
	 * <p>For tests only. A real run must pass its mode to
	 * {@link #RunSession(String, RunMode)}; otherwise its record is saved
	 * as UNKNOWN without any error.</p>
	 *
	 * @param runId
	 *            Unique, non-blank run identifier.
	 */
	public RunSession(final String runId) {
		this(runId, RunMode.UNKNOWN);
	}

	/**
	 * Creates a session for one complete game run.
	 *
	 * @param runId
	 *            Unique, non-blank run identifier.
	 * @param mode
	 *            Game mode of the run.
	 */
	public RunSession(final String runId, final RunMode mode) {
		if (runId == null || runId.trim().length() == 0)
			throw new IllegalArgumentException("runId must not be blank.");
		if (mode == null)
			throw new IllegalArgumentException("mode must not be null.");
		this.runId = runId;
		this.mode = mode;
	}

	/**
	 * Counts this run's shots, bullet hits, player hits, boss defeats and
	 * item uses published on a screen's event bus. Each screen has its own bus,
	 * so every screen of the run calls this once. Events of other runs, a
	 * second delivery of the same event and events after the run finished
	 * are ignored.
	 *
	 * @param events
	 *            Event bus of one screen of this run.
	 */
	public void listenTo(final GameplayEventPublisher events) {
		if (events == null)
			throw new IllegalArgumentException("events must not be null.");
		events.subscribe(ShotFiredEvent.class,
				new DeduplicatingListener<ShotFiredEvent>(
						new GameplayEventListener<ShotFiredEvent>() {
			public void onEvent(final ShotFiredEvent event) {
				if (accepts(event.getRunId()))
					shotsFired = addCount(shotsFired, event.getBulletCount());
			}
		}));
		events.subscribe(BulletHitEvent.class,
				new DeduplicatingListener<BulletHitEvent>(
						new GameplayEventListener<BulletHitEvent>() {
			public void onEvent(final BulletHitEvent event) {
				if (accepts(event.getRunId()))
					hits = addCount(hits, 1);
			}
		}));
		events.subscribe(PlayerHitEvent.class,
				new DeduplicatingListener<PlayerHitEvent>(
						new GameplayEventListener<PlayerHitEvent>() {
			public void onEvent(final PlayerHitEvent event) {
				if (accepts(event.getRunId()))
					timesHit = addCount(timesHit, 1);
			}
		}));
		events.subscribe(BossDefeatedEvent.class,
				new DeduplicatingListener<BossDefeatedEvent>(
						new GameplayEventListener<BossDefeatedEvent>() {
			public void onEvent(final BossDefeatedEvent event) {
				if (accepts(event.getRunId()))
					bossesDefeated = addCount(bossesDefeated, 1);
			}
		}));
		events.subscribe(EnemyDefeatedEvent.class,
				new DeduplicatingListener<EnemyDefeatedEvent>(
						new GameplayEventListener<EnemyDefeatedEvent>() {
			public void onEvent(final EnemyDefeatedEvent event) {
				if (accepts(event.getRunId()))
					enemiesDefeated = addCount(enemiesDefeated, 1);
			}
		}));
		events.subscribe(ItemUsedEvent.class,
				new DeduplicatingListener<ItemUsedEvent>(
						new GameplayEventListener<ItemUsedEvent>() {
			public void onEvent(final ItemUsedEvent event) {
				if (accepts(event.getRunId()))
					itemsUsed = addCount(itemsUsed, 1);
			}
		}));
	}

	private boolean accepts(final String eventRunId) {
		return this.completedRecord == null && this.runId.equals(eventRunId);
	}

	/** Stops at the largest int instead of wrapping to a negative count. */
	private static int addCount(final int count, final int amount) {
		return Integer.MAX_VALUE - count < amount ? Integer.MAX_VALUE
				: count + amount;
	}

	/**
	 * Records that a stage of the run started; {@link #stageCleared}
	 * counts from here. Ignored after the run finished.
	 *
	 * @param stage
	 *            1-based stage number.
	 */
	public void stageStarted(final int stage) {
		if (stage < 1)
			throw new IllegalArgumentException("stage must be 1 or greater.");
		if (this.completedRecord != null)
			return;
		this.stageReached = Math.max(this.stageReached, stage);
		this.currentStage = stage;
		this.stageCleared = false;
		int[] counts = stageCounts();
		System.arraycopy(counts, 0, this.stageStartCounts, 0, counts.length);
		this.stageStartNanos = this.activeDurationNanos;
	}

	private int[] stageCounts() {
		return new int[] { this.shotsFired, this.hits, this.timesHit,
				this.bossesDefeated, this.itemsUsed, this.enemiesDefeated,
				this.wavesReached };
	}

	/**
	 * Records that the current stage was cleared, with the values counted
	 * since {@link #stageStarted}. The clear is kept with the run, so it is
	 * saved, and counts towards stage totals, only if the run is completed.
	 *
	 * @param nowNanos
	 *            Monotonic time, normally from System.nanoTime().
	 * @return the cleared stage, or null if no stage started, the stage was
	 *         already cleared, or the run finished.
	 */
	public StageClear stageCleared(final long nowNanos) {
		if (this.completedRecord != null || this.currentStage == 0
				|| this.stageCleared)
			return null;
		int[] counts = stageCounts();
		int[] start = this.stageStartCounts;
		long nanos = this.activeDurationNanos;
		if (this.active) {
			long elapsed = nowNanos - this.activeSegmentStartNanos;
			if (elapsed > 0 && Long.MAX_VALUE - nanos >= elapsed)
				nanos += elapsed;
		}
		StageClear clear = new StageClear(this.currentStage,
				counts[5] - start[5], counts[6] - start[6],
				counts[0] - start[0], counts[1] - start[1],
				counts[2] - start[2], counts[3] - start[3],
				counts[4] - start[4],
				TimeUnit.NANOSECONDS.toSeconds(nanos - this.stageStartNanos));
		this.stageCleared = true;
		this.stagesCleared.add(clear);
		return clear;
	}

	/** @return the stages cleared so far, in order. */
	public List<StageClear> getStagesCleared() {
		return Collections.unmodifiableList(
				new ArrayList<StageClear>(this.stagesCleared));
	}

	/** Records that a wave started; waves add up over the whole run. */
	public void waveStarted() {
		if (this.completedRecord == null)
			this.wavesReached = addCount(this.wavesReached, 1);
	}

	/**
	 * Starts an active play segment. Repeated start events are ignored.
	 *
	 * @param nowNanos
	 *            Monotonic time, normally from System.nanoTime().
	 */
	public void startActiveSegment(final long nowNanos) {
		if (this.completedRecord == null && !this.active) {
			this.activeSegmentStartNanos = nowNanos;
			this.active = true;
		}
	}

	/**
	 * Stops an active play segment. Repeated stop events are ignored.
	 *
	 * @param nowNanos
	 *            Monotonic time, normally from System.nanoTime().
	 */
	public void stopActiveSegment(final long nowNanos) {
		if (this.completedRecord != null || !this.active)
			return;

		long elapsedNanos = nowNanos - this.activeSegmentStartNanos;
		if (elapsedNanos < 0)
			throw new IllegalArgumentException(
					"nowNanos must not be before the segment start.");
		if (Long.MAX_VALUE - this.activeDurationNanos < elapsedNanos)
			throw new IllegalStateException("Active duration overflow.");

		this.activeDurationNanos += elapsedNanos;
		this.active = false;
	}

	/**
	 * Finishes the run once. Later calls return the original record unchanged.
	 *
	 * @param score
	 *            Final score.
	 * @param enemiesDefeated
	 *            Total enemies defeated.
	 * @param completionTimeMillis
	 *            Completion time in Unix epoch milliseconds.
	 * @return the one completed record for this session.
	 */
	public RunRecord finish(final int score, final int enemiesDefeated,
			final long completionTimeMillis) {
		if (this.completedRecord != null)
			return this.completedRecord;
		if (this.active)
			throw new IllegalStateException(
					"Stop the active segment before finishing the run.");

		this.completedRecord = new RunRecord(this.runId, this.mode, score,
				enemiesDefeated, TimeUnit.NANOSECONDS.toMillis(
						this.activeDurationNanos), completionTimeMillis,
				getStats(), this.stagesCleared);
		return this.completedRecord;
	}

	/** Returns accumulated active time, including a currently active segment. */
	public long getActiveDurationMillis(final long nowNanos) {
		long nanos = this.activeDurationNanos;
		if (this.active && this.completedRecord == null) {
			long elapsed = nowNanos - this.activeSegmentStartNanos;
			if (elapsed > 0 && Long.MAX_VALUE - nanos >= elapsed)
				nanos += elapsed;
		}
		return TimeUnit.NANOSECONDS.toMillis(nanos);
	}

	/** Returns the ID shared by this run's stages. */
	public String getRunId() {
		return this.runId;
	}

	/** @return the game mode of the run. */
	public RunMode getMode() {
		return this.mode;
	}

	/**
	 * @return the statistics counted so far, or the final ones once the run
	 *         finished.
	 */
	public RunStats getStats() {
		if (this.completedRecord != null)
			return this.completedRecord.getStats();
		return new RunStats(this.stageReached, this.wavesReached,
				this.shotsFired, this.hits, this.timesHit,
				this.bossesDefeated, this.itemsUsed);
	}

	/** @return accuracy so far, as {@link RunStats#getAccuracy()} defines it. */
	public double getAccuracy() {
		return RunStats.accuracy(this.hits, this.shotsFired);
	}
}
