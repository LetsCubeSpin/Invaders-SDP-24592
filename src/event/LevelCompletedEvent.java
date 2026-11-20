package event;

import engine.GameplayConfig;

/**
 * Published once when a campaign stage is cleared.
 * Difficulty uses the GameplayConfig values: EASY = 0, the only difficulty.
 */

public final class LevelCompletedEvent implements GameplayEvent {
    private final int LevelId;
    private final int difficulty;

    public LevelCompletedEvent(final int LevelId, final int difficulty) {
        if (LevelId < 1)
            throw new IllegalArgumentException("levelId must be 1 or greater.");
        if (difficulty != GameplayConfig.EASY)
            throw new IllegalArgumentException("difficulty must be "
                    + GameplayConfig.EASY + " (Easy).");
        this.LevelId = LevelId;
        this.difficulty = difficulty;
    }
    /** @return 1-based campaign stage number that was completed. */
    public int getLevelId() {
        return this.LevelId;
    }

    /** @return Difficulty applied to the completed attempt. */
    public int getDifficulty() {
        return this.difficulty;
    }
}
