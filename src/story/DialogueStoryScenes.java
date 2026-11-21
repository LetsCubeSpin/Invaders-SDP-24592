// 오프닝만 실제 화면으로 보여주고 나머지 장면은 기존 임시 장면에 맡기는 StoryScenes 구현
package story;

/**
 * Story scenes that show the opening on a real screen. The questions and the
 * endings are still handled by another {@link StoryScenes}, so they keep
 * their current behaviour until their own screens exist.
 */
public final class DialogueStoryScenes implements StoryScenes {

    private final Runnable openingScreen;
    private final StoryScenes rest;

    /**
     * @param openingScreen
     *            Runs the opening screen and returns when the player is done.
     * @param rest
     *            Scenes that answer the questions and show the endings.
     */
    public DialogueStoryScenes(final Runnable openingScreen,
            final StoryScenes rest) {
        if (openingScreen == null || rest == null)
            throw new IllegalArgumentException(
                    "openingScreen and rest must not be null.");
        this.openingScreen = openingScreen;
        this.rest = rest;
    }

    @Override
    public void showOpening() {
        this.openingScreen.run();
    }

    @Override
    public int askFirst() {
        return this.rest.askFirst();
    }

    @Override
    public int askSecond(final int firstChoice) {
        return this.rest.askSecond(firstChoice);
    }

    @Override
    public void showEnding(final StoryRoute route) {
        this.rest.showEnding(route);
    }
}
