package audio;

import event.EndingReachedEvent;
import event.GameplayEventListener;
import story.Ending;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Background music of each story ending.
 *
 * As a listener it plays the music of the ending in each
 * {@link EndingReachedEvent}:
 * {@code publisher.subscribe(EndingReachedEvent.class, new EndingBgm())}.
 */
public final class EndingBgm implements GameplayEventListener<EndingReachedEvent> {

    /** Music used until each ending gets its own track. */
    static final String PLACEHOLDER = "audio/bgm/music_end_victory.wav";

    /** Music path of each ending, relative to the classpath root. */
    private static final Map<Ending, String> TRACKS;

    static {
        Map<Ending, String> tracks = new EnumMap<Ending, String>(Ending.class);
        // TODO: replace the placeholder with each ending's own track.
        tracks.put(Ending.ENDING1, PLACEHOLDER);
        tracks.put(Ending.ENDING2, PLACEHOLDER);
        tracks.put(Ending.ENDING3, PLACEHOLDER);
        tracks.put(Ending.ENDING4, PLACEHOLDER);
        TRACKS = Collections.unmodifiableMap(tracks);
    }

    /** Plays a music path. */
    private final Consumer<String> player;

    /** Plays the music through {@link AudioManager#playBGM(String)}. */
    public EndingBgm() {
        this(AudioManager::playBGM);
    }

    /**
     * @param player
     *            Plays a music path; used by tests instead of the real audio.
     */
    EndingBgm(final Consumer<String> player) {
        if (player == null)
            throw new IllegalArgumentException("player must not be null.");
        this.player = player;
    }

    /**
     * @param ending
     *            Ending whose music is wanted.
     * @return Music path, relative to the classpath root, for
     *         {@link AudioManager#playBGM(String)}.
     */
    public static String pathFor(final Ending ending) {
        if (ending == null)
            throw new IllegalArgumentException("ending must not be null.");
        String path = TRACKS.get(ending);
        if (path == null)
            throw new IllegalStateException("No music for " + ending + ".");
        return path;
    }

    /** Plays the music of the ending the player just saw. */
    @Override
    public void onEvent(final EndingReachedEvent event) {
        this.player.accept(pathFor(event.getEnding()));
    }
}
