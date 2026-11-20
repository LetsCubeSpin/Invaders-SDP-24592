package records;

import engine.LanguageManager;

/** A fixed achievement in the single local player profile. */
public enum Achievement {
    FIRST_RUN("first_run"),
    FIRST_ENEMY("first_enemy"),
    TWENTY_ENEMIES("twenty_enemies"),
    SIXTY_SECONDS("sixty_seconds"),
    BEAT_HIGH_SCORE("beat_high_score"),
    /**
     * Listed on the achievements page, but it cannot be unlocked in the game
     * until story.EndingCollection is connected (see that class).
     */
    ALL_ENDINGS("all_endings");

    private final String id;

    Achievement(final String id) {
        this.id = id;
    }

    public String getId() { return this.id; }
    public String getName() { return LanguageManager.get("achievement." + this.id + ".name"); }
    public String getCondition() { return LanguageManager.get("achievement." + this.id + ".condition"); }

    public static Achievement fromId(final String id) {
        for (Achievement value : values())
            if (value.id.equals(id))
                return value;
        return null;
    }
}
