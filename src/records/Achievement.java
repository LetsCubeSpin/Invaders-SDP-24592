package records;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import story.Ending;

/**
 * An achievement of the single local player profile.
 *
 * <p>Every achievement is defined in this class and nowhere else: to add
 * one, add a constant below with {@code add(define(...)...)}.
 * {@link RecordsService} checks it, saves it and lists it on the records
 * page in the order of the constants, with no other code to change. If it
 * needs a value no achievement counts yet, add that value to {@link Stat}
 * as well.</p>
 *
 * <p>A definition names the {@link Stat} it measures, whether one stage,
 * one run or the whole game counts ({@link Scope}), and one to three
 * targets; with more than one, they are the Bronze, Silver and Gold tiers.
 * Requirements ({@link Builder#atLeast}, {@link Builder#atMost}) limit
 * which stages or runs count, for conditions such as "without losing a
 * life" or "with 80% accuracy". A hidden achievement shows as "???" until
 * it unlocks.</p>
 *
 * <p>Never change the ID of an existing achievement: saved unlock times are
 * stored under it.</p>
 */
public final class Achievement {

    /** Whether the target must be reached in one stage, one run or the whole game. */
    public enum Scope {
        /**
         * One cleared Campaign or Story stage must reach the target, with
         * the values counted from the start of that stage. Checked as soon
         * as the stage is cleared, so it unlocks even if the run is
         * abandoned later. Progress shows the best stage of the completed
         * runs.
         */
        STAGE,
        /**
         * The values of every cleared stage that meets the requirements
         * add up over the whole game. Stage clears are saved with their
         * run, so stages of abandoned runs never count, and these are
         * checked when a run is completed.
         */
        STAGE_TOTAL,
        /**
         * One completed run must reach the target. Achievements that only
         * use enemies defeated and active seconds are also checked while
         * the run is played.
         */
        RUN,
        /**
         * The values of every completed run that meets the requirements
         * add up. Abandoned runs never count, so these are only checked
         * when a run is completed (or, for {@link Stat#ENDINGS_SEEN}, when
         * an ending is seen).
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

    public static final Achievement FIRST_RUN = add(define("first_run",
            "First Flight", "Complete your first run.")
            .total(Stat.RUNS_COMPLETED, 1));
    public static final Achievement FIRST_ENEMY = add(define("first_enemy",
            "First Contact", "Defeat your first enemy.")
            .run(Stat.ENEMIES_DEFEATED, 1));
    public static final Achievement TWENTY_ENEMIES = add(define("twenty_enemies",
            "Ace Pilot", "Defeat {target} enemies in one run.")
            .run(Stat.ENEMIES_DEFEATED, 20));
    public static final Achievement SIXTY_SECONDS = add(define("sixty_seconds",
            "Still Flying", "Survive {target} active seconds in one run.")
            .run(Stat.ACTIVE_SECONDS, 60));
    public static final Achievement BEAT_HIGH_SCORE = add(define("beat_high_score",
            "Top That", "Beat a previous run's high score.")
            .run(Stat.HIGH_SCORES_BEATEN, 1));
    /**
     * Listed on the achievements page, but it cannot be unlocked in the game
     * until story.EndingCollection is connected (see that class).
     */
    public static final Achievement ALL_ENDINGS = add(define("all_endings",
            "Every Ending", "See all four story endings.")
            .total(Stat.ENDINGS_SEEN, Ending.values().length));
    /** Tiers: enemies defeated over every completed run. */
    public static final Achievement EXTERMINATOR = add(define("exterminator",
            "Exterminator", "Defeat {target} enemies in total.")
            .total(Stat.ENEMIES_DEFEATED, 100, 500, 1000));
    /** A requirement: accuracy only counts in a run with enough shots. */
    public static final Achievement SHARPSHOOTER = add(define("sharpshooter",
            "Sharpshooter", "Finish a run with {target}% accuracy (50+ shots).")
            .run(Stat.ACCURACY_PERCENT, 50)
            .atLeast(Stat.SHOTS_FIRED, 50));
    /** Hidden, for one stage: a cleared stage that never cost a life. */
    public static final Achievement UNTOUCHABLE = add(define("untouchable",
            "Untouchable", "Clear a stage without losing a life.")
            .stage(Stat.STAGES_CLEARED, 1)
            .atMost(Stat.TIMES_HIT, 0)
            .hidden());
    /** Tiers over every cleared stage of completed runs, with a requirement. */
    public static final Achievement CLEAN_SWEEP = add(define("clean_sweep",
            "Clean Sweep", "Clear {target} stages without losing a life.")
            .stageTotal(Stat.STAGES_CLEARED, 10, 25, 50)
            .atMost(Stat.TIMES_HIT, 0));

