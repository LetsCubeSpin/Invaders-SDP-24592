package engine;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import audio.AudioManager;
import boss.BossManager;
import entity.Boss;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.Ship;
import entity.ShipType;
import event.DefeatedEnemyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
                {}, {"", "1", "EASY", "STANDARD"}, {"INVALID", "1", "EASY", "STANDARD"},
                {"TRUCE", "0", "EASY", "STANDARD"}, {"TRUCE", "8", "EASY", "STANDARD"},
                {"TRUCE", "3.5", "EASY", "STANDARD"}, {"TRUCE", "3", "EXTREME", "STANDARD"},
                {"TRUCE", "3", "", "STANDARD"}, {"TRUCE", "3", "EASY", null}, {"TRUCE", "3", "EASY", "UNKNOWN"}}) {
            assertThrows(IllegalArgumentException.class, () -> StoryDebugSelection.parse(invalid));
        }
    }

    @Test
    void changeSelectionPrefillsEveryControlAndPreviewsTheEditedBattle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var previous = new StoryDebugSelection(StoryRoute.TRUCE, 7, StoryDebugSelection.Difficulty.EASY,
                    ShipType.STANDARD, new StoryDebugModifiers(true, 15, 0.75, 4.0));
            var picker = StoryDebugLauncher.picker(previous);
            assertEquals(previous, picker.selection().get());
            JPanel fields = (JPanel) picker.panel().getComponent(0);
            select(fields, "Story route", StoryRoute.HYBRID);
            select(fields, "Stage", 3);
            select(fields, "Invincible", "OFF");
            select(fields, "Starting lives", 1);
            select(fields, "Enemy HP multiplier", 2.0);
            select(fields, "Enemy speed multiplier", 0.25);
            var changed = new StoryDebugSelection(StoryRoute.HYBRID, 3, StoryDebugSelection.Difficulty.EASY,
                    ShipType.STANDARD, new StoryDebugModifiers(false, 1, 2.0, 0.25));
            assertEquals(changed, picker.selection().get());
            assertEquals(changed.describe(), ((JTextArea) picker.panel().getComponent(1)).getText());
        });
    }

    private static void select(JPanel fields, String name, Object value) {
        for (var component : fields.getComponents()) {
            if (component instanceof JLabel label && name.equals(label.getText())) {
                ((JComboBox<?>) label.getLabelFor()).setSelectedItem(value);
                return;
            }
        }
        fail("Missing control: " + name);
    }

    @Test
    void acceptsLegacyArgumentsAndIndependentNamedOverrides() {
        String[] original = {"TRUCE", "7", "EASY", "STANDARD"};
        assertEquals(StoryDebugModifiers.defaults(), StoryDebugSelection.parse(original).modifiers());
        List<String> args = new ArrayList<>(List.of(original));
        args.addAll(List.of("--enemy-speed-multiplier", "1.75"));
        var partial = StoryDebugSelection.parse(args.toArray(String[]::new));
        assertEquals(new StoryDebugModifiers(false, ShipType.STANDARD.getMaxLives(), 1.0, 1.75), partial.modifiers());
        args.addAll(List.of("--starting-lives", "99", "--enemy-hp-multiplier", ".25", "--invincible", "on"));
        var choice = StoryDebugSelection.parse(args.toArray(String[]::new));
        assertEquals(new StoryDebugModifiers(true, 99, 0.25, 1.75), choice.modifiers());
        assertEquals(choice, StoryDebugSelection.parse(choice.arguments().toArray(String[]::new)));
    }

    @ParameterizedTest
    @CsvSource({
            "0.25, 0.25", "4.0, 4.0", "1.75, 1.75",
            "0.2500000000000000001, 0.25", "3.99999999999999999, 4.0",
            ".2500, 0.25", "4., 4.0"
    })
    void acceptsInRangeDecimalMultipliers(String input, double expected) {
        var selection = StoryDebugSelection.parse(new String[]{"TRUCE", "7", "EASY", "STANDARD",
                "--enemy-hp-multiplier", input, "--enemy-speed-multiplier", input});
        assertEquals(expected, selection.modifiers().enemyHpMultiplier());
        assertEquals(expected, selection.modifiers().enemySpeedMultiplier());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--invincible yes", "--invincible", "--starting-lives 0", "--starting-lives 100",
            "--starting-lives 1.25", "--enemy-hp-multiplier NaN", "--enemy-hp-multiplier Infinity",
            "--enemy-hp-multiplier 0.249", "--enemy-speed-multiplier 4.001", "--enemy-speed-multiplier ABC",
            "--enemy-hp-multiplier 4.00000000000000001", "--enemy-speed-multiplier 4.00000000000000001",
            "--enemy-hp-multiplier 0.2499999999999999999", "--enemy-speed-multiplier 0.2499999999999999999",
            "--starting-lives 15 --starting-lives 9", "--unknown 3"})
    void rejectsInvalidModifierArguments(String options) {
        var args = new ArrayList<>(List.of("HOMECOMING", "3", "EASY", "STANDARD"));
        args.addAll(List.of(options.split(" ")));
        assertThrows(IllegalArgumentException.class, () -> StoryDebugSelection.parse(args.toArray(String[]::new)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NORMAL", "HARD"})
    void rejectsRemovedDifficultiesWithSupportedValue(String difficulty) {
        String[] fields = {"TRUCE", "7", difficulty, "STANDARD"};
        var failure = assertThrows(IllegalArgumentException.class, () -> StoryDebugSelection.parse(fields));
        assertTrue(failure.getMessage().contains(difficulty));
        assertTrue(failure.getMessage().contains("EASY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SPEEDSTER", "TANK"})
    void rejectsRemovedShipsWithSupportedValue(String ship) {
        String[] fields = {"TRUCE", "7", "EASY", ship};
        var failure = assertThrows(IllegalArgumentException.class, () -> StoryDebugSelection.parse(fields));
        assertTrue(failure.getMessage().contains(ship));
        assertTrue(failure.getMessage().contains("STANDARD"));
    }

    @Test
    void restartResetsPreferencesAndPreservesNormalFiles(@TempDir Path dir) throws Exception {
        // Linux's JDK can create .java/fonts/<version>/fcinfo-*.properties on first use.
        // Establish that baseline using only JDK drawing APIs, before any game code runs.
        child(dir, "fontWarmup");
        System.out.println("JDK-only filesystem baseline: " + snapshotFiles(dir).keySet());
        Path profile = dir.resolve(".space-invaders/profile.properties");
        Files.createDirectories(profile.getParent());
        List<Path> saves = List.of(dir.resolve("scores"), profile);
        for (Path save : saves) Files.writeString(save, "sentinel-" + save.getFileName());
        Map<Path, byte[]> expected = snapshotFiles(dir);
        for (String scenario : List.of("disabled", "normalPreferences", "battle", "battle")) {
            child(dir, scenario);
            assertFilesUnchanged(dir, expected, scenario);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unexpected-save", ".space-invaders/unexpected.properties",
            ".java/fonts/test/fcinfo-unexpected.properties", ".java/.userPrefs/unexpected/prefs.xml"})
    void isolationRejectsAdditionalFiles(String relativePath, @TempDir Path dir) throws Exception {
        Map<Path, byte[]> expected = snapshotFiles(dir);
        Path unexpected = dir.resolve(relativePath);
        Files.createDirectories(unexpected.getParent());
        Files.writeString(unexpected, "unexpected persistence");
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertFilesUnchanged(dir, expected, "additional file"));
        assertTrue(failure.getMessage().contains(Path.of(relativePath).toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"scores",
            ".space-invaders/profile.properties", ".java/fonts/test/fcinfo-existing.properties"})
    void isolationRejectsChangedAndDeletedFiles(String relativePath, @TempDir Path dir) throws Exception {
        Path saved = dir.resolve(relativePath);
        Files.createDirectories(saved.getParent());
        Files.writeString(saved, "original");
        Map<Path, byte[]> expected = snapshotFiles(dir);
        Files.writeString(saved, "modified");
        assertThrows(AssertionError.class, () -> assertFilesUnchanged(dir, expected, "changed file"));
        Files.delete(saved);
        assertThrows(AssertionError.class, () -> assertFilesUnchanged(dir, expected, "deleted file"));
        // A deletion and a new save must fail even if the total file count is unchanged.
        Files.writeString(dir.resolve("replacement-save"), "original");
        assertThrows(AssertionError.class, () -> assertFilesUnchanged(dir, expected, "replaced file"));
    }

    private static Map<Path, byte[]> snapshotFiles(Path dir) throws IOException {
        Map<Path, byte[]> snapshot = new TreeMap<>();
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path relative = dir.relativize(path);
                // Only the known process-output file may change; no directory is excluded.
                if (!relative.equals(Path.of("child.log"))) snapshot.put(relative, Files.readAllBytes(path));
            }
        }
        return snapshot;
    }

    private static void assertFilesUnchanged(Path dir, Map<Path, byte[]> expected, String scenario)
            throws IOException {
        Map<Path, byte[]> actual = snapshotFiles(dir);
        assertEquals(expected.keySet(), actual.keySet(), scenario + ": unexpected or missing files");
        for (var entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue(), actual.get(entry.getKey()), scenario + ": changed " + entry.getKey());
        }
    }

    @Test
    void latestMainBossCCompletesItsSummonLifecycle(@TempDir Path dir) throws Exception {
        child(dir, "bossC");
    }

    @Test
    void debugAndNormalStoryStartWithSameEquipment(@TempDir Path dir) throws Exception {
        child(dir, "loadout");
    }

    private static void child(Path dir, String scenario) throws Exception {
        String cp = System.getProperty("invaders.test.classpath", System.getProperty("java.class.path"));
        String absoluteCp = String.join(File.pathSeparator, Arrays.stream(cp.split(File.pathSeparator))
                .map(path -> Path.of(path).toAbsolutePath().toString()).toList());
        var args = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Duser.home=" + dir, "-Dinvaders.storageDir=" + dir));
        String coverageAgent = System.getProperty("invaders.test.coverageAgent");
        if (coverageAgent != null && !coverageAgent.isBlank()) {
            Matcher destination = Pattern.compile("destfile=([^,]+)").matcher(coverageAgent);
            if (!destination.find()) throw new IllegalStateException("JaCoCo agent must specify a coverage destination.");
            Path coverageFile = Path.of(destination.group(1)).toAbsolutePath().normalize();
            String childDestination = dir.toAbsolutePath().normalize().relativize(coverageFile).toString();
            // Match the other isolated test JVMs: relative destinations avoid JDK 17's Korean-path encoding issue.
            args.add(destination.replaceFirst(Matcher.quoteReplacement("destfile=" + childDestination)));
        }
        if (!scenario.equals("disabled")) args.add("-Dinvaders.storyDebug=true");
        if (!scenario.equals("normalPreferences"))
            args.add("-Djava.util.prefs.PreferencesFactory=" + PreviewPreferencesFactory.class.getName());
        args.addAll(List.of("-cp", absoluteCp, StoryDebugLauncherTest.class.getName(), scenario));
        Path log = dir.resolve("child.log");
        Process p = new ProcessBuilder(args).directory(dir.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            assertTrue(p.waitFor(45, TimeUnit.SECONDS), "Story debug subprocess timed out");
            assertEquals(0, p.exitValue(), Files.readString(log, java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            if (p.isAlive()) p.destroyForcibly().waitFor();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("fontWarmup")) {
            var graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
            try {
                assertTrue(graphics.getFontMetrics(new Font(Font.DIALOG, Font.PLAIN, 12)).stringWidth("Story") > 0);
            } finally {
                graphics.dispose();
            }
            return;
        }
        if (args[0].equals("loadout")) {
            StoryDebugLauncher.requireDebugSession();
            checkLoadoutParity();
            return;
        }
        if (args[0].equals("bossC")) {
            StoryDebugLauncher.requireDebugSession();
            checkBossC();
            return;
        }
        if (!args[0].equals("battle")) {
            assertThrows(IllegalStateException.class, () -> StoryDebugLauncher.main(new String[0]));
            return;
        }
        assertEquals(GameplayConfig.EASY, GameplayConfig.getDifficulty());
        assertNull(Core.getRecordsService());
        AudioManager.setMuted(true);
        int initializedStages = 0;
        for (StoryRoute route : StoryRoute.values()) {
            for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
                for (var difficulty : StoryDebugSelection.Difficulty.values()) {
                    for (ShipType ship : ShipType.values()) {
                        var choice = new StoryDebugSelection(route, stage, difficulty, ship);
                        GameScreen screen = StoryDebugLauncher.createScreen(choice, 448, 520);
                        screen.initialize();
                        initializedStages++;
                        assertNull(screen.getActiveRunId());
                        assertEquals(stage, screen.getGameState().getLevel());
                        assertEquals(ship, ((Ship) field(screen, "ship")).getShipType());
                        assertEquals(ship.getMaxLives(), screen.getGameState().getLivesRemaining());
                        assertEquals(difficulty.setting, field(screen, "difficulty"));
                        StoryStage data = StoryStageData.get(stage, choice.progress());
                        assertSame(data, field(screen, "storyStage"));
                        double multiplier = switch (difficulty) {
                            case EASY -> 1.25;
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
                            if (stage != 7 || route != StoryRoute.HOMECOMING) continue;
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
        assertEquals(28, initializedStages);
        System.out.println("PASS: " + initializedStages + " actual Story initializations, all non-boss waves, isolated restart.");
        AudioManager.stopBGM();
    }

    private static void checkLoadoutParity() throws Exception {
        AudioManager.setMuted(true);
        int comparisons = 0;
        for (StoryRoute route : StoryRoute.values()) {
            for (int stage = 1; stage <= StoryProgress.TOTAL_STAGES; stage++) {
                for (ShipType type : ShipType.values()) {
                    var choice = new StoryDebugSelection(route, stage, StoryDebugSelection.Difficulty.EASY, type);
                    GameScreen debug = StoryDebugLauncher.createScreen(choice, 448, 520);
                    debug.initialize();
                    // Reproduce normal Story's answers independently of the debug selection helper.
                    StoryProgress progress = new StoryProgress();
                    if (stage > StoryProgress.FIRST_QUESTION_STAGE) progress.chooseFirst(route.getFirstChoice());
                    if (stage > StoryProgress.MID_BOSS_STAGE) progress.chooseSecond(route.getSecondChoice());
                    GameScreen normal = GameScreen.forStoryStage(
                            new GameState(stage, 0, type.getMaxLives(), 0, 0), progress,
                            settings -> Core.applyDifficulty(settings, GameplayConfig.getMultiplier()),
                            false, 448, 520, 60, type, null);
                    normal.initialize();
                    Ship actual = (Ship) field(debug, "ship");
                    Ship expected = (Ship) field(normal, "ship");
                    assertEquals(expected.getCurrentBulletType(), actual.getCurrentBulletType(), choice.toString());
                    assertEquals(expected.getGrenadeCount(), actual.getGrenadeCount(), choice.toString());
                    comparisons++;
                }
            }
        }
        assertEquals(28, comparisons);
        // Parity is distinct from satisfying weaponForStage/givesGrenadesOnStage; those integrations remain pending.
        System.out.println("PASS: " + comparisons + " Debug/normal Story starting-equipment comparisons.");
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
