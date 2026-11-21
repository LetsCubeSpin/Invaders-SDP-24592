package records;

/**
 * A count that achievements measure. Each value is counted per completed
 * run; a {@link Achievement.Scope#TOTAL} achievement adds the values of
 * every completed run, and a {@link Achievement.Scope#RUN} achievement uses
 * the best single run.
 *
 * <p>Values never go below 0 and only grow during a run, so an achievement
 * is reached once its value is at least the target.</p>
 */
public enum Stat {
    /** 1 for every completed run. */
    RUNS_COMPLETED,
    /** Enemies defeated; also counted live during a run. */
    ENEMIES_DEFEATED,
    /** Whole seconds of active play; also counted live during a run. */
    ACTIVE_SECONDS,
    /** Final score, or 0 for a negative score. */
    SCORE,
    /** 1 when the run scored more than every earlier completed run. */
    HIGH_SCORES_BEATEN,
    /** Highest stage started. */
    STAGE_REACHED,
    /** Waves started. */
    WAVES_REACHED,
    /** Player bullets fired. */
    SHOTS_FIRED,
    /** Player bullets that hit an enemy ship. */
    BULLET_HITS,
    /** Hits that cost the player a life. */
    TIMES_HIT,
    /** Bosses defeated. */
    BOSSES_DEFEATED,
    /** Items used. */
    ITEMS_USED,
    /**
     * Story endings seen at least once. Not counted per run: endings belong
     * to the whole profile, so only TOTAL achievements can use it.
     */
    ENDINGS_SEEN;

    /**
     * @param run
     *            Completed run.
     * @param beatHighScore
     *            Whether the run scored more than every earlier completed run.
     * @return this value for one completed run.
     */
    long ofRun(final RunRecord run, final boolean beatHighScore) {
        RunStats stats = run.getStats();
        switch (this) {
        case RUNS_COMPLETED: return 1;
        case ENEMIES_DEFEATED: return Math.max(0, run.getEnemiesDefeated());
        case ACTIVE_SECONDS: return Math.max(0, run.getActiveDurationMillis() / 1000);
        case SCORE: return Math.max(0, run.getScore());
        case HIGH_SCORES_BEATEN: return beatHighScore ? 1 : 0;
        case STAGE_REACHED: return stats.getStageReached();
        case WAVES_REACHED: return stats.getWavesReached();
        case SHOTS_FIRED: return stats.getShotsFired();
        case BULLET_HITS: return stats.getHits();
        case TIMES_HIT: return stats.getTimesHit();
        case BOSSES_DEFEATED: return stats.getBossesDefeated();
        case ITEMS_USED: return stats.getItemsUsed();
        default: return 0;
        }
    }
}
