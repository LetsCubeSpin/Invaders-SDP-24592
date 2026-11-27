package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.ControlsConfig;
import engine.Cooldown;
import engine.Core;

public class ControlsSettingsScreen extends MenuScreen {
    private static final int SELECTION_TIME = 200;

    private static final int LEFT_OPTION = 0;
    private static final int RIGHT_OPTION = 1;
    private static final int FIRE_OPTION = 2;
    private static final int BACK_OPTION = 3;
    private static final int OPTION_COUNT = 4;

    private final Cooldown selectionCooldown;
    private int selectedOption;
    private boolean waitingForKey;
    private boolean waitingForKeyRelease;
    /** True after a right click cancelled key capture, until it is released. */
    private boolean waitingForRightRelease;
    private int capturedKeyCode;

    public ControlsSettingsScreen(final int width, final int height,
            final int fps) {
        super(width, height, fps);
        this.returnCode = 4;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        this.selectedOption = LEFT_OPTION;
        this.waitingForKey = false;
        this.waitingForKeyRelease = false;
        this.capturedKeyCode = KeyEvent.VK_UNDEFINED;
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

        if (!waitingForKey) {
            readTargetAt((x, y) -> drawManager.getControlsSettingsOptionAt(this, selectedOption, false, x, y));
            if (hovered != NONE && hovered != selectedOption) {
                selectedOption = hovered;
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }
            if (clicked != NONE) {
                selectedOption = clicked;
                confirmSelectedOption();
                return;
            }
        }

        if (this.waitingForKey) {
            // Right click cancels choosing a new key, like Esc.
            if (backPressed && !inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
                this.waitingForKey = false;
                this.waitingForRightRelease = true;
                this.selectionCooldown.reset();
                AudioManager.playSFX("audio/sfx/menu_back.wav");
                return;
            }
            captureKey();
            return;
        }

        // Do not let the cancelling right click also leave the screen.
        if (this.waitingForRightRelease) {
            if (inputManager.isRightMousePressed()) {
                return;
            }
            this.waitingForRightRelease = false;
        }

        // Do not let the newly assigned key also move the menu selection.
        if (this.waitingForKeyRelease) {
            if (inputManager.isKeyDown(this.capturedKeyCode)) {
                return;
            }
            this.waitingForKeyRelease = false;
        }

        if ((!backPressed && !confirmPressed && !this.selectionCooldown.checkFinished())
                || !menuReady()) {
            return;
        }

        if (inputManager.isKeyDown(KeyEvent.VK_UP)
                || inputManager.isKeyDown(KeyEvent.VK_W)) {
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
            this.returnCode = 4;
            this.isRunning = false;
        }
    }

    /**
     * Turns off the global mute key while a new key is being captured,
     * so pressing M to assign it does not also toggle mute.
     *
     * @return False while waiting for a new key, otherwise true.
     */
    @Override
    protected boolean usesGlobalMuteKey() {
        return !this.waitingForKey;
    }

    /**
     * Goes back on the Back option, or waits for a new key on the others.
     */
    private void confirmSelectedOption() {
        if (this.selectedOption == BACK_OPTION) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            this.returnCode = 4;
            this.isRunning = false;
        } else {
            inputManager.clearLastPressedKey();
            this.waitingForKey = true;
        }
        this.selectionCooldown.reset();
    }

    private void captureKey() {
        int keyCode = inputManager.consumeLastPressedKey();

        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return;
        }

        this.waitingForKey = false;
        this.capturedKeyCode = keyCode;
        this.waitingForKeyRelease = inputManager.isKeyDown(keyCode);
        this.selectionCooldown.reset();

        if (keyCode == KeyEvent.VK_ESCAPE) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            return;
        }
        if (isKeyAssignedToAnotherAction(keyCode)) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            return;
        }

        ControlsConfig.setKey(this.selectedOption, keyCode);
        AudioManager.playSFX("audio/sfx/menu_select.wav");
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawControlsSettings(this, this.selectedOption,
                this.waitingForKey);
        drawManager.completeDrawing(this);
    }

    private boolean isKeyAssignedToAnotherAction(final int keyCode) {
    return (this.selectedOption != LEFT_OPTION
            && ControlsConfig.getKey(ControlsConfig.LEFT) == keyCode)
            || (this.selectedOption != RIGHT_OPTION
                    && ControlsConfig.getKey(ControlsConfig.RIGHT) == keyCode)
            || (this.selectedOption != FIRE_OPTION
                    && ControlsConfig.getKey(ControlsConfig.FIRE) == keyCode);
}
}
