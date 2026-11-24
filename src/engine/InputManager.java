package engine;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * Manages keyboard input for the provided screen.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public final class InputManager implements KeyListener, MouseListener, MouseMotionListener, MouseWheelListener {

    /** Releases controls whose release events may have been consumed by a modal dialog. */
    public void releaseAllInputs() {
        java.util.Arrays.fill(keys, false);
        mousePressed = false;
        rightMousePressed = false;
        clearLastPressedKey();
    }

	/** Number of recognised keys. */
	private static final int NUM_KEYS = 256;
	/** Array with the jeys marked as pressed or not. */
	private static boolean[] keys;

	private volatile int lastPressedKey = KeyEvent.VK_UNDEFINED;
	/** Singleton instance of the class. */
	private static InputManager instance;

	/** Mouse position relative to the window content. */
	private volatile int mouseX = -1;
	private volatile int mouseY = -1;

	/** A distinct press is retained even when released between two rendered frames. */
	public record MenuPress(int x, int y) {}
	private volatile MenuPress lastMenuPress;
	private volatile int backPressCount;
	private volatile int confirmPressCount;

	public int getConfirmPressCount() {
		return confirmPressCount;
	}

	public int getBackPressCount() {
		return backPressCount;
	}

	public MenuPress getLastMenuPress() {
		return lastMenuPress;
	}

	/** Left mouse button state. */
	private volatile boolean mousePressed = false;

	/** Right mouse button state. Used as a "go back" input like Esc. */
	private volatile boolean rightMousePressed = false;

	/**
	 * Private constructor.
	 */
	private InputManager() {
		keys = new boolean[NUM_KEYS];
	}

	/**
	 * Returns shared instance of InputManager.
	 *
	 * @return Shared instance of InputManager.
	 */
	protected static InputManager getInstance() {
		if (instance == null)
			instance = new InputManager();
		return instance;
	}

	/**
	 * Returns true if the provided key is currently pressed.
	 *
	 * @param keyCode
	 *                Key number to check.
	 * @return Key state.
	 */
	public boolean isKeyDown(final int keyCode) {
		return keys[keyCode];
	}

	public int consumeLastPressedKey() {
		int keyCode = lastPressedKey;
		lastPressedKey = KeyEvent.VK_UNDEFINED;
		return keyCode;
	}

	public void clearLastPressedKey() {
		lastPressedKey = KeyEvent.VK_UNDEFINED;
	}

	/**
	 * Changes the state of the key to pressed.
	 *
	 * @param key
	 *            Key pressed.
	 */
	@Override
	public void keyPressed(final KeyEvent key) {
		if (key.getKeyCode() >= 0 && key.getKeyCode() < NUM_KEYS) {
			if (!keys[key.getKeyCode()]) {
				lastPressedKey = key.getKeyCode();
				if (lastPressedKey == KeyEvent.VK_ESCAPE) backPressCount++;
				if (lastPressedKey == KeyEvent.VK_ENTER || lastPressedKey == KeyEvent.VK_SPACE) confirmPressCount++;
			}
			keys[key.getKeyCode()] = true;
		}
	}

	/**
	 * Changes the state of the key to not pressed.
	 *
	 * @param key
	 *            Key released.
	 */
	@Override
	public void keyReleased(final KeyEvent key) {
		if (key.getKeyCode() >= 0 && key.getKeyCode() < NUM_KEYS)
			keys[key.getKeyCode()] = false;
	}

	/**
	 * Does nothing.
	 *
	 * @param key
	 *            Key typed.
	 */
	@Override
	public void keyTyped(final KeyEvent key) {

	}

	public int getMouseX() {
		return mouseX;
	}

	public int getMouseY() {
		return mouseY;
	}

	public boolean isMousePressed() {
		return mousePressed;
	}

	public boolean isRightMousePressed() {
		return rightMousePressed;
	}

	/**
	 * Returns true if a "go back" input is held: Esc or the right mouse button.
	 *
	 * @return Back input state.
	 */
	public boolean isBackDown() {
		return keys[KeyEvent.VK_ESCAPE] || rightMousePressed;
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			mouseX = e.getX();
			mouseY = e.getY();
			lastMenuPress = new MenuPress(mouseX, mouseY);
			mousePressed = true;
		} else if (e.getButton() == MouseEvent.BUTTON3) {
			rightMousePressed = true;
			backPressCount++;
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			mousePressed = false;
		} else if (e.getButton() == MouseEvent.BUTTON3) {
			rightMousePressed = false;
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
	}

	@Override
	public void mouseExited(MouseEvent e) {
		mouseX = -1;
		mouseY = -1;
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
	}

    private volatile int wheelRotation;

    public int getWheelRotation() {
        return wheelRotation;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
        wheelRotation += event.getWheelRotation();
    }

	@Override
	public void mouseClicked(MouseEvent e) {
	}
}
