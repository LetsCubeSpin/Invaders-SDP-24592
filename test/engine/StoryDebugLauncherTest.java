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
import entity.Boss;
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
        if (!args[0].equals("battle")) {
            assertThrows(IllegalStateException.class, () -> StoryDebugLauncher.main(new String[0]));
            return;
        }
        assertEquals(GameplayConfig.NORMAL, GameplayConfig.getDifficulty());
        assertNull(Core.getRecordsService());
        AudioManager.setMuted(true);
        for (StoryRoute route : StoryRoute.values()) {
            for (int stage : new int[]{1, 3, 7}) {
                for (var difficulty : StoryDebugSelection.Difficulty.values()) {
                    ShipType ship = ShipType.values()[difficulty.ordinal()];
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
                    if (stage == 1) {
                        assertNull(boss);
                    } else {
                        assertEquals(choice.progress().bossForStage(stage), boss.getType());
                        if (stage != 7 || route != StoryRoute.HOMECOMING
                                || difficulty != StoryDebugSelection.Difficulty.HARD) continue;
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
        AudioManager.stopBGM();
    }

    private static Object field(GameScreen screen, String name) throws Exception {
        Field field = GameScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }
}
