package records;

/**
 * A value that achievements measure, counted for each completed run and,
 * for most values, for each cleared stage.
 *
 * <p>A {@link Achievement.Scope#RUN} achievement uses the best single run,
 * a {@link Achievement.Scope#TOTAL} achievement adds up every completed
 * run, a {@link Achievement.Scope#STAGE} achievement uses the best cleared
 * stage and a {@link Achievement.Scope#STAGE_TOTAL} achievement adds up
 * every cleared stage. Values such as accuracy that cannot be added up are
 * not for TOTAL or STAGE_TOTAL achievements. Values never go below 0.</p>
 */
public enum Stat {
    /** 1 for every completed run. */
    RUNS_COMPLETED(Source.RECORD, true, false),
    /** Enemies defeated; also counted live during a run. */
    ENEMIES_DEFEATED(Source.LIVE, true, true),
    /** Whole seconds of active play; also counted live during a run. */
    ACTIVE_SECONDS(Source.LIVE, true, true),
    /** Final score, or 0 for a negative score. */
    SCORE(Source.RECORD, true, false),
    /** 1 when the run scored more than every earlier completed run. */
    HIGH_SCORES_BEATEN(Source.RECORD, true, false),
    /** Highest stage started; for a stage, its number. Not added up. */
    STAGE_REACHED(Source.RUN_STATS, false, true),
    /** Waves started. */
    WAVES_REACHED(Source.RUN_STATS, true, true),
    /** Player bullets fired. */
    SHOTS_FIRED(Source.RUN_STATS, true, true),
    /** Player bullets that hit an enemy ship. */
    BULLET_HITS(Source.RUN_STATS, true, true),
    /** Whole percent of bullets that hit, from 0 to 100; one run only. */
    ACCURACY_PERCENT(Source.RUN_STATS, false, true),
    /** Hits that cost the player a life. */
    TIMES_HIT(Source.RUN_STATS, true, true),
    /** Bosses defeated. */
    BOSSES_DEFEATED(Source.RUN_STATS, true, true),
    /** Items used. */
    ITEMS_USED(Source.RUN_STATS, true, true),
    /**
     * Story endings seen at least once. Not counted per run: endings belong
     * to the whole profile, so only a TOTAL achievement without
     * requirements can use it.
     */
    ENDINGS_SEEN(Source.PROFILE, true, false),
    /** 1 for a cleared stage; for stage achievements only. */
    STAGES_CLEARED(Source.STAGE, true, true);

    /** Where a value comes from. */
    private enum Source {
        /** Counted during play as well as in the completed run. */
        LIVE,
        /** A field of the run record itself. */
        RECORD,
        /** The {@link RunStats} of the run record. */
        RUN_STATS,
        /** The whole profile rather than a run. */
        PROFILE,
        /**
         * A cleared stage only, read from the {@link StageClear}s saved
         * with the run rather than from the run's own values.
         */
        STAGE
    }

    private final Source source;
    private final boolean addsUp;
    private final boolean perStage;

    Stat(final Source source, final boolean addsUp, final boolean perStage) {
        this.source = source;
        this.addsUp = addsUp;
        this.perStage = perStage;
    }

    /** @return whether the value is also known while a run is played. */
    boolean isCountedLive() {
        return this.source == Source.LIVE;
    }

    /** @return whether the value belongs to the profile, not to a run. */
    boolean isProfileWide() {
        return this.source == Source.PROFILE;
    }

    /** @return whether the value is known for a completed run. */
    boolean isPerRun() {
        return this.source != Source.PROFILE && this.source != Source.STAGE;
    }

    /** @return whether the value is known for a cleared stage. */
    boolean isPerStage() {
        return this.perStage;
    }

    /** @return whether a TOTAL achievement can add the value up. */
    boolean addsUp() {
        return this.addsUp;
    }

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
        case ACCURACY_PERCENT:
            return accuracyPercent(stats.getHits(), stats.getShotsFired());
        case TIMES_HIT: return stats.getTimesHit();
        case BOSSES_DEFEATED: return stats.getBossesDefeated();
        case ITEMS_USED: return stats.getItemsUsed();
        default: return 0;
        }
    }

    /**
     * @return whole percent of shots that hit, from 0 to 100, so 80% means
     *         at least 4 of every 5 bullets; 0 without shots.
     */
    static long accuracyPercent(final long hits, final long shots) {
        return shots <= 0 ? 0 : Math.min(100, Math.max(0, hits) * 100 / shots);
    }
}
