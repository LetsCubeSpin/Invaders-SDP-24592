package records;

/** A fixed achievement in the single local player profile. */
public enum Achievement {
    FIRST_RUN("first_run", "First Flight", "Complete your first run."),
    FIRST_ENEMY("first_enemy", "First Contact", "Defeat your first enemy."),
    TWENTY_ENEMIES("twenty_enemies", "Ace Pilot", "Defeat 20 enemies in one run."),
    SIXTY_SECONDS("sixty_seconds", "Still Flying", "Survive 60 active seconds in one run."),
    BEAT_HIGH_SCORE("beat_high_score", "Top That", "Beat a previous run's high score."),
    /**
     * Listed on the achievements page, but it cannot be unlocked in the game
     * until story.EndingCollection is connected (see that class).
     */
    ALL_ENDINGS("all_endings", "Every Ending", "See all four story endings.");

    private final String id;
    private final String name;
    private final String condition;

    Achievement(final String id, final String name, final String condition) {
        this.id = id;
        this.name = name;
        this.condition = condition;
    }

    public String getId() { return this.id; }
    public String getName() { return this.name; }
    public String getCondition() { return this.condition; }

    public static Achievement fromId(final String id) {
        for (Achievement value : values())
            if (value.id.equals(id))
                return value;
        return null;
    }
}
