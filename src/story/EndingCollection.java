package story;

import event.AllEndingsSeenEvent;
import event.EndingReachedEvent;
import event.GameplayEventPublisher;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps the endings the player has seen and publishes an
 * {@link EndingReachedEvent} each time an ending is seen.
 *
 * The collection only lives in memory. {@code records.RecordsService}
 * saves the seen endings in the profile; fill the collection from the save
 * with {@link #restore(Collection)}.
 *
 * <p>Not connected to the game yet: nothing creates this class during
 * play, so the ending events, the ending music, the ending save and the
 * all-endings achievement do not happen in the game. Everything is ready
 * and tested, so take it and connect it when the story endings need it,
 * for example when the game starts:</p>
 *
 * <pre>{@code
 * EndingCollection endings = new EndingCollection(publisher);
 * endings.restore(records.getSeenEndings());   // endings from the save
 * publisher.subscribe(EndingReachedEvent.class, e -> records.endingSeen(e.getEnding()));
 * publisher.subscribe(EndingReachedEvent.class, new EndingBgm());
 * publisher.subscribe(AllEndingsSeenEvent.class, e -> records.allEndingsSeen());
 * }</pre>
 *
 * and {@code endings.see(ending)} where a story ending is shown
 * ({@code StoryScenes.showEnding}).
 */
public final class EndingCollection {

    /** Receives an event each time an ending is seen. */
    private final GameplayEventPublisher events;
    /** Endings seen at least once. */
    private final Set<Ending> seen = EnumSet.noneOf(Ending.class);

    /**
     * @param events
     *            Publisher that receives an event for each seen ending.
     */
    public EndingCollection(final GameplayEventPublisher events) {
        if (events == null)
            throw new IllegalArgumentException("events must not be null.");
        this.events = events;
    }

    /**
     * Records that the player saw an ending and publishes an
     * {@link EndingReachedEvent}. Seeing the same ending again publishes
     * the event again, with {@link EndingReachedEvent#isFirstTime()} false.
     *
     * When this ending is the last one not seen yet, an
     * {@link AllEndingsSeenEvent} is published after it.
     *
     * @param ending
     *            Ending the player saw.
     * @return Whether this is the first time the ending was seen.
     */
    public boolean see(final Ending ending) {
        if (ending == null)
            throw new IllegalArgumentException("ending must not be null.");
        boolean firstTime = this.seen.add(ending);
        this.events.publish(new EndingReachedEvent(ending, firstTime));
        if (firstTime && hasSeenAll())
            this.events.publish(new AllEndingsSeenEvent());
        return firstTime;
    }

    /**
     * Marks endings as seen without publishing events, for loading a save.
     *
     * @param endings
     *            Endings seen in an earlier session.
     */
    public void restore(final Collection<Ending> endings) {
        if (endings == null)
            throw new IllegalArgumentException("endings must not be null.");
        for (Ending ending : endings)
            if (ending == null)
                throw new IllegalArgumentException(
                        "endings must not contain null.");
        this.seen.addAll(endings);
    }

    /**
     * @param ending
     *            Ending to check.
     * @return Whether the ending has been seen.
     */
    public boolean hasSeen(final Ending ending) {
        return this.seen.contains(ending);
    }

    /** @return Seen endings in ending order, read-only. */
    public Set<Ending> getSeen() {
        return Collections.unmodifiableSet(EnumSet.copyOf(this.seen));
    }

    /** @return Number of different endings seen. */
    public int getSeenCount() {
        return this.seen.size();
    }

    /** @return Whether every ending has been seen. */
    public boolean hasSeenAll() {
        return this.seen.size() == Ending.values().length;
    }
}
