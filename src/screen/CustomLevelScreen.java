package screen;

import java.awt.event.KeyEvent;
import engine.Core;
import engine.Cooldown;
import engine.CustomLevelMenu.Action;

/** Custom-level hub; file dialogs and gameplay run only after this screen exits. */
public final class CustomLevelScreen extends Screen {
    private final int levelCount;
    private final Cooldown cooldown = Core.getCooldown(200);
    private int selected;

    public CustomLevelScreen(final int width, final int height, final int fps,
            final int levelCount) {
        super(width, height, fps);
        this.levelCount = levelCount;
        cooldown.reset();
    }

    public Action getAction() { return Action.values()[selected]; }

    private void moveSelection(final int direction) {
        do {
            selected = Math.floorMod(selected + direction, Action.values().length);
        } while (levelCount == 0 && (selected == Action.PLAY.ordinal() || selected == Action.EXPORT.ordinal()));
    }

    @Override
    protected void update() {
        super.update();
        drawManager.initDrawing(this);
        drawManager.drawCustomLevelMenu(this, selected, levelCount);
        drawManager.completeDrawing(this);
        if (!inputDelay.checkFinished() || !cooldown.checkFinished()) return;
        if (inputManager.isBackDown()) {
            selected = Action.BACK.ordinal();
            isRunning = false;
        } else if (inputManager.isKeyDown(KeyEvent.VK_UP) || inputManager.isKeyDown(KeyEvent.VK_W)) {
            moveSelection(-1);
            cooldown.reset();
        } else if (inputManager.isKeyDown(KeyEvent.VK_DOWN) || inputManager.isKeyDown(KeyEvent.VK_S)) {
            moveSelection(1);
            cooldown.reset();
        } else if ((inputManager.isKeyDown(KeyEvent.VK_SPACE) || inputManager.isKeyDown(KeyEvent.VK_ENTER))
                && (levelCount > 0 || selected == 0 || selected == 3)) {
            isRunning = false;
        }
    }
}
