package story;

/**
 * Story scenes shown between the stages of story mode: the opening, the
 * two questions and the ending.
 *
 * Story mode only talks to the scenes through this interface, so the real
 * scene screens can replace the placeholder without changing the stage
 * flow.
 */
public interface StoryScenes {

    /** Shows the opening story before stage 1. */
    void showOpening();

    /**
     * Asks the first question, after stage 1.
     *
     * @return 0 for the first option (assault), 1 for the second (rescue).
     */
    int askFirst();

    /**
     * Asks the second question, after the stage 3 boss. The question
     * depends on the first answer.
     *
     * @param firstChoice
     *            Answer to the first question, 0 or 1.
     * @return 0 for the first option, 1 for the second.
     */
    int askSecond(int firstChoice);

    /**
     * Shows the ending of a route, after the stage 7 boss.
     *
     * @param route
     *            Route the player finished.
     */
    void showEnding(StoryRoute route);
}
