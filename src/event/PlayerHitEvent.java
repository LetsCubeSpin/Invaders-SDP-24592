package event;

/**
 * One hit that got through to the player and cost at least one life.
 *
 * <p>A hit absorbed by a shield, or ignored during the invulnerability after
 * an earlier hit, is not a {@code PlayerHitEvent}: publishers send this only
 * after the life has been taken.</p>
 *
 * <p>Like {@link EnemyDefeatedEvent}, the event ID must stay the same if the
 * same hit is delivered again, so a listener can drop the duplicate.</p>
 *
 * <p>Use this event for records, achievements and statistics. In Campaign
 * mode the same hit also produces {@code PlayerStatusEvent.Type.DAMAGE_TAKEN},
 * which is meant for the HUD and sounds; it has no IDs or hit source and is
 * not sent in Infinite mode. Subscribe to one of the two, never both, or each
 * hit is counted twice.</p>
 */
public final class PlayerHitEvent implements IdentifiedGameplayEvent {

    private final String runId;
    private final String eventId;
    private final PlayerHitSource source;
    private final int damage;
    private final int livesRemaining;

    /**
     * @param runId
     *            Run in which the player was hit.
     * @param eventId
     *            ID of this hit, stable across redelivery.
     * @param source
     *            What hit the player.
     * @param damage
     *            Lives taken by the hit, 1 or more.
     * @param livesRemaining
     *            Lives left after the hit, 0 or more.
     */
    public PlayerHitEvent(final String runId, final String eventId,
            final PlayerHitSource source, final int damage, final int livesRemaining) {
        if (runId == null || eventId == null || runId.trim().isEmpty() || eventId.trim().isEmpty())
            throw new IllegalArgumentException("runId and eventId are required.");
        if (source == null)
            throw new IllegalArgumentException("source is required.");
        if (damage < 1)
            throw new IllegalArgumentException("damage must be 1 or greater.");
        if (livesRemaining < 0)
            throw new IllegalArgumentException("livesRemaining must be 0 or greater.");
        this.runId = runId;
        this.eventId = eventId;
        this.source = source;
        this.damage = damage;
        this.livesRemaining = livesRemaining;
    }

    @Override
    public String getRunId() { return this.runId; }

    @Override
    public String getEventId() { return this.eventId; }

    /** @return What hit the player, never null. */
    public PlayerHitSource getSource() { return this.source; }

    /** @return Lives taken by the hit. */
    public int getDamage() { return this.damage; }

    /** @return Lives left after the hit; 0 means the run is over. */
    public int getLivesRemaining() { return this.livesRemaining; }
}
