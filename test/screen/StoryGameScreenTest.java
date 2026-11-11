package screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import engine.CampaignUpgrades;
import engine.GameSettings;
import engine.GameState;
import entity.Boss;
import entity.BulletType;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.Ship;
import entity.ShipType;
import event.GameplayEventListener;
import event.LevelCompletedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import story.StoryProgress;
import story.StoryRoute;
import story.StoryStage;
import story.StoryStageData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryGameScreenTest {

    @AfterEach
    void stopAudio() {
        audio.AudioManager.stopBGM();
    }

    private static StoryProgress progress(final StoryRoute route) {
        StoryProgress progress = new StoryProgress();
        progress.chooseFirst(route.getFirstChoice());
        progress.chooseSecond(route.getSecondChoice());
        return progress;
    }

    private static GameScreen story(final int stage, final StoryRoute route,
            final ShipType ship, final UnaryOperator<GameSettings> difficulty) {
        GameScreen screen = GameScreen.forStoryStage(
                new GameState(stage, 0, 3, 0, 0), progress(route), difficulty,
                false, 448, 520, 60, ship, null, CampaignUpgrades.none());
        audio.AudioManager.setMuted(true);
        screen.initialize();
        return screen;
    }

    private static GameScreen story(final int stage, final StoryRoute route) {
        return story(stage, route, ShipType.STANDARD,
                UnaryOperator.identity());
    }

    @Test
    void onlyStagesThreeAndSevenHaveABoss() throws Exception {
        for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
            Boss boss = boss(story(stage, StoryRoute.HYBRID));
            assertEquals(stage == 3 || stage == 7, boss != null,
                    "stage " + stage);
        }
    }

    @Test
    void bossFollowsTheRoute() throws Exception {
        for (StoryRoute route : StoryRoute.values()) {
            assertEquals(route.getStageThreeBoss(),
                    boss(story(3, route)).getType(), route + " stage 3");
            assertEquals(route.getFinalBoss(),
                    boss(story(7, route)).getType(), route + " stage 7");
        }
    }

    @Test
    void formationUsesTheStoryRows() throws Exception {
        StoryStage data = StoryStageData.get(2, progress(StoryRoute.HYBRID));
        EnemyShipFormation formation = formation(story(2, StoryRoute.HYBRID));
        GameSettings settings = data.getSettings();
        assertEquals(settings.getFormationWidth()
                * settings.getFormationHeight(), formation.getTotalCount());
        // Rows are 40 pixels apart, starting at the formation's start Y.
        for (EnemyShip enemy : formation) {
            int row = (enemy.getPositionY() - settings.getInitialY()) / 40;
            assertEquals(data.getRows()[row].getSpriteType(),
                    enemy.getSpriteType(), "row " + row);
        }
    }

    @Test
    void difficultyIsAppliedToTheStage() throws Exception {
        GameScreen screen = story(1, StoryRoute.HYBRID, ShipType.STANDARD,
                settings -> new GameSettings(settings.getFormationWidth(),
                        settings.getFormationHeight(), 99, 999,
                        settings.getInitialX(), settings.getInitialY()));
        GameSettings used = (GameSettings) read(screen, "gameSettings");
        assertEquals(99, used.getBaseSpeed());
        assertEquals(999, used.getShootingFrecuency());
    }

    @Test
    void bossDefeatRemovesTheOtherEnemiesWithoutScore() throws Exception {
        GameScreen screen = story(3, StoryRoute.HYBRID);
        Boss boss = boss(screen);
        int scoreBefore = screen.getGameState().getScore();
        damageBoss(screen, boss.getMaxHealth() * 2);

        assertTrue(boss.isDefeated());
        assertTrue(formation(screen).isEmpty());
        assertEquals(scoreBefore + boss.getType().getPointValue(),
                screen.getGameState().getScore());
    }

    @Test
    void campaignBossDefeatKeepsTheOtherEnemies() throws Exception {
        GameScreen screen = new GameScreen(new GameState(3, 0, 3, 0, 0),
                new GameSettings(2, 2, 60, 2000), false, 448, 520, 60);
        audio.AudioManager.setMuted(true);
        screen.initialize();
        Boss boss = boss(screen);
        damageBoss(screen, boss.getMaxHealth() * 2);

        assertTrue(boss.isDefeated());
        assertFalse(formation(screen).isEmpty());
    }

    @Test
    void strongerFinalBossTakesLessDamage() throws Exception {
        Boss normal = boss(screenAfterHit(3, StoryRoute.HUMAN_VICTORY, 30));
        assertEquals(normal.getMaxHealth() - 30, normal.getCurrentHealth());

        Boss stronger = boss(screenAfterHit(7, StoryRoute.HUMAN_VICTORY, 30));
        assertEquals(stronger.getMaxHealth() - 20,
                stronger.getCurrentHealth());
    }

    @Test
    void everyShipUsesTheNormalWeapon() throws Exception {
        for (ShipType type : ShipType.values())
            for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
                Ship ship = (Ship) read(story(stage, StoryRoute.TRUCE, type,
                        UnaryOperator.identity()), "ship");
                assertEquals(BulletType.NORMAL, ship.getCurrentBulletType(),
                        type + " stage " + stage);
            }
    }

    @Test
    void campaignKeepsTheShipWeapon() throws Exception {
        GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0),
                new GameSettings(1, 1, 60, 2000), false, 448, 520, 60,
                ShipType.TANK, null, CampaignUpgrades.none());
        audio.AudioManager.setMuted(true);
        screen.initialize();
        Ship ship = (Ship) read(screen, "ship");
        assertEquals(ShipType.TANK.getBulletType(),
                ship.getCurrentBulletType());
    }

    @Test
    void asteroidFieldDropsObstaclesMoreOften() throws Exception {
        int normal = obstacleInterval(story(1, StoryRoute.HOMECOMING));
        int field = obstacleInterval(story(2, StoryRoute.HOMECOMING));
        assertTrue(field < normal);
        assertEquals(normal, obstacleInterval(story(2, StoryRoute.HYBRID)));
    }

    @Test
    void clearingAStoryStagePublishesLevelCompleted() throws Exception {
        GameScreen screen = story(1, StoryRoute.HYBRID);
        List<LevelCompletedEvent> events = new ArrayList<LevelCompletedEvent>();
        screen.getGameplayEvents().subscribe(LevelCompletedEvent.class,
                new GameplayEventListener<LevelCompletedEvent>() {
                    public void onEvent(final LevelCompletedEvent event) {
                        events.add(event);
                    }
                });
        destroyAll(formation(screen));
        screen.advanceStageIfReady();

        assertTrue(screen.isStageCleared());
        assertEquals(1, events.size());
        assertEquals(1, events.get(0).getLevelId());
    }

    @Test
    void stagesWithTwoWavesPlayBoth() throws Exception {
        GameScreen screen = story(4, StoryRoute.HYBRID);
        assertEquals(2, StoryStageData.get(4,
                progress(StoryRoute.HYBRID)).getWaveCount());
        destroyAll(formation(screen));
        screen.advanceStageIfReady();
        assertFalse(screen.isStageCleared());
        assertFalse(formation(screen).isEmpty());
        destroyAll(formation(screen));
        screen.advanceStageIfReady();
        assertTrue(screen.isStageCleared());
    }

    private static GameScreen screenAfterHit(final int stage,
            final StoryRoute route, final int damage) throws Exception {
        GameScreen screen = story(stage, route);
        damageBoss(screen, damage);
        return screen;
    }

    private static void destroyAll(final EnemyShipFormation formation) {
        List<EnemyShip> alive = new ArrayList<EnemyShip>();
        for (EnemyShip enemy : formation)
            if (!enemy.isDestroyed())
                alive.add(enemy);
        for (EnemyShip enemy : alive)
            formation.destroy(enemy);
    }

    private static int obstacleInterval(final GameScreen screen)
            throws Exception {
        return (Integer) call(screen, "fallingObstacleInterval");
    }

    private static Boss boss(final GameScreen screen) throws Exception {
        return (Boss) call(screen, "getBossOnScreen");
    }

    private static EnemyShipFormation formation(final GameScreen screen)
            throws Exception {
        return (EnemyShipFormation) read(screen, "enemyShipFormation");
    }

    private static void damageBoss(final GameScreen screen, final int damage)
            throws Exception {
        Method method = GameScreen.class.getDeclaredMethod("damageBoss",
                int.class);
        method.setAccessible(true);
        method.invoke(screen, damage);
    }

    private static Object call(final GameScreen screen, final String name)
            throws Exception {
        Method method = GameScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(screen);
    }

    private static Object read(final Object target, final String name)
            throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
