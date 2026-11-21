package records;

/** One tier of an achievement that was just unlocked, for notices and results. */
public final class AchievementUnlock {
    private final Achievement achievement;
    private final int tier;

    /**
     * @param achievement
     *            Unlocked achievement.
     * @param tier
     *            Unlocked tier, from 1.
     */
    public AchievementUnlock(final Achievement achievement, final int tier) {
        if (achievement == null)
            throw new IllegalArgumentException("achievement must not be null.");
        achievement.getTarget(tier);
        this.achievement = achievement;
        this.tier = tier;
    }

    public Achievement getAchievement() { return this.achievement; }
    public int getTier() { return this.tier; }

    /** @return the name, with the tier name when there are tiers. */
    public String getTitle() {
        return this.achievement.getTitle(this.tier);
    }

    /** @return the condition met for this tier. */
    public String getCondition() {
        return this.achievement.getCondition(this.tier);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof AchievementUnlock))
            return false;
        AchievementUnlock unlock = (AchievementUnlock) other;
        return this.achievement == unlock.achievement && this.tier == unlock.tier;
    }

    @Override
    public int hashCode() {
        return this.achievement.hashCode() * 31 + this.tier;
    }

    @Override
    public String toString() {
        return Achievement.unlockKey(this.achievement.getId(), this.tier);
    }
}
