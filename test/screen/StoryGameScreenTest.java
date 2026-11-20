package screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

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
                false, 448, 520, 60, ship, null);
        audio.AudioManager.setMuted(true);
        screen.initialize();
        return screen;
    }

    private static GameScreen story(final int stage, final StoryRoute route) {
        return story(stage, route, ShipType.STANDARD,
                UnaryOperator.identity());
    }

    @Test
    void stageOneWaitsForThePilotLineAndThenStartsAtOnce() {
        GameScreen screen = story(1, StoryRoute.HYBRID);
        // story() has already initialized the screen, which sets the delay.
        assertFalse(screen.inputDelay.checkFinished());
        long now = System.currentTimeMillis();
        assertTrue(screen.isStoryIntroHolding());
        screen.updateStoryIntro(false, now);
        // Space while typing shows the whole line but does not start yet.
        screen.updateStoryIntro(true, now + 100);
        assertTrue(screen.isStoryIntroHolding());
        screen.updateStoryIntro(false, now + 150);
        // Space once the line is complete starts the stage with no countdown.
        screen.updateStoryIntro(true, now + 200);
        assertFalse(screen.isStoryIntroHolding());
    }

    @Test
    void stagesWithoutALineShowTheTitleThenStartAtOnce() {
        GameScreen screen = story(2, StoryRoute.HYBRID);
        long now = System.currentTimeMillis();
        assertTrue(screen.isStoryIntroHolding());
        screen.updateStoryIntro(false, now + 500);
        assertTrue(screen.isStoryIntroHolding());
        screen.updateStoryIntro(false, now + 2000);
        assertFalse(screen.isStoryIntroHolding());
    }

    /** Clears the stage and starts the question, ready to choose. */
    private static GameScreen choosing(final int stage, final int shipX)
            throws Exception {
        GameScreen screen = story(stage, StoryRoute.HYBRID);
        screen.setStoryChoiceEnabled(true);
        write(screen, "stageCleared", true);
        write(screen, "levelFinished", true);
        ((Ship) read(screen, "ship")).setPositionX(shipX);
        // Typing starts, then Space finishes it, then Space starts choosing.
        assertTrue(screen.updateStoryQuestion(0, false, false, false, false));
        screen.updateStoryQuestion(10, true, false, false, false);
        screen.updateStoryQuestion(20, false, false, false, false);
        screen.updateStoryQuestion(30, true, false, false, false);
        screen.updateStoryQuestion(40, false, false, false, false);
        return screen;
    }

    private static long flyForward(final GameScreen screen) {
        long now = 50;
        for (int frame = 0; frame < 400 && screen.getStoryChoice() < 0;
                frame++, now += 16)
            screen.updateStoryQuestion(now, false, true, false, false);
        return now;
    }

    @Test
    void flyingIntoTheLeftHalfChoosesZero() throws Exception {
        GameScreen screen = choosing(1, 60);
        flyForward(screen);
        assertEquals(0, screen.getStoryChoice());
    }

    @Test
    void flyingIntoTheRightHalfChoosesOne() throws Exception {
        GameScreen screen = choosing(1, 340);
        flyForward(screen);
        assertEquals(1, screen.getStoryChoice());
    }

    @Test
    void secondQuestionIsAskedAfterTheStageThreeBoss() throws Exception {
        GameScreen screen = choosing(3, 340);
        flyForward(screen);
        assertEquals(1, screen.getStoryChoice());
    }

    @Test
    void forwardOnlyWorksWhileChoosing() throws Exception {
        GameScreen screen = story(1, StoryRoute.HYBRID);
        screen.setStoryChoiceEnabled(true);
        write(screen, "stageCleared", true);
        write(screen, "levelFinished", true);
        Ship ship = (Ship) read(screen, "ship");
        int y = ship.getPositionY();
        // Still typing the question, so the forward key does nothing.
        screen.updateStoryQuestion(0, false, true, false, false);
        screen.updateStoryQuestion(16, false, true, false, false);
        assertEquals(y, ship.getPositionY());
        assertEquals(-1, screen.getStoryChoice());
    }

    @Test
    void theChosenHalfStaysBrieflyThenTheScreenCloses() throws Exception {
        GameScreen screen = choosing(1, 60);
        long now = flyForward(screen);
        assertTrue(screen.updateStoryQuestion(now + 100, false, false, false,
                false));
        assertFalse(screen.updateStoryQuestion(now + 2000, false, false,
                false, false));
    }

    @Test
    void noQuestionWhenItIsOffOrTheStageHasNone() throws Exception {
        GameScreen off = story(1, StoryRoute.HYBRID);
        write(off, "stageCleared", true);
        assertFalse(off.updateStoryQuestion(0, false, false, false, false));

        GameScreen noQuestion = story(2, StoryRoute.HYBRID);
        noQuestion.setStoryChoiceEnabled(true);
        write(noQuestion, "stageCleared", true);
        assertFalse(noQuestion.updateStoryQuestion(0, false, false, false,
                false));
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
                ShipType.STANDARD, null);
        audio.AudioManager.setMuted(true);
        screen.initialize();
        Ship ship = (Ship) read(screen, "ship");
        assertEquals(ShipType.STANDARD.getBulletType(),
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

    private static void write(final Object target, final String name,
            final Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
