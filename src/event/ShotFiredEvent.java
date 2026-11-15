package event;

/**
 * Player bullets fired in one frame, for accuracy statistics.
 *
 * <p>The first volley of a shot and each later volley of a rapid-fire burst
 * are separate events. The bullet counts of all events add up to the
 * {@code bulletsShot} total the screens keep, so accuracy computed from these
 * events matches the score screen.</p>
 *
 * <p>Like {@link EnemyDefeatedEvent}, the event ID must stay the same if the
 * same shot is delivered again, so a listener can drop the duplicate.</p>
 */
public final class ShotFiredEvent implements IdentifiedGameplayEvent {

    private final String runId;
    private final String eventId;
    private final int bulletCount;

    /**
     * @param runId
     *            Run in which the shot was fired.
     * @param eventId
     *            ID of this shot, stable across redelivery.
     * @param bulletCount
     *            Bullets fired by this shot, 1 or more.
     */
    public ShotFiredEvent(final String runId, final String eventId, final int bulletCount) {
        if (runId == null || eventId == null || runId.trim().isEmpty() || eventId.trim().isEmpty())
            throw new IllegalArgumentException("runId and eventId are required.");
        if (bulletCount < 1)
            throw new IllegalArgumentException("bulletCount must be 1 or greater.");
        this.runId = runId;
        this.eventId = eventId;
        this.bulletCount = bulletCount;
    }

    @Override
    public String getRunId() { return this.runId; }

    @Override
    public String getEventId() { return this.eventId; }

    /** @return Bullets fired by this shot. */
    public int getBulletCount() { return this.bulletCount; }
}
