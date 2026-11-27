package screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.function.UnaryOperator;

import boss.BossAttackConfig;
import boss.BossAttackSystem;
import boss.BossManager;
import engine.GameSettings;
import engine.GameState;
import engine.GameplayConfig;
import entity.Boss;
import entity.ShipType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import story.StoryProgress;
import story.StoryRoute;

/** Story bosses wait longer between attacks; campaign bosses do not. */
class StoryBossIntervalTest {

    @AfterEach
    void stopAudio() {
        audio.AudioManager.stopBGM();
    }

    private static int interval(final Boss.BossType type, final double scale) {
        return new BossAttackSystem(BossAttackConfig.forType(type).withIntervalScale(scale),
                new Random(1)).getCurrentIntervalMilliseconds();
    }

    private static BossManager bossManager(final GameScreen screen) throws Exception {
        Field field = GameScreen.class.getDeclaredField("bossManager");
        field.setAccessible(true);
        return (BossManager) field.get(screen);
    }

    @Test
    void storyBossesWaitLonger() throws Exception {
        for (StoryRoute route : StoryRoute.values()) {
            StoryProgress progress = new StoryProgress();
            progress.chooseFirst(route.getFirstChoice());
            progress.chooseSecond(route.getSecondChoice());
            for (int stage : new int[] {3, 7}) {
                GameScreen screen = GameScreen.forStoryStage(new GameState(stage, 0, 3, 0, 0),
                        progress, UnaryOperator.identity(), false, 448, 520, 60,
                        ShipType.STANDARD, null);
                audio.AudioManager.setMuted(true);
                screen.initialize();
                assertEquals(GameplayConfig.getStoryBossIntervalScale(),
                        screen.bossAttackIntervalScale());
                BossManager manager = bossManager(screen);
                Boss.BossType type = manager.getBoss().getType();
                assertEquals(interval(type, GameplayConfig.getStoryBossIntervalScale()),
                        manager.getAttackSystem().getCurrentIntervalMilliseconds(),
                        route + " stage " + stage);
            }
        }
    }

    @Test
    void campaignBossesKeepTheirInterval() throws Exception {
        for (int level : new int[] {3, 5, 7}) {
            GameScreen screen = new GameScreen(new GameState(level, 0, 3, 0, 0),
                    new GameSettings(5, 4, 60, 2000), false, 448, 520, 60);
            audio.AudioManager.setMuted(true);
            screen.initialize();
            assertEquals(1.0, screen.bossAttackIntervalScale());
            BossManager manager = bossManager(screen);
            Boss.BossType type = manager.getBoss().getType();
            assertEquals(interval(type, 1.0),
                    manager.getAttackSystem().getCurrentIntervalMilliseconds(),
                    "campaign stage " + level);
        }
    }
}
