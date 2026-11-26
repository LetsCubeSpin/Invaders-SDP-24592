package screen;

import java.awt.event.KeyEvent;
import audio.AudioManager;
import engine.LanguageManager;

/** Results and retry for the three-stage time attack run. */
public final class TimeAttackResultScreen extends Screen {
    public static final int STAGE_COUNT = 3;
    private final int clearedStages;
    private final int score;
    private boolean confirmWasDown;

    public TimeAttackResultScreen(final int width, final int height, final int fps,
            final int clearedStages, final int score) {
        super(width, height, fps);
        this.clearedStages = clearedStages;
        this.score = score;
        this.returnCode = 14;
    }

    @Override
    public void initialize() {
        AudioManager.playBGM(this.clearedStages == STAGE_COUNT
                ? "audio/bgm/music_end_victory.wav" : "audio/bgm/music_end_defeat.wav");
        this.confirmWasDown = inputManager.isKeyDown(KeyEvent.VK_SPACE);
    }

    @Override
    public int run() {
        super.run();
        return Thread.currentThread().isInterrupted() ? 0 : this.returnCode;
    }

    @Override
    protected void update() {
        super.update();
        drawManager.initDrawing(this);
        drawManager.drawTimeAttackResult(this, clearedStages, STAGE_COUNT, score);
        drawManager.completeDrawing(this);
        boolean confirmDown = inputManager.isKeyDown(KeyEvent.VK_SPACE);
        if (this.inputDelay.checkFinished()) {
            if (inputManager.isBackDown()) {
                AudioManager.playSFX("audio/sfx/menu_back.wav");
                this.isRunning = false;
            } else if (confirmDown && !this.confirmWasDown) {
                LanguageManager.resetToEnglish();
                this.returnCode = GameModeScreen.START_TIME_ATTACK;
                this.isRunning = false;
            }
        }
        this.confirmWasDown = confirmDown;
    }
}
