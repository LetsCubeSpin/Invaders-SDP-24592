package records;

/**
 * Values of one cleared stage, counted from the moment the stage started.
 * {@link RunSession} creates one when a stage is cleared and keeps it with
 * the run, so it is saved only if the run is completed.
 */
public final class StageClear {
    /** Number of saved values; see {@link #toValues()}. */
    static final int VALUE_COUNT = 9;

    private final int stage;
    private final int enemiesDefeated;
    private final int waves;
    private final int shotsFired;
    private final int hits;
    private final int timesHit;
    private final int bossesDefeated;
    private final int itemsUsed;
    private final long activeSeconds;

    /**
     * @param stage
     *            1-based stage number.
     * @param enemiesDefeated
     *            Enemies defeated in the stage.
     * @param waves
     *            Waves started in the stage.
     * @param shotsFired
     *            Player bullets fired in the stage.
     * @param hits
     *            Player bullets that hit an enemy ship in the stage.
     * @param timesHit
     *            Hits that cost the player a life in the stage.
     * @param bossesDefeated
     *            Bosses defeated in the stage.
     * @param itemsUsed
     *            Items used in the stage.
     * @param activeSeconds
     *            Whole seconds of active play in the stage.
     */
    public StageClear(final int stage, final int enemiesDefeated, final int waves,
            final int shotsFired, final int hits, final int timesHit,
            final int bossesDefeated, final int itemsUsed, final long activeSeconds) {
        if (stage < 1)
            throw new IllegalArgumentException("stage must be 1 or greater.");
        if (enemiesDefeated < 0 || waves < 0 || shotsFired < 0 || hits < 0
                || timesHit < 0 || bossesDefeated < 0 || itemsUsed < 0
                || activeSeconds < 0)
            throw new IllegalArgumentException("Stage values must not be negative.");
        this.stage = stage;
        this.enemiesDefeated = enemiesDefeated;
        this.waves = waves;
        this.shotsFired = shotsFired;
        this.hits = hits;
        this.timesHit = timesHit;
        this.bossesDefeated = bossesDefeated;
        this.itemsUsed = itemsUsed;
        this.activeSeconds = activeSeconds;
    }

    public int getStage() { return this.stage; }
    public int getEnemiesDefeated() { return this.enemiesDefeated; }
    public int getWaves() { return this.waves; }
    public int getShotsFired() { return this.shotsFired; }
    public int getHits() { return this.hits; }
    public int getTimesHit() { return this.timesHit; }
    public int getBossesDefeated() { return this.bossesDefeated; }
    public int getItemsUsed() { return this.itemsUsed; }
    public long getActiveSeconds() { return this.activeSeconds; }

    /** @return the values achievements compare, for this stage. */
    StatTally toTally() {
        StatTally tally = StatTally.forStage();
        tally.set(Stat.STAGES_CLEARED, 1);
        tally.set(Stat.STAGE_REACHED, this.stage);
        tally.set(Stat.ENEMIES_DEFEATED, this.enemiesDefeated);
        tally.set(Stat.WAVES_REACHED, this.waves);
        tally.set(Stat.SHOTS_FIRED, this.shotsFired);
        tally.set(Stat.BULLET_HITS, this.hits);
        tally.set(Stat.ACCURACY_PERCENT,
                Stat.accuracyPercent(this.hits, this.shotsFired));
        tally.set(Stat.TIMES_HIT, this.timesHit);
        tally.set(Stat.BOSSES_DEFEATED, this.bossesDefeated);
        tally.set(Stat.ITEMS_USED, this.itemsUsed);
        tally.set(Stat.ACTIVE_SECONDS, this.activeSeconds);
        return tally;
    }

    /**
     * @return the values in the order the profile saves them: stage,
     *         enemies, waves, shots, hits, times hit, bosses, items and
     *         active seconds.
     */
    long[] toValues() {
        return new long[] { this.stage, this.enemiesDefeated, this.waves,
                this.shotsFired, this.hits, this.timesHit, this.bossesDefeated,
                this.itemsUsed, this.activeSeconds };
    }

    /** Reverses {@link #toValues()}. */
    static StageClear fromValues(final long[] values) {
        if (values.length != VALUE_COUNT)
            throw new IllegalArgumentException("A stage has "
                    + VALUE_COUNT + " values.");
        for (int i = 0; i < VALUE_COUNT - 1; i++)
            if (values[i] > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Stage value is too large.");
        return new StageClear((int) values[0], (int) values[1], (int) values[2],
                (int) values[3], (int) values[4], (int) values[5], (int) values[6],
                (int) values[7], values[8]);
    }
}
