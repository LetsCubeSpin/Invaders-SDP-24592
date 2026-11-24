package engine;

/**
 * The game runs on one fixed difficulty, Easy. Story mode is easier still:
 * it has its own multiplier and gives its bosses more time between attacks.
 */
public final class GameplayConfig {
    public static final int EASY = 0;

    private GameplayConfig() {
    }

    public static int getDifficulty() {
        return EASY;
    }

    /**
     * @return Multiplier of the enemy movement and shooting intervals in
     *         every mode except story mode. Larger values make enemies
     *         slower and less frequent shooters.
     */
    public static double getMultiplier() {
        return 1.25;
    }

    /**
     * @return Multiplier of the enemy movement and shooting intervals in
     *         story mode, about 20% easier than {@link #getMultiplier()}.
     */
    public static double getStoryMultiplier() {
        return 1.5;
    }

    /**
     * @return How much longer story bosses wait between normal attacks than
     *         the same bosses outside story mode.
     */
    public static double getStoryBossIntervalScale() {
        return 1.2;
    }
}
