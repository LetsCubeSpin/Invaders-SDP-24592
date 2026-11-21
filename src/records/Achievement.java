package records;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import story.Ending;

/**
 * An achievement of the single local player profile.
 *
 * <p>Every achievement is defined in this class and nowhere else: to add
 * one, add a constant below with {@link #define} or {@link #defineHidden}.
 * {@link RecordsService} checks it, saves it and lists it on the records
 * page in the order of the constants, with no other code to change. If it
 * needs a value no achievement counts yet, add that value to
 * {@link Stat} as well.</p>
 *
 * <p>An achievement has one to three tiers, each with a target for its
 * {@link Stat}; with more than one, they are Bronze, Silver and Gold.
 * Never change the ID of an existing achievement: saved unlock times are
 * stored under it.</p>
 */
public final class Achievement {

    /** Whether the target must be reached in one run or over the whole game. */
    public enum Scope {
        /**
         * One completed run must reach the target. Enemies defeated and
         * active seconds are also checked while the run is played.
         */
        RUN,
        /**
         * The values of every completed run add up. Abandoned runs do not
         * count, so these are only checked when a run is completed (or,
         * for {@link Stat#ENDINGS_SEEN}, when an ending is seen).
         */
        TOTAL
    }

    /** Most tiers an achievement can have. */
    public static final int MAX_TIERS = 3;
    /** Names of the tiers of an achievement that has more than one. */
    private static final String[] TIER_NAMES = { "Bronze", "Silver", "Gold" };
    /** Placeholder in a condition, replaced by the target of the tier. */
    private static final String TARGET = "{target}";
    /** Every defined achievement, in display order. */
    private static final List<Achievement> ALL = new ArrayList<Achievement>();

    public static final Achievement FIRST_RUN = define("first_run",
            "First Flight", "Complete your first run.",
            Scope.TOTAL, Stat.RUNS_COMPLETED, 1);
    public static final Achievement FIRST_ENEMY = define("first_enemy",
            "First Contact", "Defeat your first enemy.",
            Scope.RUN, Stat.ENEMIES_DEFEATED, 1);
    public static final Achievement TWENTY_ENEMIES = define("twenty_enemies",
            "Ace Pilot", "Defeat {target} enemies in one run.",
            Scope.RUN, Stat.ENEMIES_DEFEATED, 20);
    public static final Achievement SIXTY_SECONDS = define("sixty_seconds",
            "Still Flying", "Survive {target} active seconds in one run.",
            Scope.RUN, Stat.ACTIVE_SECONDS, 60);
    public static final Achievement BEAT_HIGH_SCORE = define("beat_high_score",
            "Top That", "Beat a previous run's high score.",
            Scope.RUN, Stat.HIGH_SCORES_BEATEN, 1);
    /**
     * Listed on the achievements page, but it cannot be unlocked in the game
     * until story.EndingCollection is connected (see that class).
     */
    public static final Achievement ALL_ENDINGS = define("all_endings",
            "Every Ending", "See all four story endings.",
            Scope.TOTAL, Stat.ENDINGS_SEEN, Ending.values().length);

    private final String id;
    private final String name;
    private final String condition;
    private final Scope scope;
    private final Stat stat;
    private final boolean hidden;
    private final long[] targets;

