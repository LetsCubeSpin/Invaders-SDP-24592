package event;

/**
 * One defeated boss, published once per boss battle in Campaign and Infinite
 * mode.
 *
 * <p>Like {@link EnemyDefeatedEvent}, the event ID must stay the same if the
 * same defeat is delivered again, so a listener can drop the duplicate.</p>
 *
 * <p>The boss kind reuses {@link DefeatedEnemyType}, so a listener sees the
 * same classification here as on the matching {@link EnemyDefeatedEvent}.
 * Only boss classifications are accepted; {@link DefeatedEnemyType#UNKNOWN}
 * stays allowed for a boss kind that has no classification yet.</p>
 */
public final class BossDefeatedEvent implements IdentifiedGameplayEvent {

    private final String runId;
    private final String eventId;
    private final DefeatedEnemyType bossType;
    private final long battleDurationMillis;

    /**
     * @param runId
     *            Run in which the boss was defeated.
     * @param eventId
     *            ID of this defeat, stable across redelivery.
     * @param bossType
     *            BOSS_A, BOSS_B, BOSS_C or UNKNOWN.
     * @param battleDurationMillis
     *            Time from the start of the battle to the defeat, 0 or more.
     *            The start countdown before the boss can act is not included.
     */
    public BossDefeatedEvent(final String runId, final String eventId,
            final DefeatedEnemyType bossType, final long battleDurationMillis) {
        if (runId == null || eventId == null || runId.trim().isEmpty() || eventId.trim().isEmpty())
            throw new IllegalArgumentException("runId and eventId are required.");
        if (bossType == null)
            throw new IllegalArgumentException("bossType is required.");
        if (!isBossClassification(bossType))
            throw new IllegalArgumentException(bossType + " is not a boss.");
        if (battleDurationMillis < 0)
            throw new IllegalArgumentException("battleDurationMillis must be 0 or greater.");
        this.runId = runId;
        this.eventId = eventId;
        this.bossType = bossType;
        this.battleDurationMillis = battleDurationMillis;
    }

    private static boolean isBossClassification(final DefeatedEnemyType type) {
        return type == DefeatedEnemyType.BOSS_A
                || type == DefeatedEnemyType.BOSS_B
                || type == DefeatedEnemyType.BOSS_C
                || type == DefeatedEnemyType.UNKNOWN;
    }

    @Override
    public String getRunId() { return this.runId; }

    @Override
    public String getEventId() { return this.eventId; }

    /** @return Kind of boss defeated, never null. */
    public DefeatedEnemyType getBossType() { return this.bossType; }

    /** @return Milliseconds from the start of the battle to the defeat. */
    public long getBattleDurationMillis() { return this.battleDurationMillis; }
}
