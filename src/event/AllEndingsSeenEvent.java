package event;

/**
 * Published once, when the player sees the last ending they had not seen,
 * so that every ending has now been seen.
 *
 * Subscribe with
 * {@code publisher.subscribe(AllEndingsSeenEvent.class, listener)}.
 *
 * <p>Not published in the game yet; it is ready to use once
 * {@link story.EndingCollection} is connected (see that class).</p>
 */
public final class AllEndingsSeenEvent implements GameplayEvent {
}
