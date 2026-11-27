package screen;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.Core;
import engine.Cooldown;
import entity.Ship;

/** Lets the player customize the ship's color, from the Settings menu. */
public class ShipColorScreen extends MenuScreen {

    /** Return code that opens the Settings menu. */
    public static final int RETURN_TO_SETTINGS = 4;

    private static final int SELECTION_TIME = 200;
    private static final int COLOR_ADJUSTMENT_TIME = 25;
    public static final int HUE_BAR = 2;
    private static final int COLOR_BAR_WIDTH = 240;
    private static final int COLOR_BAR_HEIGHT = 14;
    private static final int PREVIEW_SHIP_OFFSET = 48;

    private final Cooldown selectionCooldown;
    private final Ship previewShip;
    private final Ship secondPlayerPreviewShip;
    private int selectedPlayerIndex;
    private boolean editingCustomColor;
    private boolean draggingColor;
    private boolean wasUpPressed;
    private boolean wasDownPressed;

    public ShipColorScreen(final int width, final int height, final int fps) {
        super(width, height, fps);

        this.returnCode = RETURN_TO_SETTINGS;
        this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
        int centerX = width / 2;
        int positionY = height / 2 - 8;
        this.previewShip = new Ship(centerX - PREVIEW_SHIP_OFFSET - 13,
                positionY);
        this.secondPlayerPreviewShip = new Ship(
                centerX + PREVIEW_SHIP_OFFSET - 13, positionY);
    }

    @Override
    public final int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();

        draw();
        boolean acceptsInput = menuReady();
        if (acceptsInput) {
            int player = clicked >= 0 ? clicked : hovered;
            if ((player == 0 || player == 1) && !draggingColor) selectPlayer(player);
            if (clicked == PREVIOUS || clicked == NEXT) {
                editingCustomColor = false;
                changeSelection(clicked == NEXT ? 1 : -1);
            }
        }
        handlePlayerSelection(acceptsInput);
        handleColorBar(acceptsInput);
        this.selectionCooldown.setDuration(this.editingCustomColor
                ? COLOR_ADJUSTMENT_TIME : SELECTION_TIME);

        if (acceptsInput && (backPressed || clicked == BACK)) {
            this.returnCode = RETURN_TO_SETTINGS;
            this.isRunning = false;
            AudioManager.playSFX("audio/sfx/menu_back.wav");
        } else if (acceptsInput
                && ShipCustomization.isCustomColor(this.selectedPlayerIndex)
                && confirmPressed) {
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

    }

    private void handlePlayerSelection(final boolean acceptsInput) {
        boolean upPressed = this.inputManager.isKeyDown(KeyEvent.VK_UP);
        boolean downPressed = this.inputManager.isKeyDown(KeyEvent.VK_DOWN);
        if (acceptsInput && upPressed && !this.wasUpPressed) {
            selectPlayer(0);
        } else if (acceptsInput && downPressed && !this.wasDownPressed) {
            selectPlayer(1);
        }
        this.wasUpPressed = upPressed;
        this.wasDownPressed = downPressed;
    }

    private void selectPlayer(final int playerIndex) {
        if (this.selectedPlayerIndex == playerIndex)
            return;
        this.selectedPlayerIndex = playerIndex;
        this.editingCustomColor = false;
        this.draggingColor = false;
        AudioManager.playSFX("audio/sfx/menu_move.wav");
    }

    private void changeSelection(final int direction) {
        if (this.editingCustomColor) {
            float hue = ShipCustomization.getCustomHue(this.selectedPlayerIndex)
                    + direction / (float) (COLOR_BAR_WIDTH - 1);
            ShipCustomization.setCustomHue(this.selectedPlayerIndex,
                    Math.max(0f, Math.min(1f, hue)));
        } else {
            ShipCustomization.changeColor(this.selectedPlayerIndex, direction);
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        }
        this.selectionCooldown.reset();
    }

    private Rectangle getColorBarBounds() {
        return new Rectangle((this.getWidth() - COLOR_BAR_WIDTH) / 2,
                this.getHeight() / 2 + 30, COLOR_BAR_WIDTH, COLOR_BAR_HEIGHT);
    }

    private void handleColorBar(final boolean acceptsInput) {
        if (!acceptsInput || !ShipCustomization.isCustomColor(selectedPlayerIndex)) {
            draggingColor = false;
            return;
        }
        boolean pressed = inputManager.isMousePressed();
        boolean started = clicked == HUE_BAR;
        if (started) editingCustomColor = true;
        draggingColor = pressed && (started || draggingColor);
        if (started || draggingColor) {
            int x = draggingColor ? inputManager.getMouseX() : inputPressX();
            if (x < 0 || (draggingColor && inputManager.getMouseY() < 0)) return;
            Rectangle bounds = getColorBarBounds();
            float hue = (x - bounds.x) / (float) (bounds.width - 1);
            ShipCustomization.setCustomHue(selectedPlayerIndex, Math.max(0f, Math.min(1f, hue)));
        }
    }

    private void draw() {
        this.drawManager.initDrawing(this);
        this.previewShip.setColor(ShipCustomization.getColor(0));
        this.secondPlayerPreviewShip.setColor(ShipCustomization.getColor(1));
        this.drawManager.drawShipColorSelection(this, this.previewShip,
                this.secondPlayerPreviewShip, getColorBarBounds(),
                this.editingCustomColor, this.selectedPlayerIndex);

        this.drawManager.completeDrawing(this);
    }
}
