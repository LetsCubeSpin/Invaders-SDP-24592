// 스토리 오프닝 설정 문장을 타자기처럼 한 글자씩 보여주는 화면
package screen;


import story.Typewriter;

/**
 * Shows the story narration typed out one character at a time. Space shows
 * the rest of the text at once, and Space again leaves the screen.
 */
public class StoryNarrationScreen extends MenuScreen {

    /** Typing speed in characters per second. */
    private static final double CHARS_PER_SECOND = 30;

    private final Typewriter typewriter;
    public StoryNarrationScreen(final int width, final int height,
            final int fps, final String text) {
        super(width, height, fps);
        this.typewriter = new Typewriter(text, CHARS_PER_SECOND);
    }

    @Override
    public int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();
        long now = System.currentTimeMillis();
        // Typing starts when the screen starts accepting input.
        if (this.inputDelay.checkFinished())
            this.typewriter.start(now);

        boolean done = this.typewriter.isDone(now);
        drawManager.initDrawing(this);
        drawManager.drawStoryNarration(this, this.typewriter.getText(),
                this.typewriter.visibleLength(now), done, now);
        drawManager.completeDrawing(this);

        if (!this.inputDelay.checkFinished())
            return;
        if (confirmPressed || clicked == NEXT) {
            if (done)
                this.isRunning = false;
            else
                this.typewriter.finish();
        } else if (backPressed) {
            this.isRunning = false;
        }
    }
}
