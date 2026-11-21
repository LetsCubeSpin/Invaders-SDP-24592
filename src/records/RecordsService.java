package records;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;

import story.Ending;

/**
 * Coordinates run records, achievements, notifications, and local saving.
 *
 * <p>Achievements are checked from their definitions in {@link Achievement}.
 * A {@link Achievement.Scope#RUN} achievement is checked against the run
 * being played (enemies defeated and active seconds) and, when a run is
 * completed, against the best completed run. A
 * {@link Achievement.Scope#TOTAL} achievement is checked against the values
 * of every completed run added up, so abandoned runs never count.</p>
 */
public final class RecordsService {
    private final ProfileStore store;
    private final ExecutorService writer;
    /** Achievements checked and listed, in display order. */
    private final List<Achievement> achievements;
    private final Map<String, RunRecord> records = new LinkedHashMap<String, RunRecord>();
    /**
     * Unlock time of each achievement tier, by {@link Achievement#unlockKey}.
     * Keys of achievements this build does not define are kept, so a
     * profile shared with a build that has more achievements keeps them.
     */
    private final Map<String, Long> unlockTimes = new LinkedHashMap<String, Long>();
    private final Set<Ending> seenEndings = EnumSet.noneOf(Ending.class);
    private final Deque<AchievementUnlock> notifications = new ArrayDeque<AchievementUnlock>();
    private final List<AchievementUnlock> currentRunUnlocks = new ArrayList<AchievementUnlock>();
    private final Set<String> seenDefeatEvents = new HashSet<String>();
    /** Values of every completed run added up, and the endings seen. */
    private final StatTally totals = new StatTally();
    /** Best value of each stat in one completed run. */
    private final StatTally bestRun = new StatTally();
    /** Values counted live in the run being played. */
    private StatTally activeRun = new StatTally();
    /** Highest score of the completed runs; meaningful once there are records. */
    private int highestScore;
    private String activeRunId;
    private boolean writesBlocked;
    private boolean closed;
    private final String loadWarning;
    private volatile String saveError;
    private long saveRevision;

    public RecordsService(final Path file) {
        this(file, Achievement.values());
    }

