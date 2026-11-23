package records;

/**
 * What the records page shows for one achievement: its unlocked tiers,
 * progress towards the next tier, and "???" while a hidden one is locked.
 */
public final class AchievementStatus {
    /** Shown instead of the name and condition of a locked hidden achievement. */
    public static final String HIDDEN_TEXT = "???";

    private final Achievement achievement;
    private final int unlockedTiers;
    private final long progress;
    private final Long lastUnlockTime;

    /**
     * @param achievement
     *            Achievement shown.
     * @param unlockedTiers
     *            Tiers unlocked, from 0 to the tier count.
     * @param progress
     *            Current value of its stat: the best completed run for a
     *            RUN achievement, the whole game for a TOTAL one, and the
     *            same over the saved stage clears for STAGE and
     *            STAGE_TOTAL.
     * @param lastUnlockTime
     *            Unlock time of the highest unlocked tier, or null.
     */
    AchievementStatus(final Achievement achievement, final int unlockedTiers,
            final long progress, final Long lastUnlockTime) {
        this.achievement = achievement;
        this.unlockedTiers = unlockedTiers;
        this.progress = progress;
        this.lastUnlockTime = lastUnlockTime;
    }

    public Achievement getAchievement() { return this.achievement; }
    /** @return tiers unlocked, from 0 to the tier count. */
    public int getUnlockedTiers() { return this.unlockedTiers; }
    /** @return current value of the achievement's stat. */
    public long getProgress() { return this.progress; }
    /** @return unlock time of the highest unlocked tier, or null if locked. */
    public Long getLastUnlockTime() { return this.lastUnlockTime; }

    public boolean isUnlocked() { return this.unlockedTiers > 0; }

    /** @return whether every tier is unlocked. */
    public boolean isComplete() {
        return this.unlockedTiers == this.achievement.getTierCount();
    }

    /** @return false only for a hidden achievement with no tier unlocked. */
    public boolean isRevealed() {
        return !this.achievement.isHidden() || isUnlocked();
    }

    /**
     * @return the name with the highest unlocked tier, the plain name while
     *         locked, or "???" while hidden.
     */
    public String getTitle() {
        if (!isRevealed())
            return HIDDEN_TEXT;
        return isUnlocked() ? this.achievement.getTitle(this.unlockedTiers)
                : this.achievement.getName();
    }

    /**
     * @return the condition of the next tier (of the last one once
     *         complete), or "???" while hidden.
     */
    public String getCondition() {
        return isRevealed() ? this.achievement.getCondition(shownTier())
                : HIDDEN_TEXT;
    }

    /** @return the target of the next tier, or of the last one once complete. */
    public long getTarget() {
        return this.achievement.getTarget(shownTier());
    }

    /**
     * @return progress such as "13/20" towards the next tier, capped at the
     *         target, or an empty string while hidden. It is never below
     *         the target of the highest unlocked tier, which a stage of an
     *         abandoned run or a moment during play may have reached
     *         without a completed run saving it.
     */
    public String getProgressText() {
        if (!isRevealed())
            return "";
        return shownProgress() + "/" + getTarget();
    }

    /**
     * @return how far the progress text is towards its target, from 0 to 1,
     *         for a progress bar; 1 once complete and 0 while hidden.
     */
    public double getProgressFraction() {
        if (!isRevealed())
            return 0;
        return (double) shownProgress() / getTarget();
    }

    /** @return the progress shown, from the unlocked tier's target up to the next target. */
    private long shownProgress() {
        long shown = Math.max(this.progress, isUnlocked()
                ? this.achievement.getTarget(this.unlockedTiers) : 0);
        return Math.min(shown, getTarget());
    }

    private int shownTier() {
        return Math.min(this.unlockedTiers + 1, this.achievement.getTierCount());
    }
}
