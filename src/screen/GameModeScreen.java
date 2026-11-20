package screen;

import java.awt.event.KeyEvent;
import audio.AudioManager;
import engine.Cooldown;
import engine.Core;

public class GameModeScreen extends Screen { // Implementation of the game mode selection screen.

    private static final int SELECTION_TIME = 200;
    /** Choices of the mode step: 0 Endless, 1 Campaign, 2 Story, 3 Custom, 4 Back. */
    static final int MODE_CHOICES = 5;
    /** Choices of the player step: 0 1P, 1 2P, 2 Back. */
    static final int PLAYER_CHOICES = 3;
    /** Mode index of story mode, which is played by one player. */
    static final int STORY_MODE = 2;
    /** Mode index of custom levels, which bypass player selection. */
    static final int CUSTOM_MODE = 3;
    /** Mode index of Back on the mode step. */
    static final int MODE_BACK = 4;

    private int selected;
    private Cooldown selectionCooldown;

    // 2인 모드 처리를 위한 추가 필드
    private int menuStep; // 0: 모드 선택 단계, 1: 인원 선택 단계
    private int selectedMode; // 0: Endless, 1: Campaign
    private boolean is2P; // 2인용 여부 저장 플래그

    public GameModeScreen(final int width, final int height, final int fps) {
        super(width, height, fps);
        this.selected = 0;
        this.returnCode = 1;
        this.menuStep = 0; // 시작은 모드 선택 단계
        this.selectedMode = 0;
        this.is2P = false;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
    }

    public final int run() {
        super.run();
        return this.returnCode;
    }

    protected final void update() {
        super.update();

        draw();
        if (this.selectionCooldown.checkFinished()
                && this.inputDelay.checkFinished()) {

            if (inputManager.isKeyDown(KeyEvent.VK_UP)
                    || inputManager.isKeyDown(KeyEvent.VK_W)) {
                this.selected = this.selected == 0
                        ? choiceCount(this.menuStep) - 1
                        : this.selected - 1;
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");   // 추가 (두 곳 모두)
            }
            if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
                    || inputManager.isKeyDown(KeyEvent.VK_S)) {
                this.selected = (this.selected + 1)
                        % choiceCount(this.menuStep);
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }

            if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
                if (this.menuStep == 0) {
                    // [1단계] 모드 선택 처리
                    if (this.selected == MODE_BACK) { // Back
                        this.returnCode = 1;
                        this.isRunning = false;
                        AudioManager.playSFX("audio/sfx/menu_back.wav");
                    } else if (this.selected == STORY_MODE || this.selected == CUSTOM_MODE) {
                        // Story and custom levels bypass the player-count selection.
                        this.returnCode = returnCodeFor(this.selected);
                        this.isRunning = false;
                        AudioManager.playSFX("audio/sfx/menu_select.wav");
                    } else { // Endless(0) 또는 Campaign(1) 선택 시 2단계(인원 선택)로 전진
                        this.selectedMode = this.selected;
                        this.menuStep = 1;
                        this.selected = 0; // 커서 위치 초기화
                        this.selectionCooldown.reset();
                        AudioManager.playSFX("audio/sfx/menu_select.wav");
                    }
                } else if (this.menuStep == 1) {
                    // [2단계] 인원 선택 처리
                    if (this.selected == 2) { // Back 선택 시 다시 1단계(모드 선택)로 복귀
                        this.menuStep = 0;
                        this.selected = this.selectedMode;
                        this.selectionCooldown.reset();
                        AudioManager.playSFX("audio/sfx/menu_back.wav");
                    } else {
                        // 1P(0) 또는 2P(1) 최종 확정
                        this.is2P = (this.selected == 1);

                        // 기존 returnCode 유지 (Endless: 8, Campaign: 2)
                        this.returnCode = returnCodeFor(this.selectedMode);
                        this.isRunning = false;
                        AudioManager.playSFX("audio/sfx/menu_select.wav");
                    }
                }
            }

            // ESC / 우클릭 처리 (뒤로가기)
            if (inputManager.isBackDown()) {
                if (this.menuStep == 1) {
                    this.menuStep = 0; // 2단계에서는 1단계로 복귀
                    this.selected = this.selectedMode;
                    this.selectionCooldown.reset();
                    AudioManager.playSFX("audio/sfx/menu_back.wav");
                } else {
                    this.returnCode = 1; // 1단계에서는 메인 메뉴로 복귀
                    this.isRunning = false;
                    AudioManager.playSFX("audio/sfx/menu_back.wav");
                }
            }
        }
    }

    public final int getSelectedGameMode() {
        return this.selected;
    }

    public final int getPlayerCount() {
        return this.is2P ? 2 : 1;
    }

    /**
     * @param menuStep 0 for the mode step, 1 for the player step.
     * @return Number of choices on that step.
     */
    static int choiceCount(final int menuStep) {
        return menuStep == 0 ? MODE_CHOICES : PLAYER_CHOICES;
    }

    /**
     * @param mode Mode index: 0 Endless, 1 Campaign, 2 Story, 3 Custom.
     * @return Code Core uses to start the mode.
     */
    static int returnCodeFor(final int mode) {
        switch (mode) {
        case 0:
            return 8;
        case 1:
            return 2;
        case STORY_MODE:
            return 15;
        case CUSTOM_MODE:
            return 16;
        default:
            throw new IllegalArgumentException("Unknown game mode: " + mode);
        }
    }

    private void draw() {
        drawManager.initDrawing(this);
        if (this.menuStep == 0) {
            drawManager.drawGameModeMenu(this, this.selected);
        } else {
            drawManager.drawPlayerSelectMenu(this, this.selected);
        }
        drawManager.completeDrawing(this);
    }
}
