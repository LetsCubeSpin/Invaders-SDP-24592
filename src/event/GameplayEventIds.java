package event;

import java.util.UUID;

/**
 * Builds event IDs in the format the enemy defeat IDs use: a kind that
 * groups the IDs, the separator, then a random part that keeps every action
 * distinct, even across the screens of one run.
 *
 * <p>The kind only groups IDs for reading logs; listeners read the kind from
 * the event itself, never from its ID.</p>
 */
public final class GameplayEventIds {

    /** Separates the kind from the random part, as in enemy defeat IDs. */
    public static final String SEPARATOR = ":";

    private GameplayEventIds() {
    }

    /**
     * @param kind
     *            Group of the ID, such as a boss type or a hit source.
     * @return New ID beginning with the kind and the separator.
     */
    public static String newId(final String kind) {
        if (kind == null || kind.trim().isEmpty())
            throw new IllegalArgumentException("kind is required.");
        return kind + SEPARATOR + UUID.randomUUID();
    }
}
