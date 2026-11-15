package event;

/**
 * One use of an item by the player.
 *
 * <p>Like {@link EnemyDefeatedEvent}, the event ID must stay the same if the
 * same use is delivered again, so a listener can drop the duplicate.</p>
 */
public final class ItemUsedEvent implements GameplayEvent {

    private final String runId;
    private final String eventId;
    private final UsedItemType itemType;

    /**
     * @param runId
     *            Run in which the item was used.
     * @param eventId
     *            ID of this use, stable across redelivery.
     * @param itemType
     *            Kind of item used.
     */
    public ItemUsedEvent(final String runId, final String eventId,
            final UsedItemType itemType) {
        if (runId == null || eventId == null || runId.trim().isEmpty() || eventId.trim().isEmpty())
            throw new IllegalArgumentException("runId and eventId are required.");
        if (itemType == null)
            throw new IllegalArgumentException("itemType is required.");
        this.runId = runId;
        this.eventId = eventId;
        this.itemType = itemType;
    }

    public String getRunId() { return this.runId; }

    public String getEventId() { return this.eventId; }

    /** @return Kind of item used, never null. */
    public UsedItemType getItemType() { return this.itemType; }
}
