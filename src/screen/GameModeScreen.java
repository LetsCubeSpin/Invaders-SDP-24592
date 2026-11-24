package screen;

import java.awt.event.KeyEvent;
import audio.AudioManager;
import engine.Cooldown;
import engine.Core;

public class GameModeScreen extends MenuScreen { // Implementation of the game mode selection screen.

    private static final int SELECTION_TIME = 200;
    /** Choices of the mode step: 0 Endless, 1 Campaign, 2 Story, 3 Custom, 4 Time Attack, 5 Back. */
    static final int MODE_CHOICES = 6;
    /** Choices of the player step: 0 1P, 1 2P, 2 Back. */
    static final int PLAYER_CHOICES = 3;
    /** Mode index of story mode, which is played by one player. */
    static final int STORY_MODE = 2;
    /** Mode index of custom levels, which bypass player selection. */
    static final int CUSTOM_MODE = 3;
    /** Mode index of Back on the mode step. */
    static final int MODE_BACK = 5;
    static final int TIME_ATTACK_MODE = 4;
    public static final int START_TIME_ATTACK = 17;

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
        if (hovered >= 0 && hovered != selected) {
            selected = hovered;
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        }
        if (!menuReady()) return;
        if (backPressed) {
            goBack();
        } else if (clicked >= 0 || confirmPressed) {
            if (clicked >= 0) selected = clicked;
            if (selected == choiceCount(menuStep) - 1) {
                goBack();
            } else {
                choose();
            }
        } else if (selectionCooldown.checkFinished()) {
            int direction = 0;
            if (inputManager.isKeyDown(KeyEvent.VK_UP) || inputManager.isKeyDown(KeyEvent.VK_W))
                direction = -1;
            else if (inputManager.isKeyDown(KeyEvent.VK_DOWN) || inputManager.isKeyDown(KeyEvent.VK_S))
                direction = 1;
            if (direction != 0) {
                selected = Math.floorMod(selected + direction, choiceCount(menuStep));
                selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }
        }
    }

    private void choose() {
        if (menuStep == 0 && selected < STORY_MODE) {
            selectedMode = selected;
            menuStep = 1;
            selected = 0;
        } else {
            is2P = menuStep == 1 && selected == 1;
            returnCode = returnCodeFor(menuStep == 0 ? selected : selectedMode);
            isRunning = false;
        }
        selectionCooldown.reset();
        AudioManager.playSFX("audio/sfx/menu_select.wav");
    }

    private void goBack() {
        if (menuStep == 1) {
            menuStep = 0;
            selected = selectedMode;
            selectionCooldown.reset();
        } else {
            returnCode = 1;
            isRunning = false;
        }
        AudioManager.playSFX("audio/sfx/menu_back.wav");
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
     * @param mode Mode index: 0 Endless, 1 Campaign, 2 Story, 3 Custom, 4 Time Attack.
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
        case TIME_ATTACK_MODE:
            return START_TIME_ATTACK;
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
