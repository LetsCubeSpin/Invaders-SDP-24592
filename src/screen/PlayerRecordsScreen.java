package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import records.RecordsService;

/** Local browsing page for completed runs, their details and achievements. */
public final class PlayerRecordsScreen extends Screen {
    /** Tab listing personal bests and the top runs. */
    public static final int TAB_RUNS = 0;
    /** Tab showing every field of one of the top runs. */
    public static final int TAB_RUN_DETAILS = 1;
    /** Tab listing the achievements. */
    public static final int TAB_ACHIEVEMENTS = 2;
    /** Number of tabs, cycled with Left/Right. */
    private static final int TAB_COUNT = 3;

    private final RecordsService service;
    private int tab;
    /** Rank, from 0, of the run shown on the details tab. */
    private int selectedRun;
    /** Index, from 0, of the first achievement shown on the achievements tab. */
    private int firstAchievement;
    private long nextInputAt;

    public PlayerRecordsScreen(final int width, final int height,
            final int fps, final RecordsService service) {
        super(width, height, fps);
        this.service = service;
        this.returnCode = 1;
    }

    @Override
    public int run() {
        super.run();
        return this.returnCode;
    }

    protected void update() {
        super.update();
        this.drawManager.initDrawing(this);
        this.drawManager.drawRecordsPage(this, this.service, this.tab,
                this.selectedRun, this.firstAchievement);
        this.drawManager.completeDrawing(this);
        if (!this.inputDelay.checkFinished()
                || System.currentTimeMillis() < this.nextInputAt)
            return;
        if (this.inputManager.isBackDown()
                || this.inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            this.returnCode = 1;
            this.isRunning = false;
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_LEFT)) {
            changeTab(-1);
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_RIGHT)) {
            changeTab(1);
        } else if (this.tab == TAB_RUN_DETAILS
                && this.inputManager.isKeyDown(KeyEvent.VK_UP)) {
            changeRun(-1);
        } else if (this.tab == TAB_RUN_DETAILS
                && this.inputManager.isKeyDown(KeyEvent.VK_DOWN)) {
            changeRun(1);
        } else if (this.tab == TAB_ACHIEVEMENTS
                && this.inputManager.isKeyDown(KeyEvent.VK_UP)) {
            scrollAchievements(-1);
        } else if (this.tab == TAB_ACHIEVEMENTS
                && this.inputManager.isKeyDown(KeyEvent.VK_DOWN)) {
            scrollAchievements(1);
        }
    }

    /** Moves to the next or previous tab, wrapping around. */
    private void changeTab(final int step) {
        this.tab = Math.floorMod(this.tab + step, TAB_COUNT);
        this.nextInputAt = System.currentTimeMillis() + 250;
        AudioManager.playSFX("audio/sfx/menu_move.wav");
    }

    /**
     * Scrolls the achievements by one, stopping when the first or last one
     * is shown.
     */
    private void scrollAchievements(final int step) {
        int last = Math.max(0, this.service.getAchievementStatuses().size()
                - this.drawManager.getAchievementsPerPage(this));
        int first = Math.max(0, Math.min(this.firstAchievement + step, last));
        if (first == this.firstAchievement)
            return;
        this.firstAchievement = first;
        this.nextInputAt = System.currentTimeMillis() + 150;
        AudioManager.playSFX("audio/sfx/menu_move.wav");
    }

    /** Selects the next or previous top run, wrapping around. */
    private void changeRun(final int step) {
        int runs = this.service.getTopRuns().size();
        if (runs == 0)
            return;
        this.selectedRun = Math.floorMod(this.selectedRun + step, runs);
        this.nextInputAt = System.currentTimeMillis() + 150;
        AudioManager.playSFX("audio/sfx/menu_move.wav");
    }
}
