package story;

import engine.GameSettings;
import entity.EnemyType;
import level.EnemyCell;
import level.WaveDefinition;

/**
 * Settings of one story stage: formation size and timing, enemy type of
 * each row, number of waves, falling obstacles and boss strength.
 */
public final class StoryStage {

    /** Formation size, speed, shooting frequency and start position. */
    private final GameSettings settings;
    /** Enemy type of each row from top to bottom. */
    private final EnemyType[] rows;
    /** Number of waves on the stage. */
    private final int waveCount;
    /** Whether more falling obstacles appear (asteroid field). */
    private final boolean asteroidField;
    /** Multiplier for the boss, 1.0 means the normal boss. */
    private final double bossStrength;

    /**
     * @param settings
     *            Formation size, timing and start position.
     * @param rows
     *            Enemy type of each formation row, top to bottom.
     * @param waveCount
     *            Number of waves, 1 or more.
     * @param asteroidField
     *            Whether more falling obstacles appear.
     * @param bossStrength
     *            Boss multiplier, 1.0 or greater.
     */
    public StoryStage(final GameSettings settings, final EnemyType[] rows,
            final int waveCount, final boolean asteroidField,
            final double bossStrength) {
        if (settings == null)
            throw new IllegalArgumentException("settings must not be null.");
        if (rows == null || rows.length != settings.getFormationHeight())
            throw new IllegalArgumentException(
                    "rows must have one enemy type per formation row.");
        for (EnemyType type : rows)
            if (type == null)
                throw new IllegalArgumentException(
                        "rows must not contain null.");
        if (waveCount < 1)
            throw new IllegalArgumentException("waveCount must be 1 or more.");
        if (bossStrength < 1.0)
            throw new IllegalArgumentException(
                    "bossStrength must be 1.0 or greater.");
        this.settings = settings;
        this.rows = rows.clone();
        this.waveCount = waveCount;
        this.asteroidField = asteroidField;
        this.bossStrength = bossStrength;
    }

    /**
     * Builds one wave of this stage. The wave has the same size and start
     * position as the settings, so the enemy formation accepts it.
     *
     * @param stageNumber
     *            Stage number, used in the wave id.
     * @param waveNumber
     *            Wave number, 1 to {@link #getWaveCount()}.
     * @return New wave.
     */
    public WaveDefinition createWave(final int stageNumber,
            final int waveNumber) {
        if (stageNumber < 1)
            throw new IllegalArgumentException("stageNumber must be 1 or more.");
        if (waveNumber < 1 || waveNumber > this.waveCount)
            throw new IllegalArgumentException(
                    "waveNumber must be between 1 and " + this.waveCount + ".");
        int height = this.settings.getFormationHeight();
        int width = this.settings.getFormationWidth();
        EnemyCell[][] grid = new EnemyCell[height][width];
        for (int row = 0; row < height; row++)
            for (int column = 0; column < width; column++)
                grid[row][column] = new EnemyCell(this.rows[row].name(), 1);
        return new WaveDefinition("story-" + stageNumber + "-" + waveNumber,
                grid, this.settings.getInitialX(),
                this.settings.getInitialY(), this.settings.getBaseSpeed(),
                this.settings.getShootingFrecuency());
    }

    public GameSettings getSettings() {
        return this.settings;
    }

    public EnemyType[] getRows() {
        return this.rows.clone();
    }

    public int getWaveCount() {
        return this.waveCount;
    }

    public boolean hasAsteroidField() {
        return this.asteroidField;
    }

    public double getBossStrength() {
        return this.bossStrength;
    }
}
