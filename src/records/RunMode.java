package records;

/**
 * Game mode a run was played in. The constant name is what the profile
 * stores, so renaming a constant breaks saved records.
 */
public enum RunMode {
	/** Campaign stages played in order. */
	CAMPAIGN("Campaign"),
	/** Endless waves with no stages. */
	INFINITE("Infinite"),
	/** The seven story stages with their questions and ending. */
	STORY("Story");

	/** Name shown to the player. */
	private final String displayName;

	RunMode(final String displayName) {
		this.displayName = displayName;
	}

	/** @return the name shown to the player. */
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * @return whether runs in this mode have stages and waves. When false,
	 *         the stage and wave counts of the run are always 0.
	 */
	public boolean hasStages() {
		return this == CAMPAIGN || this == STORY;
	}
}
