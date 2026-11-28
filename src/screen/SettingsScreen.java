package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.Cooldown;
import engine.Core;
import engine.LanguageManager;

public class SettingsScreen extends MenuScreen {

    private static final int SELECTION_TIME = 200;
    private static final int AUDIO_OPTION = 0;
    private static final int CONTROLS_OPTION = 1;
    private static final int COLOR_OPTION = 2;
    private static final int LANGUAGE_OPTION = 3;
    private static final int BACK_OPTION = 4;
    private static final int OPTION_COUNT = 5;

    private Cooldown selectionCooldown;
    private int selectedOption;

    public SettingsScreen(final int width, final int height, final int fps) {
        super(width, height, fps);
        this.returnCode = 1; // Retour au menu principal
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        this.selectedOption = AUDIO_OPTION;
    }

    @Override
    public int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();
        draw();

        readTargetAt((x, y) -> drawManager.getSettingsMenuOptionAt(this, x, y));
        if (hovered != NONE && hovered != selectedOption) {
            selectedOption = hovered;
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        }
        if (clicked != NONE) {
            selectedOption = clicked;
            confirmSelectedOption();
            return;
        }

        if ((!backPressed && !confirmPressed && !this.selectionCooldown.checkFinished())
                || !menuReady()) {
            return;
        }

        if (inputManager.isKeyDown(KeyEvent.VK_UP) || inputManager.isKeyDown(KeyEvent.VK_W)) {
            this.selectedOption =
                    (this.selectedOption - 1 + OPTION_COUNT) % OPTION_COUNT;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
                || inputManager.isKeyDown(KeyEvent.VK_S)) {
            this.selectedOption =
                    (this.selectedOption + 1) % OPTION_COUNT;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (confirmPressed) {
            confirmSelectedOption();
        } else if (backPressed) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            this.returnCode = 1;
            this.isRunning = false;
        }
    }

    /**
     * Opens the screen of the selected option.
     */
    private void confirmSelectedOption() {
        switch (this.selectedOption) {
        case AUDIO_OPTION:
            this.returnCode = 5;
            break;
        case CONTROLS_OPTION:
            this.returnCode = 6;
            break;
        case COLOR_OPTION:
            this.returnCode = 7;
            break;
        case LANGUAGE_OPTION:
            LanguageManager.toggleLanguage();
            AudioManager.playSFX("audio/sfx/menu_select.wav");
            return;
        case BACK_OPTION:
        default:
            this.returnCode = 1;
            break;
        }
        if(this.selectedOption == BACK_OPTION){
            AudioManager.playSFX("audio/sfx/menu_back.wav");
        }
        else {
            AudioManager.playSFX("audio/sfx/menu_select.wav");
        }
        this.isRunning = false;
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawSettingsMenu(this, this.selectedOption);
        drawManager.completeDrawing(this);
    }
}
