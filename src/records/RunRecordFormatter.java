package records;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Produces a stable, human-readable representation of a completed run.
 */
public final class RunRecordFormatter {

	/** Shown for a value the run's mode does not have. */
	static final String NOT_APPLICABLE = "-";

	/** Constructor, not called. */
	private RunRecordFormatter() {
	}

	/**
	 * Lines describing the mode and play statistics of a run, for the
	 * records page. A mode without stages shows "-" for the stage and waves.
	 *
	 * @param record
	 *            Record to describe.
	 * @return the lines, in display order.
	 */
	public static List<String> detailLines(final RunRecord record) {
		if (record == null)
			throw new IllegalArgumentException("record must not be null.");
		List<String> lines = new ArrayList<String>();
		lines.add("Mode: " + record.getMode().getDisplayName());
		RunStats stats = record.getStats();
		boolean stages = record.getMode().hasStages();
		lines.add("Stage reached: " + (stages
				? Integer.toString(stats.getStageReached()) : NOT_APPLICABLE));
		lines.add("Waves reached: " + (stages || stats.getWavesReached() > 0
				? Integer.toString(stats.getWavesReached()) : NOT_APPLICABLE));
		lines.add("Shots fired: " + stats.getShotsFired());
		lines.add("Hits: " + stats.getHits());
		lines.add("Accuracy: " + formatAccuracy(stats));
		lines.add("Times hit: " + stats.getTimesHit());
		lines.add("Bosses defeated: " + stats.getBossesDefeated());
		lines.add("Items used: " + stats.getItemsUsed());
		return lines;
	}

	/** @return accuracy as a percentage with one decimal, such as 25.0%. */
	static String formatAccuracy(final RunStats stats) {
		return String.format(Locale.ROOT, "%.1f%%", stats.getAccuracy() * 100);
	}

	/**
	 * Formats every field of the record.
	 *
	 * @param record
	 *            Record to format.
	 * @return a multi-line summary.
	 */
	public static String format(final RunRecord record) {
		if (record == null)
			throw new IllegalArgumentException("record must not be null.");

		String lineSeparator = System.getProperty("line.separator");
		String seconds = String.format(Locale.ROOT, "%.3f",
				record.getActiveDurationMillis() / 1000.0);
		RunStats stats = record.getStats();
		return "Run ID: " + record.getRunId() + lineSeparator
				+ "Score: " + record.getScore() + lineSeparator
				+ "Enemies defeated: " + record.getEnemiesDefeated()
				+ lineSeparator + "Active duration: "
				+ record.getActiveDurationMillis() + " ms (" + seconds
				+ " s)" + lineSeparator + "Completion time: "
				+ record.getCompletionTimeMillis() + lineSeparator
				+ "Mode: " + record.getMode().getDisplayName() + lineSeparator
				+ "Stage reached: " + stats.getStageReached() + lineSeparator
				+ "Waves reached: " + stats.getWavesReached() + lineSeparator
				+ "Shots fired: " + stats.getShotsFired() + lineSeparator
				+ "Hits: " + stats.getHits() + lineSeparator
				+ "Accuracy: " + formatAccuracy(stats) + lineSeparator
				+ "Times hit: " + stats.getTimesHit() + lineSeparator
				+ "Bosses defeated: " + stats.getBossesDefeated()
				+ lineSeparator + "Items used: " + stats.getItemsUsed();
	}
}
