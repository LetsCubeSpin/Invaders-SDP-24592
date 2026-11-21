package event;

/**
 * One player bullet that hit an enemy ship, for accuracy statistics.
 *
 * <p>Sent once per bullet, whether or not the hit defeats the target: an
 * enemy with health left, a summoned minion and each hit on a boss all count.
 * Grenades and their fragments are not bullets and send no hit. Together
 * with {@link ShotFiredEvent}, accuracy is the hits divided by the bullets
 * fired.</p>
 *
 * <p>Like {@link EnemyDefeatedEvent}, the event ID must stay the same if the
 * same hit is delivered again, so a listener can drop the duplicate.</p>
 */
public final class BulletHitEvent implements IdentifiedGameplayEvent {

    private final String runId;
    private final String eventId;

    /**
     * @param runId
     *            Run in which the bullet hit.
     * @param eventId
     *            ID of this hit, stable across redelivery.
     */
    public BulletHitEvent(final String runId, final String eventId) {
        if (runId == null || eventId == null || runId.trim().isEmpty() || eventId.trim().isEmpty())
            throw new IllegalArgumentException("runId and eventId are required.");
        this.runId = runId;
        this.eventId = eventId;
    }

    @Override
    public String getRunId() { return this.runId; }

    @Override
    public String getEventId() { return this.eventId; }
}