    /**
     * Creates an achievement without adding it to {@link #values()}; the
     * constants use {@link #define} instead. Tests use this directly.
     *
     * @param id
     *            Saved ID: lowercase letters, digits and underscores.
     * @param name
     *            Name shown once revealed.
     * @param condition
     *            How to reach it; {@code {target}} is replaced by the
     *            target of the tier shown.
     * @param scope
     *            One run or the whole game.
     * @param stat
     *            Value compared with the targets.
     * @param hidden
     *            Whether the name and condition show as "???" until the
     *            first tier is unlocked.
     * @param targets
     *            Target of each tier, from the first; 1 to
     *            {@link #MAX_TIERS} positive, strictly increasing values.
     */
    Achievement(final String id, final String name, final String condition,
            final Scope scope, final Stat stat, final boolean hidden,
            final long... targets) {
        if (id == null || !id.matches("[a-z0-9_]+"))
            throw new IllegalArgumentException(
                    "id must use lowercase letters, digits and underscores: " + id);
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("name must not be blank.");
        if (condition == null || condition.trim().isEmpty())
            throw new IllegalArgumentException("condition must not be blank.");
        if (scope == null || stat == null)
            throw new IllegalArgumentException("scope and stat must not be null.");
        if (scope == Scope.RUN && stat == Stat.ENDINGS_SEEN)
            throw new IllegalArgumentException(
                    "Endings are not counted per run; use Scope.TOTAL.");
        if (targets == null || targets.length < 1 || targets.length > MAX_TIERS)
            throw new IllegalArgumentException(
                    "An achievement needs 1 to " + MAX_TIERS + " targets.");
        for (int i = 0; i < targets.length; i++)
            if (targets[i] < 1 || (i > 0 && targets[i] <= targets[i - 1]))
                throw new IllegalArgumentException(
                        "Targets must be positive and strictly increasing.");
        this.id = id;
        this.name = name;
        this.condition = condition;
        this.scope = scope;
        this.stat = stat;
        this.hidden = hidden;
        this.targets = targets.clone();
    }

    /** Defines a visible achievement; see the class description. */
    private static Achievement define(final String id, final String name,
            final String condition, final Scope scope, final Stat stat,
            final long... targets) {
        return register(new Achievement(id, name, condition, scope, stat,
                false, targets));
    }

    /** Defines an achievement shown as "???" until its first tier unlocks. */
    @SuppressWarnings("unused")
    private static Achievement defineHidden(final String id, final String name,
            final String condition, final Scope scope, final Stat stat,
            final long... targets) {
        return register(new Achievement(id, name, condition, scope, stat,
                true, targets));
    }

    private static Achievement register(final Achievement achievement) {
        if (fromId(achievement.id) != null)
            throw new IllegalStateException("Duplicate achievement ID: "
                    + achievement.id);
        ALL.add(achievement);
        return achievement;
    }

    /** @return every defined achievement, in display order. */
    public static List<Achievement> values() {
        return Collections.unmodifiableList(ALL);
    }

    /** @return the defined achievement with this ID, or null. */
    public static Achievement fromId(final String id) {
        for (Achievement value : ALL)
            if (value.id.equals(id))
                return value;
        return null;
    }

    /**
     * Key of one tier's unlock time in {@link ProfileSnapshot}: the ID for
     * the first tier, otherwise the ID, '#' and the tier.
     */
    static String unlockKey(final String id, final int tier) {
        return tier == 1 ? id : id + "#" + tier;
    }

    public String getId() { return this.id; }
    public String getName() { return this.name; }
    public Scope getScope() { return this.scope; }
    public Stat getStat() { return this.stat; }
    public boolean isHidden() { return this.hidden; }
    public int getTierCount() { return this.targets.length; }

    /** @return the condition of the first tier. */
    public String getCondition() {
        return getCondition(1);
    }

    /** @return the condition of a tier, from 1, with its target filled in. */
    public String getCondition(final int tier) {
        return this.condition.replace(TARGET, Long.toString(getTarget(tier)));
    }

    /** @return the target of a tier, from 1. */
    public long getTarget(final int tier) {
        checkTier(tier);
        return this.targets[tier - 1];
    }

    /**
     * @return "Bronze", "Silver" or "Gold" for a tier, from 1, or an empty
     *         string when the achievement has a single tier.
     */
    public String getTierName(final int tier) {
        checkTier(tier);
        return this.targets.length == 1 ? "" : TIER_NAMES[tier - 1];
    }

    /** @return the name, followed by the tier name when there are tiers. */
    public String getTitle(final int tier) {
        String tierName = getTierName(tier);
        return tierName.isEmpty() ? this.name : this.name + " (" + tierName + ")";
    }

    /** @return how many tiers a value reaches, from 0 to the tier count. */
    public int tiersReached(final long value) {
        int reached = 0;
        while (reached < this.targets.length && value >= this.targets[reached])
            reached++;
        return reached;
    }

    private void checkTier(final int tier) {
        if (tier < 1 || tier > this.targets.length)
            throw new IllegalArgumentException("No tier " + tier + " in " + this.id);
    }

    @Override
    public String toString() {
        return this.id;
    }
}
