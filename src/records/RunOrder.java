package records;

import java.util.Comparator;

/** Order of the run list on the records page, switched with J. */
public enum RunOrder {
    /**
     * Highest score first. Equal scores put the earlier run first, then
     * sort by run ID.
     */
    SCORE("Score") {
        @Override
        int compare(final RunRecord first, final RunRecord second) {
            int order = Integer.compare(second.getScore(), first.getScore());
            if (order != 0)
                return order;
            order = Long.compare(first.getCompletionTimeMillis(),
                    second.getCompletionTimeMillis());
            return order != 0 ? order : first.getRunId().compareTo(second.getRunId());
        }
    },
    /**
     * Latest completed run first. Runs completed at the same time sort by
     * run ID.
     */
    NEWEST("Newest") {
        @Override
        int compare(final RunRecord first, final RunRecord second) {
            int order = Long.compare(second.getCompletionTimeMillis(),
                    first.getCompletionTimeMillis());
            return order != 0 ? order : first.getRunId().compareTo(second.getRunId());
        }
    };

    /** Name shown to the player. */
    private final String displayName;

    RunOrder(final String displayName) {
        this.displayName = displayName;
    }

    /** @return the name shown to the player. */
    public String getDisplayName() {
        return this.displayName;
    }

    /** @return the order J switches to, wrapping around. */
    public RunOrder next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Compares two runs; the run that comes first is smaller. */
    abstract int compare(RunRecord first, RunRecord second);

    /** @return this order as a comparator. */
    Comparator<RunRecord> comparator() {
        return this::compare;
    }
}
