package engine;

/** The game runs on one fixed difficulty, Easy. */
public final class GameplayConfig {
    public static final int EASY = 0;

    private GameplayConfig() {
    }

    public static int getDifficulty() {
        return EASY;
    }

    public static double getMultiplier() {
        return 1.25;
    }
}
