package engine;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Input owned by a single isolated battle window. Only the game thread ends a battle. */
final class StoryDebugBattleControls extends KeyAdapter implements BooleanSupplier {
    private static final int SHORTCUT_MODIFIERS = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
    private static final String RETURN = "개발자 도구로 복귀";
    private enum Request { MENU, RETURN }

    private final AtomicReference<Request> pending = new AtomicReference<>();
    private final BooleanSupplier menu;
    private boolean escapePressedHere;

    StoryDebugBattleControls(Frame frame) {
        this(frame, () -> showMenu(frame));
    }

    StoryDebugBattleControls(Component window, BooleanSupplier menu) {
        this.menu = menu;
        window.addKeyListener(this);
    }

    @Override
    public void keyPressed(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            escapePressedHere = event.getModifiersEx() == 0;
        } else if (event.getKeyCode() == KeyEvent.VK_D && event.getModifiersEx() == SHORTCUT_MODIFIERS) {
            pending.set(Request.RETURN);
            event.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
        // Opening on release keeps a held Escape from closing or reopening the menu.
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (escapePressedHere && event.getModifiersEx() == 0) {
                pending.compareAndSet(null, Request.MENU);
                event.consume();
            }
            escapePressedHere = false;
        }
    }

    @Override
    public boolean getAsBoolean() {
        Request request = pending.getAndSet(null);
        if (request != Request.MENU) return request == Request.RETURN;
        // The game loop waits while the EDT handles the modal menu and its keyboard input.
        FutureTask<Boolean> decision = new FutureTask<>(() -> {
            try {
                return menu.getAsBoolean();
            } finally {
                Core.getInputManager().releaseAllInputs();
            }
        });
        SwingUtilities.invokeLater(decision);
        try {
            return decision.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the debug battle menu", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not display the debug battle menu", e.getCause());
        }
    }

    private static boolean showMenu(Frame frame) {
        Object[] options = {"계속하기", RETURN};
        JOptionPane pane = new JOptionPane("복귀 단축키   Ctrl + Shift + D",
                JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        JDialog dialog = pane.createDialog(frame, "Story debug");
        dialog.getRootPane().registerKeyboardAction(event -> {
            pane.setValue(RETURN);
            dialog.setVisible(false);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_D, SHORTCUT_MODIFIERS), JComponent.WHEN_IN_FOCUSED_WINDOW);
        try {
            dialog.setVisible(true);
            return RETURN.equals(pane.getValue());
        } finally {
            dialog.dispose();
        }
    }
}
