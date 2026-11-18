// 스토리 모드 진입 전에 조종사 초상화를 보여주는 임시 확인 화면
package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;

/**
 * Shows the animated pilot portrait before story mode starts.
 * Space continues to the story, Back returns to the main menu.
 */
public class PortraitPreviewScreen extends Screen {

    /** Return code that sends the player back to the main menu. */
    public static final int BACK_TO_MENU = 1;
    /** Return code that continues into story mode. */
    public static final int CONTINUE_STORY = 2;

    private final long startNanos = System.nanoTime();

    public PortraitPreviewScreen(final int width, final int height,
            final int fps) {
        super(width, height, fps);
        this.returnCode = CONTINUE_STORY;
    }

    @Override
    public int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();
        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;

        drawManager.initDrawing(this);
        drawManager.drawPortraitPreview(this, seconds);
        drawManager.completeDrawing(this);

        if (!this.inputDelay.checkFinished()) {
            return;
        }
        if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
            AudioManager.playSFX("audio/sfx/menu_select.wav");
            this.returnCode = CONTINUE_STORY;
            this.isRunning = false;
        } else if (inputManager.isBackDown()) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            this.returnCode = BACK_TO_MENU;
            this.isRunning = false;
        }
    }
}
