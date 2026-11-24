package records;

import java.util.EnumSet;
import java.util.Set;

/** Value of every {@link Stat} in one run or stage. */
final class StatTally {
    private final long[] values = new long[Stat.values().length];
    /** Values known for this run or stage; the others are missing, not 0. */
    private final Set<Stat> known;

    /** A run being played, whose live values start at 0. */
    StatTally() {
        this(EnumSet.allOf(Stat.class));
    }

    private StatTally(final Set<Stat> known) {
        this.known = known;
    }

    /** @return the values of one completed run. */
    static StatTally ofRun(final RunRecord run, final boolean beatHighScore) {
        Set<Stat> known = EnumSet.noneOf(Stat.class);
        for (Stat stat : Stat.values())
            if (stat.isPerRun())
                known.add(stat);
        StatTally tally = new StatTally(known);
        for (Stat stat : known)
            tally.values[stat.ordinal()] = stat.ofRun(run, beatHighScore);
        return tally;
    }

    /** @return an empty tally of one stage, to fill with {@link #set}. */
    static StatTally forStage() {
        Set<Stat> known = EnumSet.noneOf(Stat.class);
        for (Stat stat : Stat.values())
            if (stat.isPerStage())
                known.add(stat);
        return new StatTally(known);
    }

    long get(final Stat stat) {
        return this.values[stat.ordinal()];
    }

    void set(final Stat stat, final long value) {
        this.values[stat.ordinal()] = Math.max(0, value);
    }

    /** @return whether a value is known, rather than missing. */
    boolean has(final Stat stat) {
        return this.known.contains(stat);
    }
}
