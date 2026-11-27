package screen;

import java.awt.event.KeyEvent;
import java.util.List;
import engine.Core;
import engine.Cooldown;
import engine.CustomLevelMenu;

/** Scrollable library shared by Play and Export; Esc cancels without executing either. */
public final class CustomLevelListScreen extends MenuScreen {
    public static final int PAGE_SIZE = 7;
    private final List<CustomLevelMenu.Entry> entries;
    private final CustomLevelMenu.Action action;
    private final Cooldown cooldown = Core.getCooldown(180);
    private int selected;
    private int result = -1;

    public CustomLevelListScreen(final int width, final int height, final int fps,
            final List<CustomLevelMenu.Entry> entries, final int selected,
            final CustomLevelMenu.Action action) {
        super(width, height, fps);
        if (entries.isEmpty()) throw new IllegalArgumentException("The library is empty");
        this.entries = List.copyOf(entries);
        this.selected = Math.max(0, Math.min(selected, entries.size() - 1));
        this.action = action;
        cooldown.reset();
    }

    public int getSelection() { return result; }

    @Override
    protected void update() {
        super.update();
        drawManager.initDrawing(this);
        drawManager.drawCustomLevelList(this, entries, selected, action);
        drawManager.completeDrawing(this);
        if (hovered >= 0) selected = hovered;
        if (!menuReady()) return;
        if (clicked >= 0) {
            result = clicked;
            isRunning = false;
            return;
        }
        if (clicked == PREVIOUS || clicked == NEXT) {
            int page = selected / PAGE_SIZE + (clicked == NEXT ? 1 : -1);
            selected = page * PAGE_SIZE;
            return;
        }
        if (scroll != 0) {
            selected = Math.max(0, Math.min(entries.size() - 1, selected + scroll));
        }
        if (clicked == BACK || backPressed) {
            isRunning = false;
            return;
        }
        if (confirmPressed) {
            result = selected;
            isRunning = false;
            return;
        }
        if (!cooldown.checkFinished()) return;
        if (inputManager.isKeyDown(KeyEvent.VK_UP) || inputManager.isKeyDown(KeyEvent.VK_W)) {
            selected = (selected + entries.size() - 1) % entries.size();
            cooldown.reset();
        } else if (inputManager.isKeyDown(KeyEvent.VK_DOWN) || inputManager.isKeyDown(KeyEvent.VK_S)) {
            selected = (selected + 1) % entries.size();
            cooldown.reset();
        }
    }
}
