package engine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import audio.AudioManager;
import boss.BossManager;
import entity.Boss;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import event.DefeatedEnemyType;
import entity.Ship;
import entity.ShipType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import screen.GameScreen;
import shop.PreviewPreferencesFactory;
import story.StoryProgress;
import story.StoryRoute;
import story.StoryStage;
import story.StoryStageData;

class StoryDebugLauncherTest {
    static Stream<Arguments> selections() {
        var cases = Stream.<Arguments>builder();
        for (StoryRoute route : StoryRoute.values())
            for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++)
                for (var difficulty : StoryDebugSelection.Difficulty.values())
                    for (ShipType ship : ShipType.values())
                        cases.add(Arguments.of(route, stage, difficulty, ship));
        return cases.build();
    }

    @ParameterizedTest(name = "{0} stage {1} {2} {3}")
    @MethodSource("selections")
    void reconstructsOnlyPastChoices(StoryRoute route, int stage, StoryDebugSelection.Difficulty difficulty,
            ShipType ship) {
        var selection = StoryDebugSelection.parse(new String[]{route.name(), "" + stage, difficulty.name(), ship.name()});
        assertEquals(new StoryDebugSelection(route, stage, difficulty, ship), selection);
        StoryProgress p = selection.progress();
        assertNotSame(p, selection.progress());
        assertEquals(stage >= 2, p.hasFirstChoice());
        assertEquals(stage >= 4, p.hasSecondChoice());
        if (stage >= 2) assertEquals(route.getFirstChoice(), p.getFirstChoice());
        if (stage >= 4) assertEquals(route, p.getRoute());
        assertEquals(stage == 3 ? route.getStageThreeBoss() : stage == 7 ? route.getFinalBoss() : null,
                p.bossForStage(stage));
        assertEquals(stage <= 3 ? ship.getBulletType() : route.getWeapon(), p.weaponForStage(stage, ship.getBulletType()));
        assertEquals(stage >= 4 && route.givesGrenades(), p.givesGrenadesOnStage(stage));
        assertTrue(StoryStageData.get(stage, p).getWaveCount() > 0);
    }

    @Test
    void rejectsInvalidOrMissingFields() {
        for (String[] invalid : new String[][]{
                {}, {"", "1", "EASY", "TANK"}, {"INVALID", "1", "EASY", "TANK"},
                {"TRUCE", "0", "NORMAL", "TANK"}, {"TRUCE", "8", "NORMAL", "TANK"},
                {"TRUCE", "3.5", "NORMAL", "TANK"}, {"TRUCE", "3", "EXTREME", "TANK"},
                {"TRUCE", "3", "", "TANK"}, {"TRUCE", "3", "HARD", null}, {"TRUCE", "3", "HARD", "UNKNOWN"}}) {
            assertThrows(IllegalArgumentException.class, () -> StoryDebugSelection.parse(invalid));
        }
    }

    @Test
    void restartResetsPreferencesAndPreservesNormalFiles(@TempDir Path dir) throws Exception {
        Path profile = dir.resolve(".space-invaders/records-achievements.properties");
        Files.createDirectories(profile.getParent());
        List<Path> saves = List.of(dir.resolve("scores"), dir.resolve(FileManager.ASSETS_FILENAME), profile);
        for (Path save : saves) Files.writeString(save, "sentinel-" + save.getFileName());
        for (String scenario : List.of("disabled", "normalPreferences", "battle", "battle")) {
            child(dir, scenario);
            for (Path save : saves) assertEquals("sentinel-" + save.getFileName(), Files.readString(save));
        }
        try (var files = Files.walk(dir)) {
            assertEquals(4, files.filter(Files::isRegularFile).count(), "Only sentinels and the process log");
        }
    }

    @Test
    void latestMainBossCCompletesItsSummonLifecycle(@TempDir Path dir) throws Exception {
        child(dir, "bossC");
    }

    private static void child(Path dir, String scenario) throws Exception {
        String cp = System.getProperty("invaders.test.classpath", System.getProperty("java.class.path"));
        String absoluteCp = String.join(File.pathSeparator, Arrays.stream(cp.split(File.pathSeparator))
                .map(path -> Path.of(path).toAbsolutePath().toString()).toList());
        var args = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-Duser.home=" + dir, "-Dinvaders.storageDir=" + dir));
        if (!scenario.equals("disabled")) args.add("-Dinvaders.storyDebug=true");
        if (!scenario.equals("normalPreferences"))
            args.add("-Djava.util.prefs.PreferencesFactory=" + PreviewPreferencesFactory.class.getName());
        args.addAll(List.of("-cp", absoluteCp, StoryDebugLauncherTest.class.getName(), scenario));
        Path log = dir.resolve("child.log");
        Process p = new ProcessBuilder(args).directory(dir.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            assertTrue(p.waitFor(45, TimeUnit.SECONDS), "Story debug subprocess timed out");
            assertEquals(0, p.exitValue(), Files.readString(log, java.nio.charset.StandardCharsets.ISO_8859_1));
        } finally {
            if (p.isAlive()) p.destroyForcibly().waitFor();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("bossC")) {
            StoryDebugLauncher.requireDebugSession();
            checkBossC();
            return;
        }
        if (!args[0].equals("battle")) {
            assertThrows(IllegalStateException.class, () -> StoryDebugLauncher.main(new String[0]));
            return;
        }
        assertEquals(GameplayConfig.NORMAL, GameplayConfig.getDifficulty());
        assertNull(Core.getRecordsService());
        AudioManager.setMuted(true);
        for (StoryRoute route : StoryRoute.values()) {
            for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
                for (var difficulty : StoryDebugSelection.Difficulty.values()) {
                    for (ShipType ship : ShipType.values()) {
                        var choice = new StoryDebugSelection(route, stage, difficulty, ship);
                        GameScreen screen = StoryDebugLauncher.createScreen(choice, 448, 520);
                        screen.initialize();
                        assertNull(screen.getActiveRunId());
                        assertEquals(stage, screen.getGameState().getLevel());
                        assertEquals(ship, ((Ship) field(screen, "ship")).getShipType());
                        assertEquals(ship.getMaxLives(), screen.getGameState().getLivesRemaining());
                        assertEquals(difficulty.setting, field(screen, "difficulty"));
                        StoryStage data = StoryStageData.get(stage, choice.progress());
                        assertSame(data, field(screen, "storyStage"));
                        double multiplier = switch (difficulty) {
                            case EASY -> 1.25;
                            case NORMAL -> 1.0;
                            case HARD -> 0.8;
                        };
                        GameSettings expected = Core.applyDifficulty(data.getSettings(), multiplier);
                        GameSettings actual = (GameSettings) field(screen, "gameSettings");
                        assertEquals(expected.getBaseSpeed(), actual.getBaseSpeed());
                        assertEquals(expected.getShootingFrecuency(), actual.getShootingFrecuency());
                        Method findBoss = GameScreen.class.getDeclaredMethod("getBossOnScreen");
                        findBoss.setAccessible(true);
                        Boss boss = (Boss) findBoss.invoke(screen);
                        if (stage != 3 && stage != 7) {
                            assertNull(boss);
                            // Exercise every configured wave through the production progression method.
                            for (int wave = 0; wave < data.getWaveCount(); wave++) {
                                clearFormation(screen);
                                call(screen, "advanceStageIfReady");
                                assertEquals(wave == data.getWaveCount() - 1, screen.isStageCleared());
                            }
                        } else {
                            assertEquals(choice.progress().bossForStage(stage), boss.getType());
                            if (stage != 7 || route != StoryRoute.HOMECOMING
                                    || difficulty != StoryDebugSelection.Difficulty.HARD
                                    || ship != ShipType.TANK) continue;
                            Method hit = GameScreen.class.getDeclaredMethod("damageBoss", int.class);
                            hit.setAccessible(true);
                            hit.invoke(screen, boss.getMaxHealth() * 10);
                            // Run the normal loop through the boss explosion, clear event and screen exit.
                            screen.run();
                            assertTrue(screen.isStageCleared());
                        }
                    }
                }
            }
        }
        System.out.println("PASS: 252 actual Story initializations, all non-boss waves, isolated restart.");
        AudioManager.stopBGM();
    }

    private static void checkBossC() throws Exception {
        // StoryRoute still maps HYBRID to A. Use main's real campaign mapping to exercise C;
        // do not replace the story route or fabricate a debug-only boss mapping.
        AudioManager.setMuted(true);
        GameScreen screen = new GameScreen(new GameState(7, 0, 3, 0, 0),
                new GameSettings(1, 1, 60, 2000), false, 448, 520, 60);
        screen.initialize();
        BossManager manager = (BossManager) field(screen, "bossManager");
        Boss boss = (Boss) call(screen, "getBossOnScreen");
        assertEquals(Boss.BossType.BOSS_C, boss.getType());
        assertNull(screen.getActiveRunId());
        expire(field(screen, "inputDelay"));
        call(screen, "update");
        for (int phase = 1; phase <= 3; phase++) {
            if (phase > 1) {
                boss.takeDamage(250);
                manager.sync();
            }
            assertEquals(phase, boss.getPhase());
            // Advance just the clock: requests, conversion to enemies and cleanup remain production code.
            expire(field(manager.getAttackSystem(), "summonCooldown"));
            @SuppressWarnings("unchecked")
            List<EnemyShip> minions = (List<EnemyShip>) field(screen, "summonedEnemies");
            int before = minions.size();
            call(screen, "update");
            assertEquals(before + (phase == 2 ? 3 : 2), minions.size());
            DefeatedEnemyType expected = new DefeatedEnemyType[]{
                    DefeatedEnemyType.A, DefeatedEnemyType.B, DefeatedEnemyType.C}[phase - 1];
            for (EnemyShip minion : minions.subList(before, minions.size()))
                assertEquals(expected, minion.getDefeatedType());
        }
        Method hit = GameScreen.class.getDeclaredMethod("damageBoss", int.class);
        hit.setAccessible(true);
        hit.invoke(screen, boss.getMaxHealth() * 10);
        assertTrue(((List<?>) field(screen, "summonedEnemies")).isEmpty());
        clearFormation(screen);
        expire(field(screen, "bossExplosionCooldown"));
        call(screen, "update");
        assertTrue(screen.isStageCleared());
        assertFalse(manager.getAttackSystem().isActive());
        AudioManager.stopBGM();
        System.out.println("PASS: BOSS_C in GameScreen, A/B/C summons, defeat cleanup and stage clear.");
    }

    private static void clearFormation(GameScreen screen) throws Exception {
        EnemyShipFormation formation = (EnemyShipFormation) field(screen, "enemyShipFormation");
        var enemies = new ArrayList<EnemyShip>();
        for (EnemyShip enemy : formation) enemies.add(enemy);
        for (EnemyShip enemy : enemies) formation.destroy(enemy);
    }

    private static void expire(Object cooldown) throws Exception {
        Field time = Cooldown.class.getDeclaredField("time");
        time.setAccessible(true);
        time.setLong(cooldown, 0L);
    }

    private static Object call(GameScreen screen, String name) throws Exception {
        Method method = GameScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(screen);
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException missing) {
                // Screen owns the input-delay timer.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
