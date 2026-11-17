package screen;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.Core;
import engine.Cooldown;
import entity.Ship;

/** Lets the player customize the ship's color, from the Settings menu. */
public class ShipColorScreen extends Screen {

    /** Return code that opens the Settings menu. */
    public static final int RETURN_TO_SETTINGS = 4;

    private static final int SELECTION_TIME = 200;
    private static final int COLOR_ADJUSTMENT_TIME = 25;
    private static final int COLOR_BAR_WIDTH = 240;
    private static final int COLOR_BAR_HEIGHT = 14;

    private final Cooldown selectionCooldown;
    private final Ship previewShip;
    private boolean editingCustomColor;
    private boolean draggingColor;
    private boolean wasMousePressed = true;
    private boolean wasConfirmPressed = true;

    public ShipColorScreen(final int width, final int height, final int fps) {
        super(width, height, fps);

        this.returnCode = RETURN_TO_SETTINGS;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        this.previewShip = new Ship(width / 2 - 13, height / 2 - 8);
    }

    @Override
    public final int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();

        boolean acceptsInput = this.inputDelay.checkFinished();
        boolean confirmPressed = this.inputManager.isKeyDown(KeyEvent.VK_ENTER)
                || this.inputManager.isKeyDown(KeyEvent.VK_SPACE);
        handleColorBar(acceptsInput);
        this.selectionCooldown.setDuration(this.editingCustomColor
                ? COLOR_ADJUSTMENT_TIME : SELECTION_TIME);

        if (acceptsInput && this.inputManager.isBackDown()) {
            this.returnCode = RETURN_TO_SETTINGS;
            this.isRunning = false;
            AudioManager.playSFX("audio/sfx/menu_back.wav");
        } else if (acceptsInput && ShipCustomization.isCustomColor()
                && confirmPressed && !this.wasConfirmPressed) {
            this.editingCustomColor = !this.editingCustomColor;
            this.draggingColor = false;
            this.selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_select.wav");
        } else if (acceptsInput
                && this.selectionCooldown.checkFinished()) {
            if (this.inputManager.isKeyDown(KeyEvent.VK_LEFT)
                    || this.inputManager.isKeyDown(KeyEvent.VK_A)) {
                changeSelection(-1);
            } else if (this.inputManager.isKeyDown(KeyEvent.VK_RIGHT)
                    || this.inputManager.isKeyDown(KeyEvent.VK_D)) {
                changeSelection(1);
            }
        }

        this.wasConfirmPressed = confirmPressed;
        draw();
    }

    private void changeSelection(final int direction) {
        if (this.editingCustomColor) {
            float hue = ShipCustomization.getCustomHue()
                    + direction / (float) (COLOR_BAR_WIDTH - 1);
            ShipCustomization.setCustomHue(Math.max(0f, Math.min(1f, hue)));
        } else {
            ShipCustomization.changeColor(direction);
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        }
        this.selectionCooldown.reset();
    }

    private Rectangle getColorBarBounds() {
        return new Rectangle((this.getWidth() - COLOR_BAR_WIDTH) / 2,
                this.getHeight() / 2 + 30, COLOR_BAR_WIDTH, COLOR_BAR_HEIGHT);
    }

    private void handleColorBar(final boolean acceptsInput) {
        boolean pressed = this.inputManager.isMousePressed();
        if (!acceptsInput || !ShipCustomization.isCustomColor() || !pressed) {
            this.draggingColor = false;
        } else {
            Rectangle bounds = getColorBarBounds();
            int mouseX = this.inputManager.getMouseX();
            int mouseY = this.inputManager.getMouseY();
            Rectangle hitArea = new Rectangle(bounds);
            hitArea.grow(0, 6);
            if (!this.wasMousePressed && hitArea.contains(mouseX, mouseY)) {
                this.draggingColor = true;
                this.editingCustomColor = true;
            }
            if (this.draggingColor && mouseX >= 0 && mouseY >= 0) {
                float hue = (mouseX - bounds.x) / (float) (bounds.width - 1);
                ShipCustomization.setCustomHue(Math.max(0f, Math.min(1f, hue)));
            }
        }
        this.wasMousePressed = pressed;
    }

    private void draw() {
        this.drawManager.initDrawing(this);
        this.previewShip.setColor(ShipCustomization.getColor());
        this.drawManager.drawShipColorSelection(this, this.previewShip,
                getColorBarBounds(), this.editingCustomColor);

        this.drawManager.completeDrawing(this);
    }
}
