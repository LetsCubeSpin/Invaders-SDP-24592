package event;

import story.Ending;

/**
 * Published every time the player sees a story ending.
 *
 * Subscribe with
 * {@code publisher.subscribe(EndingReachedEvent.class, listener)}.
 *
 * <p>Published by {@link story.EndingCollection} when a story run reaches
 * its ending (see that class).</p>
 */
public final class EndingReachedEvent implements GameplayEvent {
    private final Ending ending;
    private final boolean firstTime;

    /**
     * @param ending
     *            Ending the player saw.
     * @param firstTime
     *            Whether this is the first time the ending was seen.
     */
    public EndingReachedEvent(final Ending ending, final boolean firstTime) {
        if (ending == null)
            throw new IllegalArgumentException("ending must not be null.");
        this.ending = ending;
        this.firstTime = firstTime;
    }

    /** @return Ending the player saw, never null. */
    public Ending getEnding() {
        return this.ending;
    }

    /** @return Whether this is the first time the ending was seen. */
    public boolean isFirstTime() {
        return this.firstTime;
    }
}
