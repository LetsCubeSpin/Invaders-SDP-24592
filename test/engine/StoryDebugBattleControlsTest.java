package engine;

import audio.AudioManager;
import event.LevelCompletedEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import screen.GameScreen;
import story.StoryProgress;

import static org.junit.jupiter.api.Assertions.*;

class StoryDebugBattleControlsTest {
    private static final int SHORTCUT = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;

    @Test
    void escapeCanResumeAndOnlyTheExactShortcutRequestsAReturn() {
        JPanel window = new JPanel();
        AtomicInteger menus = new AtomicInteger();
        StoryDebugBattleControls controls = new StoryDebugBattleControls(window, () -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            menus.incrementAndGet();
            return false;
        });
        send(window, KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, 0);
        assertFalse(controls.getAsBoolean());
        send(window, KeyEvent.KEY_RELEASED, KeyEvent.VK_ESCAPE, 0);
        assertFalse(controls.getAsBoolean());
        assertEquals(1, menus.get());
        // Escape used to dismiss the modal may be released after focus returns to the game.
        send(window, KeyEvent.KEY_RELEASED, KeyEvent.VK_ESCAPE, 0);
        assertFalse(controls.getAsBoolean());
        assertEquals(1, menus.get());
        for (int modifiers : new int[] {0, InputEvent.CTRL_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK,
                SHORTCUT | InputEvent.ALT_DOWN_MASK}) {
            send(window, KeyEvent.KEY_PRESSED, KeyEvent.VK_D, modifiers);
            assertFalse(controls.getAsBoolean());
        }
        send(window, KeyEvent.KEY_PRESSED, KeyEvent.VK_D, SHORTCUT);
        send(window, KeyEvent.KEY_PRESSED, KeyEvent.VK_D, SHORTCUT);
        send(window, KeyEvent.KEY_RELEASED, KeyEvent.VK_ESCAPE, 0);
        assertTrue(controls.getAsBoolean());
        assertFalse(controls.getAsBoolean());
        assertEquals(1, menus.get());
    }

    @ParameterizedTest
    @CsvSource({"1, false, true", "1, true, false", "7, true, true", "7, false, false"})
    void activeBattleReturnsWithoutCompletionOrRewards(int stage, boolean invincible, boolean useMenu) {
        JPanel window = new JPanel();
        AtomicInteger menus = new AtomicInteger();
        StoryDebugBattleControls controls = new StoryDebugBattleControls(window, () -> {
            menus.incrementAndGet();
            return true;
        });
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(1);
        progress.chooseSecond(1);
        AtomicInteger polls = new AtomicInteger();
        BooleanSupplier input = () -> {
            if (polls.incrementAndGet() == 2) {
                if (useMenu) {
                    send(window, KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, 0);
                    send(window, KeyEvent.KEY_RELEASED, KeyEvent.VK_ESCAPE, 0);
                } else {
                    send(window, KeyEvent.KEY_PRESSED, KeyEvent.VK_D, SHORTCUT);
                }
            }
            assertTrue(polls.get() <= 2, "Return must stop the active game loop");
            return controls.getAsBoolean();
        };
        GameScreen screen = GameScreen.forStoryDebugStage(new GameState(stage, 430, 3, 0, 0),
                progress, settings -> settings, 448, 520, 60, new StoryDebugModifiers(invincible, 15, 1, 1), input);
        AtomicInteger clears = new AtomicInteger();
        screen.getGameplayEvents().subscribe(LevelCompletedEvent.class, e -> clears.incrementAndGet());
        boolean wasMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        try {
            screen.initialize();
            assertEquals(GameScreen.RETURN_TO_STORY_DEBUG, screen.run());
            assertEquals(useMenu ? 1 : 0, menus.get());
            assertFalse(screen.isStageCleared());
            assertEquals(0, clears.get());
            assertEquals(430, screen.getGameState().getScore());
            assertEquals(15, screen.getGameState().getLivesRemaining());
        } finally {
            AudioManager.stopBGM();
            AudioManager.setMuted(wasMuted);
        }
    }

    private static void send(JPanel window, int type, int key, int modifiers) {
        KeyEvent event = new KeyEvent(window, type, System.currentTimeMillis(),
                modifiers, key, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : window.getKeyListeners()) {
            if (type == KeyEvent.KEY_RELEASED)
                listener.keyReleased(event);
            else
                listener.keyPressed(event);
        }
    }
}
