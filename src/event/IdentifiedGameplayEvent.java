package event;

/**
 * A gameplay event that names its run and carries an ID for duplicate
 * detection, the contract {@link EnemyDefeatedEvent} introduced.
 *
 * <p>The event ID is given once, when the action happens, and travels on the
 * event unchanged, so the same action delivered again keeps the same ID. A
 * listener that must count each action once wraps itself in a
 * {@link DeduplicatingListener}.</p>
 */
public interface IdentifiedGameplayEvent extends GameplayEvent {

    /** @return Run the action happened in, never blank. */
    String getRunId();

    /** @return ID of the action, never blank and unique within the run. */
    String getEventId();
}
