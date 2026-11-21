package records;

/** Mutable value of every {@link Stat}, kept by {@link RecordsService}. */
final class StatTally {
    private final long[] values = new long[Stat.values().length];

    /** @return the values of one completed run. */
    static StatTally ofRun(final RunRecord run, final boolean beatHighScore) {
        StatTally tally = new StatTally();
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

    /** Adds every value of another tally, stopping at the largest long. */
    void add(final StatTally other) {
        for (int i = 0; i < this.values.length; i++)
            this.values[i] = Long.MAX_VALUE - this.values[i] < other.values[i]
                    ? Long.MAX_VALUE : this.values[i] + other.values[i];
    }

    /** Keeps the larger of each value. */
    void max(final StatTally other) {
        for (int i = 0; i < this.values.length; i++)
            this.values[i] = Math.max(this.values[i], other.values[i]);
    }
}
