package records;

/** Value of every {@link Stat} in one run, completed or being played. */
final class StatTally {
    private final long[] values = new long[Stat.values().length];
    /** Whether the run has {@link RunStats}; version 1 records do not. */
    private final boolean hasRunStats;

    /** A run being played, whose live values start at 0. */
    StatTally() {
        this(true);
    }

    private StatTally(final boolean hasRunStats) {
        this.hasRunStats = hasRunStats;
    }

    /** @return the values of one completed run. */
    static StatTally ofRun(final RunRecord run, final boolean beatHighScore) {
        StatTally tally = new StatTally(run.getMode() != RunMode.UNKNOWN);
        for (Stat stat : Stat.values())
            tally.values[stat.ordinal()] = stat.ofRun(run, beatHighScore);
        return tally;
    }

    long get(final Stat stat) {
        return this.values[stat.ordinal()];
    }

    void set(final Stat stat, final long value) {
        this.values[stat.ordinal()] = Math.max(0, value);
    }

    /** @return whether a value is known for this run, rather than missing. */
    boolean has(final Stat stat) {
        return this.hasRunStats || !stat.needsRunStats();
    }
}