    private final String id;
    private final String name;
    private final String condition;
    private final Scope scope;
    private final Stat stat;
    private final boolean hidden;
    private final long[] targets;
    private final List<Requirement> requirements;

    private Achievement(final Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.condition = builder.condition;
        this.scope = builder.scope;
        this.stat = builder.stat;
        this.hidden = builder.hidden;
        this.targets = builder.targets.clone();
        this.requirements = Collections.unmodifiableList(
                new ArrayList<Requirement>(builder.requirements));
    }

    /**
     * Starts a definition. The constants pass it to {@link #add}; tests call
     * {@link Builder#build()} to get an achievement the game does not list.
     *
     * @param id
     *            Saved ID: lowercase letters, digits and underscores.
     * @param name
     *            Name shown once revealed.
     * @param condition
     *            How to reach it; {@code {target}} is replaced by the target
     *            of the tier shown.
     * @return a definition to finish with {@link Builder#stage},
     *         {@link Builder#stageTotal}, {@link Builder#run} or
     *         {@link Builder#total}.
     */
    static Builder define(final String id, final String name,
            final String condition) {
        return new Builder(id, name, condition);
    }

    /** Builds a definition and lists it in the game. */
    private static Achievement add(final Builder builder) {
        Achievement achievement = builder.build();
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

    /**
     * @param tier
     *            Tier, from 1.
     * @return the condition of the tier, with its target filled in.
     */
    public String getCondition(final int tier) {
        return this.condition.replace(TARGET, Long.toString(getTarget(tier)));
    }

    /**
     * @param tier
     *            Tier, from 1.
     * @return the target of the tier.
     */
    public long getTarget(final int tier) {
        checkTier(tier);
        return this.targets[tier - 1];
    }

    /**
     * @param tier
     *            Tier, from 1.
     * @return "Bronze", "Silver" or "Gold", or an empty string when the
     *         achievement has a single tier.
     */
    public String getTierName(final int tier) {
        checkTier(tier);
        return this.targets.length == 1 ? "" : TIER_NAMES[tier - 1];
    }

    /**
     * @param tier
     *            Tier, from 1.
     * @return the name, followed by the tier name when there are tiers.
     */
    public String getTitle(final int tier) {
        String tierName = getTierName(tier);
        return tierName.isEmpty() ? this.name : this.name + " (" + tierName + ")";
    }

    /**
     * @param value
     *            Value of the achievement's stat.
     * @return how many tiers the value reaches, from 0 to the tier count.
     */
    public int tiersReached(final long value) {
        int reached = 0;
        while (reached < this.targets.length && value >= this.targets[reached])
            reached++;
        return reached;
    }

    /**
     * @return whether a run or stage counts towards this achievement: it
     *         has the values the achievement uses and meets every
     *         requirement. A version 1 record has no {@link RunStats}, so it
     *         never meets a requirement on them, even "at most 0".
     */
    boolean counts(final StatTally run) {
        if (!run.has(this.stat))
            return false;
        for (Requirement requirement : this.requirements)
            if (!requirement.isMetBy(run))
                return false;
        return true;
    }

    /**
     * @return whether this is checked while a run is played: a RUN
     *         achievement whose stat and requirements are all counted live.
     */
    boolean isCheckedLive() {
        if (this.scope != Scope.RUN || !this.stat.isCountedLive())
            return false;
        for (Requirement requirement : this.requirements)
            if (!requirement.stat.isCountedLive())
                return false;
        return true;
    }

    private void checkTier(final int tier) {
        if (tier < 1 || tier > this.targets.length)
            throw new IllegalArgumentException("No tier " + tier + " in " + this.id);
    }

    @Override
    public String toString() {
        return this.id;
    }

    /** A limit a run must meet to count towards an achievement. */
    private static final class Requirement {
        private final Stat stat;
        private final long limit;
        /** True for "at most the limit", false for "at least". */
        private final boolean atMost;

        Requirement(final Stat stat, final long limit, final boolean atMost) {
            if (stat == null || stat.isProfileWide())
                throw new IllegalArgumentException(
                        "A requirement needs a stat counted per run.");
            if (limit < 0)
                throw new IllegalArgumentException("limit must not be negative.");
            this.stat = stat;
            this.limit = limit;
            this.atMost = atMost;
        }

        boolean isMetBy(final StatTally run) {
            if (!run.has(this.stat))
                return false;
            long value = run.get(this.stat);
            return this.atMost ? value <= this.limit : value >= this.limit;
        }
    }

    /** Definition of an achievement; see {@link Achievement#define}. */
    static final class Builder {
        private final String id;
        private final String name;
        private final String condition;
        private Scope scope;
        private Stat stat;
        private long[] targets;
        private boolean hidden;
        private final List<Requirement> requirements = new ArrayList<Requirement>();

        private Builder(final String id, final String name, final String condition) {
            this.id = id;
            this.name = name;
            this.condition = condition;
        }

        /**
         * One cleared stage must reach the targets.
         *
         * @param stat
         *            Value of the stage compared with the targets.
         * @param targets
         *            Target of each tier, from the first: 1 to
         *            {@link #MAX_TIERS} positive, increasing values.
         * @return this definition.
         */
        Builder stage(final Stat stat, final long... targets) {
            return measure(Scope.STAGE, stat, targets);
        }

        /**
         * Every cleared stage of the completed runs adds up towards the
         * targets.
         *
         * @param stat
         *            Value of each stage compared with the targets.
         * @param targets
         *            Target of each tier, from the first: 1 to
         *            {@link #MAX_TIERS} positive, increasing values.
         * @return this definition.
         */
        Builder stageTotal(final Stat stat, final long... targets) {
            return measure(Scope.STAGE_TOTAL, stat, targets);
        }

        /**
         * One completed run must reach the targets.
         *
         * @param stat
         *            Value compared with the targets.
         * @param targets
         *            Target of each tier, from the first: 1 to
         *            {@link #MAX_TIERS} positive, increasing values.
         * @return this definition.
         */
        Builder run(final Stat stat, final long... targets) {
            return measure(Scope.RUN, stat, targets);
        }

        /**
         * Every completed run adds up towards the targets.
         *
         * @param stat
         *            Value compared with the targets.
         * @param targets
         *            Target of each tier, from the first: 1 to
         *            {@link #MAX_TIERS} positive, increasing values.
         * @return this definition.
         */
        Builder total(final Stat stat, final long... targets) {
            return measure(Scope.TOTAL, stat, targets);
        }

        /** Only stages or runs with at least this value count. */
        Builder atLeast(final Stat requirementStat, final long limit) {
            this.requirements.add(new Requirement(requirementStat, limit, false));
            return this;
        }

        /** Only stages or runs with at most this value count. */
        Builder atMost(final Stat requirementStat, final long limit) {
            this.requirements.add(new Requirement(requirementStat, limit, true));
            return this;
        }

        /** Shows the name and condition as "???" until the first tier unlocks. */
        Builder hidden() {
            this.hidden = true;
            return this;
        }

        private Builder measure(final Scope measuredScope, final Stat measuredStat,
                final long... measuredTargets) {
            this.scope = measuredScope;
            this.stat = measuredStat;
            this.targets = measuredTargets;
            return this;
        }

        /** @return the achievement, without listing it in the game. */
        Achievement build() {
            if (this.id == null || !this.id.matches("[a-z0-9_]+"))
                throw new IllegalArgumentException(
                        "id must use lowercase letters, digits and underscores: " + this.id);
            if (this.name == null || this.name.trim().isEmpty())
                throw new IllegalArgumentException("name must not be blank.");
            if (this.condition == null || this.condition.trim().isEmpty())
                throw new IllegalArgumentException("condition must not be blank.");
            if (this.scope == null || this.stat == null)
                throw new IllegalArgumentException(
                        "Call run or total with a stat: " + this.id);
            if (this.stat.isProfileWide() && (this.scope == Scope.RUN
                    || !this.requirements.isEmpty()))
                throw new IllegalArgumentException(this.stat
                        + " is not counted per run; use total without requirements.");
            boolean stages = this.scope == Scope.STAGE
                    || this.scope == Scope.STAGE_TOTAL;
            if ((this.scope == Scope.TOTAL || this.scope == Scope.STAGE_TOTAL)
                    && !this.stat.addsUp())
                throw new IllegalArgumentException(this.stat
                        + " cannot be added up: " + this.id);
            for (Stat used : usedStats())
                if (stages ? !used.isPerStage()
                        : !used.isPerRun() && !used.isProfileWide())
                    throw new IllegalArgumentException(used
                            + " is not counted per " + (stages ? "stage" : "run")
                            + ": " + this.id);
            if (this.targets == null || this.targets.length < 1
                    || this.targets.length > MAX_TIERS)
                throw new IllegalArgumentException(
                        "An achievement needs 1 to " + MAX_TIERS + " targets.");
            for (int i = 0; i < this.targets.length; i++)
                if (this.targets[i] < 1
                        || (i > 0 && this.targets[i] <= this.targets[i - 1]))
                    throw new IllegalArgumentException(
                            "Targets must be positive and strictly increasing.");
            return new Achievement(this);
        }

        /** @return the measured stat and the stats of the requirements. */
        private List<Stat> usedStats() {
            List<Stat> used = new ArrayList<Stat>();
            used.add(this.stat);
            for (Requirement requirement : this.requirements)
                used.add(requirement.stat);
            return used;
        }
    }
}
