package engine;

import entity.ShipType;

/** Immutable overrides for a single Story debug battle. */
public record StoryDebugModifiers(boolean invincible, int startingLives,
        double enemyHpMultiplier, double enemySpeedMultiplier) {
    public static final int MIN_LIVES = 1;
    public static final int MAX_LIVES = 99;
    public static final double MIN_MULTIPLIER = 0.25;
    public static final double MAX_MULTIPLIER = 4.0;

    public StoryDebugModifiers {
        if (startingLives < MIN_LIVES || startingLives > MAX_LIVES) {
            throw new IllegalArgumentException("Starting lives must be between 1 and 99.");
        }
        validateMultiplier("Enemy HP", enemyHpMultiplier);
        validateMultiplier("Enemy speed", enemySpeedMultiplier);
    }

    public static StoryDebugModifiers defaults() {
        return new StoryDebugModifiers(false, ShipType.STANDARD.getMaxLives(), 1.0, 1.0);
    }

    private static void validateMultiplier(String name, double value) {
        if (!Double.isFinite(value) || value < MIN_MULTIPLIER || value > MAX_MULTIPLIER) {
            throw new IllegalArgumentException(name + " multiplier must be between 0.25 and 4.0.");
        }
    }

    /** Round to the nearest integer (half up), keeping live enemies within the positive int range. */
    public int scaleHp(int baseHp) {
        long rounded = Math.round(baseHp * enemyHpMultiplier);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, rounded));
    }
}
