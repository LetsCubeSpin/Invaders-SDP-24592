package screen;

import java.awt.event.KeyEvent;
import audio.AudioManager;
import engine.Cooldown;
import engine.Core;

public class GameModeScreen extends Screen { // Implementation of the game mode selection screen.

    private static final int SELECTION_TIME = 200;
    private static final int NUM_CHOICES = 3; // Number of game mode choices (0: Infinite, 1: Campaign, 2: Back)

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
                this.selected = this.selected == 0 ? NUM_CHOICES - 1
                        : this.selected - 1;
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");   // 추가 (두 곳 모두)
            }
            if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
                    || inputManager.isKeyDown(KeyEvent.VK_S)) {
                this.selected = (this.selected + 1) % NUM_CHOICES;
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }

            if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
                if (this.menuStep == 0) {
                    // [1단계] 모드 선택 처리
                    if (this.selected == 2) { // Back
                        this.returnCode = 1;
                        this.isRunning = false;
                        AudioManager.playSFX("audio/sfx/menu_back.wav");
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
                        this.returnCode = (this.selectedMode == 0) ? 8 : 2;
                        this.isRunning = false;
                        AudioManager.playSFX("audio/sfx/menu_select.wav");
                    }
                }
            }

            // ESC 처리 (뒤로가기)
            if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
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
