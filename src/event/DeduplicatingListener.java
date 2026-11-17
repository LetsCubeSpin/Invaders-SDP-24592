package event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Passes each action to the wrapped listener once, dropping any later
 * delivery of the same run ID and event ID. This is the duplicate check the
 * enemy defeat listeners each keep for themselves, made reusable.
 *
 * <pre>{@code
 * publisher.subscribe(PlayerHitEvent.class,
 *         new DeduplicatingListener<PlayerHitEvent>(this::onPlayerHit));
 * }</pre>
 *
 * <p>Subscribe the wrapper, not the wrapped listener, and keep the wrapper
 * to unsubscribe it later. Seen IDs are kept for the wrapper's lifetime, so
 * create one per screen or run subscription.</p>
 *
 * @param <T> Event type handled by the wrapped listener
 */
public final class DeduplicatingListener<T extends IdentifiedGameplayEvent>
        implements GameplayEventListener<T> {

    private final GameplayEventListener<T> delegate;
    /** Event IDs already handled, per run. */
    private final Map<String, Set<String>> seen = new HashMap<String, Set<String>>();

    /**
     * @param delegate
     *            Listener that should see each action once.
     */
    public DeduplicatingListener(final GameplayEventListener<T> delegate) {
        if (delegate == null)
            throw new IllegalArgumentException("delegate must not be null");
        this.delegate = delegate;
    }

    @Override
    public void onEvent(final T event) {
        if (markSeen(event))
            this.delegate.onEvent(event);
    }

    /**
     * Records the event as handled. The mark stays even if the wrapped
     * listener then fails, so a redelivery cannot run it twice.
     *
     * @return Whether this is the first delivery of the action.
     */
    private boolean markSeen(final T event) {
        Set<String> runIds = this.seen.get(event.getRunId());
        if (runIds == null) {
            runIds = new HashSet<String>();
            this.seen.put(event.getRunId(), runIds);
        }
        return runIds.add(event.getEventId());
    }
}
