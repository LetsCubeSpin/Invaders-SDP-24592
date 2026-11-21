package records;

/**
 * A value that achievements measure, counted for each completed run.
 *
 * <p>A {@link Achievement.Scope#RUN} achievement uses the best single run,
 * and a {@link Achievement.Scope#TOTAL} achievement adds up every completed
 * run; values such as accuracy that cannot be added up are for RUN
 * achievements only. Values never go below 0.</p>
 */
public enum Stat {
    /** 1 for every completed run. */
    RUNS_COMPLETED(Source.RECORD, true),
    /** Enemies defeated; also counted live during a run. */
    ENEMIES_DEFEATED(Source.LIVE, true),
    /** Whole seconds of active play; also counted live during a run. */
    ACTIVE_SECONDS(Source.LIVE, true),
    /** Final score, or 0 for a negative score. */
    SCORE(Source.RECORD, true),
    /** 1 when the run scored more than every earlier completed run. */
    HIGH_SCORES_BEATEN(Source.RECORD, true),
    /** Highest stage started; one run only. */
    STAGE_REACHED(Source.RUN_STATS, false),
    /** Waves started. */
    WAVES_REACHED(Source.RUN_STATS, true),
    /** Player bullets fired. */
    SHOTS_FIRED(Source.RUN_STATS, true),
    /** Player bullets that hit an enemy ship. */
    BULLET_HITS(Source.RUN_STATS, true),
    /** Whole percent of bullets that hit, from 0 to 100; one run only. */
    ACCURACY_PERCENT(Source.RUN_STATS, false),
    /** Hits that cost the player a life. */
    TIMES_HIT(Source.RUN_STATS, true),
    /** Bosses defeated. */
    BOSSES_DEFEATED(Source.RUN_STATS, true),
    /** Items used. */
    ITEMS_USED(Source.RUN_STATS, true),
    /**
     * Story endings seen at least once. Not counted per run: endings belong
     * to the whole profile, so only a TOTAL achievement without
     * requirements can use it.
     */
    ENDINGS_SEEN(Source.PROFILE, true);

    /** Where a value comes from. */
    private enum Source {
        /** Counted during play as well as in the completed run. */
        LIVE,
        /** A field every run record has, including version 1 records. */
        RECORD,
        /** {@link RunStats}, which version 1 records did not store. */
        RUN_STATS,
        /** The whole profile rather than a run. */
        PROFILE
    }

    private final Source source;
    private final boolean addsUp;

    Stat(final Source source, final boolean addsUp) {
        this.source = source;
        this.addsUp = addsUp;
    }

    /** @return whether the value is also known while a run is played. */
    boolean isCountedLive() {
        return this.source == Source.LIVE;
    }

    /** @return whether runs saved by version 1 profiles lack the value. */
    boolean needsRunStats() {
        return this.source == Source.RUN_STATS;
    }

    /** @return whether the value belongs to the profile, not to a run. */
    boolean isProfileWide() {
        return this.source == Source.PROFILE;
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
            // Whole percent, so 80% means at least 4 of every 5 bullets.
            return stats.getShotsFired() == 0 ? 0 : Math.min(100,
                    stats.getHits() * 100L / stats.getShotsFired());
        case TIMES_HIT: return stats.getTimesHit();
        case BOSSES_DEFEATED: return stats.getBossesDefeated();
        case ITEMS_USED: return stats.getItemsUsed();
        default: return 0;
        }
    }
}
