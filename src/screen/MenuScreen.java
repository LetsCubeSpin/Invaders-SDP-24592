package screen;

import engine.InputManager.MenuPress;
import java.util.function.IntBinaryOperator;

/** Pointer input for menu controls, using the bounds drawn on the current frame. */
public abstract class MenuScreen extends Screen {
    public static final int NONE = -1;
    public static final int BACK = -2;
    public static final int PREVIOUS = -3;
    public static final int NEXT = -4;
    private int lastX;
    private int lastY;
    private int lastBack;
    private int lastConfirm;
    protected boolean confirmPressed;
    protected boolean backPressed;
    private boolean moved;
    private int lastWheel;
    protected int scroll;
    private MenuPress lastPress;
    private MenuPress press;
    protected int hovered = NONE;
    protected int clicked = NONE;

    protected MenuScreen(int width, int height, int fps) {
        super(width, height, fps);
        lastConfirm = inputManager.getConfirmPressCount();
        lastBack = inputManager.getBackPressCount();
        lastWheel = inputManager.getWheelRotation();
        lastPress = inputManager.getLastMenuPress();
        lastX = inputManager.getMouseX();
        lastY = inputManager.getMouseY();
    }

    @Override
    protected void update() {
        super.update();
        int x = inputManager.getMouseX();
        int y = inputManager.getMouseY();
        moved = x != lastX || y != lastY;
        lastX = x;
        lastY = y;
        int confirm = inputManager.getConfirmPressCount();
        confirmPressed = lastConfirm != confirm;
        lastConfirm = confirm;
        int back = inputManager.getBackPressCount();
        backPressed = lastBack != back;
        lastBack = back;
        int wheel = inputManager.getWheelRotation();
        scroll = wheel - lastWheel;
        lastWheel = wheel;
        MenuPress latest = inputManager.getLastMenuPress();
        press = latest != lastPress ? latest : null;
        lastPress = latest;
        hovered = clicked = NONE;
    }

    /** Register an enabled control as it is drawn. A stationary pointer leaves keyboard focus alone. */
    public final boolean menuTarget(int id, int x, int y, int width, int height) {
        boolean inside = lastX >= x && lastX < x + width && lastY >= y && lastY < y + height;
        if (inside && moved) hovered = id;
        if (press != null && press.x() >= x && press.x() < x + width
                && press.y() >= y && press.y() < y + height) clicked = id;
        return inside;
    }

    protected final void readTargetAt(IntBinaryOperator targetAt) {
        if (moved) hovered = targetAt.applyAsInt(lastX, lastY);
        if (press != null) clicked = targetAt.applyAsInt(press.x(), press.y());
    }

    /** Fresh presses belong to this screen even during the opening key-repeat delay. */
    protected final boolean menuReady() {
        return clicked != NONE || backPressed || confirmPressed || inputDelay.checkFinished();
    }

    protected final int inputPressX() {
        return press.x();
    }

}
