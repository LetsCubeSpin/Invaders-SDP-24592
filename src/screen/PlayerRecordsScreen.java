package screen;

import java.awt.event.KeyEvent;
import java.util.List;

import audio.AudioManager;
import records.RecordsService;
import records.RunFilter;
import records.RunOrder;
import records.RunRecord;

/** Local browsing page for completed runs, achievements and stats. */
public final class PlayerRecordsScreen extends MenuScreen {
    /** Tab listing every run, with the details of the selected one. */
    public static final int TAB_RECORDS = 0;
    /** Tab listing the achievements. */
    public static final int TAB_ACHIEVEMENTS = 1;
    /** Tab showing the personal bests and the cumulative stats. */
    public static final int TAB_STATS = 2;
    /** Number of tabs, cycled with Left/Right. */
    public static final int TAB_COUNT = 3;
    /** Key that switches the order of the run list. */
    public static final int SORT_KEY = KeyEvent.VK_J;
    /** Key that switches the mode filter of the run list. */
    public static final int FILTER_KEY = KeyEvent.VK_K;

    public static final int SORT = -5;
    public static final int FILTER = -6;

    private final RecordsService service;
    private int tab;
    private RunOrder order = RunOrder.SCORE;
    private RunFilter filter = RunFilter.ALL;
    /** Index, from 0, of the selected run in the sorted, filtered list. */
    private int selectedRun;
    /** Index, from 0, of the first run shown in the list. */
    private int firstRun;
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
                this.order, this.filter, this.selectedRun, this.firstRun,
                this.firstAchievement);
        this.drawManager.completeDrawing(this);
        if (!menuReady()) return;
        int target = clicked != NONE ? clicked : hovered;
        if (target >= 0 && target < TAB_COUNT && target != tab) {
            changeTab(target - tab);
        } else if (tab == TAB_RECORDS && target >= TAB_COUNT) {
            changeRun(target - TAB_COUNT - selectedRun);
        }
        if (clicked == SORT) changeOrder();
        if (clicked == FILTER) changeFilter();
        if (scroll != 0 && tab == TAB_RECORDS) changeRun(scroll);
        if (scroll != 0 && tab == TAB_ACHIEVEMENTS) scrollAchievements(scroll);
        if (backPressed || clicked == BACK || confirmPressed) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            this.returnCode = 1;
            this.isRunning = false;
            return;
        }
        if (System.currentTimeMillis() < nextInputAt) return;
        if (this.inputManager.isKeyDown(KeyEvent.VK_LEFT)) {
            changeTab(-1);
        } else if (this.inputManager.isKeyDown(KeyEvent.VK_RIGHT)) {
            changeTab(1);
        } else if (this.tab == TAB_RECORDS) {
            if (this.inputManager.isKeyDown(KeyEvent.VK_UP))
                changeRun(-1);
            else if (this.inputManager.isKeyDown(KeyEvent.VK_DOWN))
                changeRun(1);
            else if (this.inputManager.isKeyDown(SORT_KEY))
                changeOrder();
            else if (this.inputManager.isKeyDown(FILTER_KEY))
                changeFilter();
        } else if (this.tab == TAB_ACHIEVEMENTS) {
            if (this.inputManager.isKeyDown(KeyEvent.VK_UP))
                scrollAchievements(-1);
            else if (this.inputManager.isKeyDown(KeyEvent.VK_DOWN))
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

    /**
     * Selects the next or previous run, stopping at the first and last, and
     * scrolls the list so the selected run is shown.
     */
    private void changeRun(final int step) {
        List<RunRecord> runs = this.service.getRuns(this.order, this.filter);
        int selected = Math.max(0, Math.min(this.selectedRun + step, runs.size() - 1));
        if (selected == this.selectedRun)
            return;
        this.selectedRun = selected;
        int perPage = this.drawManager.getRunsPerPage(this);
        if (selected < this.firstRun)
            this.firstRun = selected;
        else if (selected >= this.firstRun + perPage)
            this.firstRun = selected - perPage + 1;
        this.nextInputAt = System.currentTimeMillis() + 150;
        AudioManager.playSFX("audio/sfx/menu_move.wav");
    }

    /** Switches between score and newest order and selects the first run. */
    private void changeOrder() {
        this.order = this.order.next();
        listChanged();
    }

    /** Switches to the next mode filter and selects the first run. */
    private void changeFilter() {
        this.filter = this.filter.next();
        listChanged();
    }

    private void listChanged() {
        this.selectedRun = 0;
        this.firstRun = 0;
        this.nextInputAt = System.currentTimeMillis() + 250;
        AudioManager.playSFX("audio/sfx/menu_move.wav");
    }
}
