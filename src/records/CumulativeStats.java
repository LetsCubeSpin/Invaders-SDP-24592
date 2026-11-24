package records;

import java.util.Collection;

/**
 * Values added up over every completed run, for the stats tab of the
 * records page. Abandoned runs leave no record, so they are not counted.
 *
 * <p>Stages cleared come from the stage clears saved with each run, so runs
 * saved before stage clears were kept add none.</p>
 */
public final class CumulativeStats {
    private int runsCompleted;
    private long enemiesDefeated;
    private long activeDurationMillis;
    private long shotsFired;
    private long hits;
    private long timesHit;
    private long bossesDefeated;
    private long itemsUsed;
    private long stagesCleared;
    private final int endingsSeen;
    private final int endingCount;

    /**
     * Adds up the runs.
     *
     * @param runs
     *            Completed runs.
     * @param endingsSeen
     *            Endings seen at least once.
     * @param endingCount
     *            Endings the game has.
     */
    CumulativeStats(final Collection<RunRecord> runs, final int endingsSeen,
            final int endingCount) {
        for (RunRecord run : runs) {
            this.runsCompleted++;
            this.enemiesDefeated += run.getEnemiesDefeated();
            this.activeDurationMillis += run.getActiveDurationMillis();
            this.stagesCleared += run.getStagesCleared().size();
            RunStats stats = run.getStats();
            this.shotsFired += stats.getShotsFired();
            this.hits += stats.getHits();
            this.timesHit += stats.getTimesHit();
            this.bossesDefeated += stats.getBossesDefeated();
            this.itemsUsed += stats.getItemsUsed();
        }
        this.endingsSeen = endingsSeen;
        this.endingCount = endingCount;
    }

    /** @return completed runs. */
    public int getRunsCompleted() {
        return this.runsCompleted;
    }

    /** @return enemies defeated in every completed run. */
    public long getEnemiesDefeated() {
        return this.enemiesDefeated;
    }

    /** @return active play time of every completed run, in milliseconds. */
    public long getActiveDurationMillis() {
        return this.activeDurationMillis;
    }

    /** @return player bullets fired. */
    public long getShotsFired() {
        return this.shotsFired;
    }

    /** @return player bullets that hit an enemy ship. */
    public long getHits() {
        return this.hits;
    }

    /** @return hits over shots, from 0 to 1; 0 without shots. */
    public double getAccuracy() {
        return this.shotsFired == 0 ? 0 : (double) this.hits / this.shotsFired;
    }

    /** @return hits that cost the player a life. */
    public long getTimesHit() {
        return this.timesHit;
    }

    /** @return bosses defeated. */
    public long getBossesDefeated() {
        return this.bossesDefeated;
    }

    /** @return items used. */
    public long getItemsUsed() {
        return this.itemsUsed;
    }

    /** @return stages cleared, from the stage clears saved with the runs. */
    public long getStagesCleared() {
        return this.stagesCleared;
    }

    /** @return endings seen at least once. */
    public int getEndingsSeen() {
        return this.endingsSeen;
    }

    /** @return endings the game has. */
    public int getEndingCount() {
        return this.endingCount;
    }
}
