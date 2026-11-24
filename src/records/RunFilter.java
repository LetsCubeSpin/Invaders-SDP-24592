package records;

/** Modes the run list on the records page shows, switched with K. */
public enum RunFilter {
    /** Every run. */
    ALL("All", null),
    /** Campaign runs only. */
    CAMPAIGN("Campaign", RunMode.CAMPAIGN),
    /** Infinite Mode runs only. */
    INFINITE("Infinite", RunMode.INFINITE),
    /** Story runs only. */
    STORY("Story", RunMode.STORY);

    /** Name shown to the player. */
    private final String displayName;
    /** The only mode shown, or null for every mode. */
    private final RunMode mode;

    RunFilter(final String displayName, final RunMode mode) {
        this.displayName = displayName;
        this.mode = mode;
    }

    /** @return the name shown to the player. */
    public String getDisplayName() {
        return this.displayName;
    }

    /** @return the filter K switches to, wrapping around. */
    public RunFilter next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** @return whether the run is shown. */
    public boolean matches(final RunRecord run) {
        return this.mode == null || run.getMode() == this.mode;
    }
}
