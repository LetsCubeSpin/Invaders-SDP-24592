package story;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Temporary story scenes used until the real scene screens are added.
 *
 * It shows nothing and always gives the answers of one route. The route is
 * HYBRID unless the game is started with -Dstory.route=NAME, for example
 * -Dstory.route=TRUCE, so every route can be played for testing.
 */
public final class PlaceholderStoryScenes implements StoryScenes {

    /** System property that picks the route to play. */
    public static final String ROUTE_PROPERTY = "story.route";

    private static final Logger LOGGER =
            Logger.getLogger(PlaceholderStoryScenes.class.getName());

    /** Route whose answers are given. */
    private final StoryRoute route;

    /** Uses the route named by the system property, or HYBRID. */
    public PlaceholderStoryScenes() {
        this(System.getProperty(ROUTE_PROPERTY));
    }

    /**
     * @param routeName
     *            Name of the route to play, any case. Null or blank means
     *            HYBRID.
     */
    PlaceholderStoryScenes(final String routeName) {
        this.route = parseRoute(routeName);
    }

    /**
     * @param routeName
     *            Name of a route, any case, or null.
     * @return Matching route, or HYBRID when the name is null or blank.
     */
    static StoryRoute parseRoute(final String routeName) {
        if (routeName == null || routeName.trim().isEmpty())
            return StoryRoute.HYBRID;
        try {
            return StoryRoute.valueOf(
                    routeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown story route: "
                    + routeName, unknown);
        }
    }

    /** @return Route whose answers are given. */
    public StoryRoute getRoute() {
        return this.route;
    }

    @Override
    public void showOpening() {
        LOGGER.info("Story opening (placeholder).");
    }

    @Override
    public int askFirst() {
        LOGGER.info("Story question 1 (placeholder): answer "
                + this.route.getFirstChoice() + ".");
        return this.route.getFirstChoice();
    }

    @Override
    public int askSecond(final int firstChoice) {
        if (firstChoice != this.route.getFirstChoice())
            throw new IllegalArgumentException(
                    "First answer does not match the placeholder route.");
        LOGGER.info("Story question 2 (placeholder): answer "
                + this.route.getSecondChoice() + ".");
        return this.route.getSecondChoice();
    }

    @Override
    public void showEnding(final StoryRoute finishedRoute) {
        if (finishedRoute == null)
            throw new IllegalArgumentException("route must not be null.");
        LOGGER.info("Story ending " + finishedRoute.getEndingNumber() + " ("
                + finishedRoute + ", placeholder).");
    }
}