    /**
     * Uses other achievement definitions; for tests of tiers, progress and
     * hidden achievements that the game does not define.
     */
    RecordsService(final Path file, final List<Achievement> achievements) {
        this.achievements = Collections.unmodifiableList(
                new ArrayList<Achievement>(achievements));
        this.store = new ProfileStore(file);
		this.writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
			public Thread newThread(final Runnable runnable) {
				Thread thread = new Thread(runnable, "records-profile-writer");
				thread.setDaemon(true);
				return thread;
			}
		});
        try {
            ProfileSnapshot snapshot = this.store.load();
            for (RunRecord record : snapshot.getRecords())
                countRun(record);
            this.unlockTimes.putAll(snapshot.getUnlockTimes());
            this.seenEndings.addAll(snapshot.getSeenEndings());
            this.totals.set(Stat.ENDINGS_SEEN, this.seenEndings.size());
        } catch (IOException exception) {
            this.writesBlocked = true;
            this.saveError = "Profile load failed; existing files are protected: "
                    + exception.getMessage();
        }
        this.loadWarning = this.store.getLoadWarning();
    }

    public synchronized void startRun(final String runId) {
        if (this.closed) throw new IllegalStateException("Records service is closed.");
        if (runId == null || runId.trim().isEmpty()) throw new IllegalArgumentException("runId must not be blank.");
        this.activeRunId = runId;
        this.activeRun = new StatTally();
        this.seenDefeatEvents.clear();
        this.currentRunUnlocks.clear();
    }

    public synchronized void abandonRun(final String runId) {
        if (runId != null && runId.equals(this.activeRunId)) {
            this.activeRunId = null;
            this.seenDefeatEvents.clear();
        }
    }

    /** Accepts each source event once; safe for a future shared enemy event hook. */
    public synchronized boolean enemyDefeated(final String runId,
            final String eventId) {
        if (this.closed || runId == null || !runId.equals(this.activeRunId)
                || eventId == null || eventId.trim().length() == 0
                || !this.seenDefeatEvents.add(eventId))
            return false;
        this.activeRun.set(Stat.ENEMIES_DEFEATED,
                this.activeRun.get(Stat.ENEMIES_DEFEATED) + 1);
        check(Achievement.Scope.RUN, this.activeRun, System.currentTimeMillis());
        return true;
    }

    public synchronized void activeDuration(final String runId,
            final long durationMillis) {
        if (this.closed || runId == null || !runId.equals(this.activeRunId))
            return;
        this.activeRun.set(Stat.ACTIVE_SECONDS, Math.max(
                this.activeRun.get(Stat.ACTIVE_SECONDS), durationMillis / 1000));
        check(Achievement.Scope.RUN, this.activeRun, System.currentTimeMillis());
    }

    /**
     * Unlocks {@link Achievement#ALL_ENDINGS}. {@link #endingSeen} unlocks
     * it as well once every ending is saved. Meant to be called for
     * {@code event.AllEndingsSeenEvent}, for example
     * {@code publisher.subscribe(AllEndingsSeenEvent.class, e -> records.allEndingsSeen())}.
     *
     * <p>Not called in the game yet; ready to use once
     * {@code story.EndingCollection} is connected (see that class).</p>
     */
    public synchronized void allEndingsSeen() {
        if (!this.closed)
            reach(Achievement.ALL_ENDINGS, Achievement.ALL_ENDINGS.getTierCount(),
                    System.currentTimeMillis());
    }

    /**
     * Saves that an ending was seen, so it is still seen after a restart.
     * Meant to be called for {@code event.EndingReachedEvent}, for example
     * {@code publisher.subscribe(EndingReachedEvent.class, e -> records.endingSeen(e.getEnding()))}.
     *
     * <p>Not called in the game yet; ready to use once
     * {@code story.EndingCollection} is connected (see that class).</p>
     *
     * @param ending
     *            Ending the player saw.
     * @return Whether the ending was new and is being saved.
     */
    public synchronized boolean endingSeen(final Ending ending) {
        if (ending == null)
            throw new IllegalArgumentException("ending must not be null.");
        if (this.closed || !this.seenEndings.add(ending))
            return false;
        this.totals.set(Stat.ENDINGS_SEEN, this.seenEndings.size());
        check(Achievement.Scope.TOTAL, this.totals, System.currentTimeMillis());
        enqueueSave();
        return true;
    }

    /**
     * @return Endings seen in this or an earlier session, in ending order;
     *         pass them to {@code EndingCollection.restore} on startup.
     */
    public synchronized Set<Ending> getSeenEndings() {
        return Collections.unmodifiableSet(EnumSet.copyOf(this.seenEndings));
    }

    public synchronized boolean completeRun(final RunRecord record) {
        if (this.closed || record == null || this.records.containsKey(record.getRunId()))
            return false;
        // Every save containing a completion achievement also contains its run.
        countRun(record);
        // The best run, rather than this one, also unlocks a RUN achievement
        // added after an earlier run already reached it.
        check(Achievement.Scope.RUN, this.bestRun, record.getCompletionTimeMillis());
        check(Achievement.Scope.TOTAL, this.totals, record.getCompletionTimeMillis());
        if (record.getRunId().equals(this.activeRunId))
            this.activeRunId = null;
        enqueueSave();
        return true;
    }

    /** Adds a completed run to the records and to the achievement values. */
    private void countRun(final RunRecord record) {
        boolean beatHighScore = !this.records.isEmpty()
                && record.getScore() > this.highestScore;
        this.highestScore = this.records.isEmpty() ? record.getScore()
                : Math.max(this.highestScore, record.getScore());
        this.records.put(record.getRunId(), record);
        StatTally run = StatTally.ofRun(record, beatHighScore);
        this.totals.add(run);
        this.bestRun.max(run);
    }

    /** Unlocks every tier the values reach, for the achievements of one scope. */
    private void check(final Achievement.Scope scope, final StatTally values,
            final long timeMillis) {
        for (Achievement achievement : this.achievements)
            if (achievement.getScope() == scope)
                reach(achievement, achievement.tiersReached(
                        values.get(achievement.getStat())), timeMillis);
    }

    /** Unlocks the first tiers of an achievement, lowest first. */
    private void reach(final Achievement achievement, final int tiers,
            final long timeMillis) {
        for (int tier = 1; tier <= tiers; tier++)
            unlock(achievement, tier, timeMillis);
    }

    private void unlock(final Achievement achievement, final int tier,
            final long timeMillis) {
        String key = Achievement.unlockKey(achievement.getId(), tier);
        if (this.unlockTimes.containsKey(key))
            return;
        this.unlockTimes.put(key, Long.valueOf(timeMillis));
        AchievementUnlock unlock = new AchievementUnlock(achievement, tier);
        this.currentRunUnlocks.add(unlock);
        this.notifications.addLast(unlock);
        enqueueSave();
    }


    private ProfileSnapshot snapshot() {
        return new ProfileSnapshot(new ArrayList<RunRecord>(this.records.values()),
                this.unlockTimes, this.seenEndings);
    }

    public synchronized AchievementUnlock pollNotification() {
        return this.notifications.pollFirst();
    }

    /** @return the tiers unlocked since the current run started, in order. */
    public synchronized List<AchievementUnlock> getCurrentRunUnlocks() {
        return Collections.unmodifiableList(
                new ArrayList<AchievementUnlock>(this.currentRunUnlocks));
    }

    public synchronized List<RunRecord> getTopRuns() {
        List<RunRecord> result = new ArrayList<RunRecord>(this.records.values());
        Collections.sort(result, new Comparator<RunRecord>() {
            public int compare(final RunRecord first, final RunRecord second) {
                int scoreOrder = Integer.compare(second.getScore(), first.getScore());
                if (scoreOrder != 0)
                    return scoreOrder;
                int timeOrder = Long.compare(first.getCompletionTimeMillis(),
                        second.getCompletionTimeMillis());
                return timeOrder != 0 ? timeOrder
                        : first.getRunId().compareTo(second.getRunId());
            }
        });
        if (result.size() > 10)
            result = new ArrayList<RunRecord>(result.subList(0, 10));
        return Collections.unmodifiableList(result);
    }

    public synchronized int getHighestScore() {
        int result = 0;
        if (!this.records.isEmpty())
            result = Integer.MIN_VALUE;
        for (RunRecord record : this.records.values())
            result = Math.max(result, record.getScore());
        return result;
    }

    public synchronized int getMostEnemiesDefeated() {
        int result = 0;
        for (RunRecord record : this.records.values())
            result = Math.max(result, record.getEnemiesDefeated());
        return result;
    }

    public synchronized long getLongestSurvivalMillis() {
        long result = 0;
        for (RunRecord record : this.records.values())
            result = Math.max(result, record.getActiveDurationMillis());
        return result;
    }

    /** @return when the first tier of an achievement unlocked, or null. */
    public synchronized Long getUnlockTime(final Achievement achievement) {
        return getUnlockTime(achievement, 1);
    }

    /** @return when a tier, from 1, of an achievement unlocked, or null. */
    public synchronized Long getUnlockTime(final Achievement achievement,
            final int tier) {
        achievement.getTarget(tier);
        return this.unlockTimes.get(Achievement.unlockKey(achievement.getId(), tier));
    }

    /** @return how many tiers of an achievement are unlocked, lowest first. */
    public synchronized int getUnlockedTiers(final Achievement achievement) {
        int tiers = 0;
        while (tiers < achievement.getTierCount() && this.unlockTimes.containsKey(
                Achievement.unlockKey(achievement.getId(), tiers + 1)))
            tiers++;
        return tiers;
    }

    /**
     * @return the current value of an achievement's stat: the best completed
     *         run for a RUN achievement, every completed run added up for a
     *         TOTAL one.
     */
    public synchronized long getProgress(final Achievement achievement) {
        return (achievement.getScope() == Achievement.Scope.RUN ? this.bestRun
                : this.totals).get(achievement.getStat());
    }

    /** @return what the records page shows for each achievement, in order. */
    public synchronized List<AchievementStatus> getAchievementStatuses() {
        List<AchievementStatus> result = new ArrayList<AchievementStatus>();
        for (Achievement achievement : this.achievements) {
            int tiers = getUnlockedTiers(achievement);
            result.add(new AchievementStatus(achievement, tiers,
                    getProgress(achievement),
                    tiers == 0 ? null : getUnlockTime(achievement, tiers)));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized int getRecordCount() { return this.records.size(); }
    public synchronized boolean isWritesBlocked() { return this.writesBlocked; }
    public String getSaveError() { return this.saveError; }
    public String getLoadWarning() { return this.loadWarning; }

    public void close() {
        synchronized (this) {
            this.closed = true;
            this.writer.shutdown();
        }
        try {
            if (!this.writer.awaitTermination(10, TimeUnit.SECONDS)) {
                this.writer.shutdownNow();
                this.saveError = "Profile save did not finish before shutdown.";
            }
        } catch (InterruptedException exception) {
            this.writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void enqueueSave() {
        if (this.writesBlocked || this.closed) return;
        final ProfileSnapshot state=snapshot();
        final long revision=++this.saveRevision;
        this.saveError="Saving profile...";
        this.writer.execute(new Runnable() {
            public void run() {
                String error=null;
                try { store.write(state); }
                catch (IOException exception) { error="Save failed; previous data was preserved: "+exception.getMessage(); }
                synchronized (RecordsService.this) { if (revision==saveRevision) saveError=error; }
            }
        });
    }
}
