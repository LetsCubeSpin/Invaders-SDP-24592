package engine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import audio.AudioManager;
import entity.Boss;
import entity.ShipType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;
import screen.GameScreen;
import story.StoryRoute;

@Timeout(45)
class StoryDebugControllerTest {
    private static final StoryDebugSelection FIRST = new StoryDebugSelection(
            StoryRoute.HYBRID, 3, StoryDebugSelection.Difficulty.EASY, ShipType.STANDARD,
            new StoryDebugModifiers(true, 15, 2.0, 0.5));
    private static final StoryDebugSelection SECOND = new StoryDebugSelection(
            StoryRoute.TRUCE, 7, StoryDebugSelection.Difficulty.EASY, ShipType.STANDARD,
            new StoryDebugModifiers(false, 99, 0.5, 4.0));

    private static final class View implements StoryDebugController.View {
        final List<StoryDebugSelection> previous = new ArrayList<>();
        final List<StoryDebugController.Result> results = new ArrayList<>();
        final List<StoryDebugSelection> choices;
        final List<StoryDebugController.Action> actions;
        int selections;

        View(List<StoryDebugSelection> choices, StoryDebugController.Action... actions) {
            this.choices = choices;
            this.actions = List.of(actions);
        }

        public StoryDebugSelection choose(StoryDebugSelection last) {
            previous.add(last);
            return selections < choices.size() ? choices.get(selections++) : null;
        }

        public StoryDebugController.Action afterStage(StoryDebugSelection selection, StoryDebugController.Result result) {
            results.add(result);
            return actions.get(results.size() - 1);
        }
    }

    @Test
    void replaysThenChangesRouteAndStageWhileKeepingTheLastSelection() throws Exception {
        View view = new View(List.of(FIRST, SECOND), StoryDebugController.Action.REPLAY,
                StoryDebugController.Action.CHANGE, StoryDebugController.Action.EXIT);
        List<StoryDebugSelection> runs = new ArrayList<>();
        assertEquals(0, StoryDebugController.runLoop(view, selection -> {
            runs.add(selection);
            return new StoryDebugController.Result(0, "ended");
        }, FIRST));
        assertEquals(List.of(FIRST, FIRST, SECOND), runs);
        assertEquals(List.of(FIRST, FIRST), view.previous);
    }

    @Test
    void returningFromABossBattleOpensPreviousSettingsAndStartsAFreshStage(@TempDir Path dir) throws Exception {
        View view = new View(List.of(FIRST, SECOND), StoryDebugController.Action.EXIT);
        Path normalSave = dir.resolve("scores");
        Files.writeString(normalSave, "normal progress");
        List<List<String>> battles = new ArrayList<>();
        assertEquals(0, StoryDebugController.runLoop(view, selection -> {
            boolean returning = battles.isEmpty();
            int expected = returning ? GameScreen.RETURN_TO_STORY_DEBUG : 0;
            Path evidence = dir.resolve("child-" + battles.size() + ".txt");
            Path log = dir.resolve("child-" + battles.size() + ".log");
            try (StoryDebugSession session = new StoryDebugSession(dir)) {
                List<String> command = fixtureCommand(session, selection, evidence, returning ? "return" : "write", 0);
                int exit = session.runCommand(command, ProcessBuilder.Redirect.to(log.toFile()));
                assertChildExit(expected, exit, log);
                battles.add(Files.readAllLines(evidence));
                return new StoryDebugController.Result(exit, "stage result");
            }
        }, FIRST));
        assertEquals(List.of(FIRST, FIRST), view.previous);
        assertEquals(List.of(0), view.results.stream().map(StoryDebugController.Result::exitCode).toList());
        assertEquals(2, battles.size());
        assertEquals(FIRST.toString(), battles.get(0).get(2));
        assertEquals(SECOND.toString(), battles.get(1).get(2));
        assertNotEquals(battles.get(0).get(0), battles.get(1).get(0), "Separate JVMs");
        assertNotEquals(battles.get(0).get(1), battles.get(1).get(1), "Separate session directories");
        for (List<String> battle : battles) assertFalse(Files.exists(Path.of(battle.get(1))));
        assertEquals("normal progress", Files.readString(normalSave));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void cliStageNormallyExitsWithoutDialogs(int status) throws Exception {
        View view = new View(List.of());
        assertEquals(status, StoryDebugController.runLoop(view, selection -> {
            assertEquals(FIRST, selection);
            return new StoryDebugController.Result(status, "ended");
        }, FIRST, true));
        assertTrue(view.previous.isEmpty());
        assertTrue(view.results.isEmpty());
    }

    @Test
    void returningFromCliPromotesToInteractiveReplayAndChange() throws Exception {
        View view = new View(List.of(SECOND), StoryDebugController.Action.REPLAY, StoryDebugController.Action.EXIT);
        List<StoryDebugSelection> runs = new ArrayList<>();
        assertEquals(0, StoryDebugController.runLoop(view, selection -> {
            runs.add(selection);
            int code = runs.size() == 1 ? GameScreen.RETURN_TO_STORY_DEBUG : 0;
            return new StoryDebugController.Result(code, "stage result");
        }, FIRST, true));
        assertEquals(List.of(FIRST), view.previous);
        assertEquals(List.of(FIRST, SECOND, SECOND), runs);
        assertEquals(2, view.results.size());
    }

    @Test
    void cancelAfterReturningExitsSuccessfully() throws Exception {
        View view = new View(List.of());
        assertEquals(0, StoryDebugController.runLoop(view,
                selection -> new StoryDebugController.Result(GameScreen.RETURN_TO_STORY_DEBUG, "returned"),
                FIRST, true));
        assertEquals(List.of(FIRST), view.previous);
        assertTrue(view.results.isEmpty());
    }

    @Test
    void cancelBeforeFirstBattleStartsNothing() throws Exception {
        assertEquals(0, StoryDebugController.runLoop(new View(List.of()), selection -> {
            fail("Cancellation must not launch a battle");
            return null;
        }, FIRST));
    }

    @Test
    void cancelWhileChangingExitsWithoutRepeating() throws Exception {
        View view = new View(List.of(SECOND), StoryDebugController.Action.CHANGE);
        assertEquals(0, StoryDebugController.runLoop(view,
                selection -> new StoryDebugController.Result(0, "ended"), FIRST));
        assertEquals(1, view.results.size());
        assertEquals(List.of(FIRST, SECOND), view.previous);
    }

    @Test
    void failedStageRequiresAnExplicitChoiceAndRetainsItsExitStatus() throws Exception {
        View view = new View(List.of(FIRST), StoryDebugController.Action.EXIT);
        assertEquals(7, StoryDebugController.runLoop(view,
                selection -> new StoryDebugController.Result(7, "failed"), FIRST));
        assertEquals(1, view.results.size());
    }

    @Test
    void launchOrCleanupFailureCanBeRetriedExplicitly() throws Exception {
        View view = new View(List.of(FIRST), StoryDebugController.Action.REPLAY, StoryDebugController.Action.EXIT);
        int[] attempts = {0};
        assertEquals(0, StoryDebugController.runLoop(view, selection -> {
            if (attempts[0]++ == 0) throw new IOException("fixture failure");
            return new StoryDebugController.Result(0, "ended");
        }, FIRST));
        assertEquals(List.of(1, 0), view.results.stream().map(StoryDebugController.Result::exitCode).toList());
        assertTrue(view.results.get(0).message().contains("fixture failure"));
    }

    @Test
    void interruptionDoesNotOpenAnotherDialogOrLaunchAnotherStage() throws Exception {
        View view = new View(List.of(FIRST));
        try {
            assertEquals(130, StoryDebugController.runLoop(view, selection -> {
                throw new InterruptedException();
            }, FIRST));
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(view.results.isEmpty());
        } finally { Thread.interrupted(); }
    }

    @Test
    void eachStageGetsFreshJvmPreferencesAndFilesAndCleansOnFailure(@TempDir Path dir) throws Exception {
        Path root = Files.createDirectory(dir.resolve("sessions with spaces 'quote; [x]"));
        List<Path> normalFiles = List.of(dir.resolve("scores"),
                dir.resolve(".space-invaders/profile.properties"));
        for (Path file : normalFiles) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "normal-sentinel");
        }
        var directories = new HashSet<Path>();
        var pids = new HashSet<String>();
        for (int i = 0; i < 3; i++) {
            Path evidence = dir.resolve("child-" + i + ".txt");
            int status = i == 1 ? 7 : 0;
            StoryDebugSelection choice = i == 2 ? SECOND : FIRST;
            Path sessionPath;
            String workingPath;
            try (StoryDebugSession session = new StoryDebugSession(root)) {
                sessionPath = session.directory();
                workingPath = sessionPath.toRealPath().toString();
                assertTrue(directories.add(sessionPath));
                List<String> command = fixtureCommand(session, choice, evidence, "write", status);
                Path log = dir.resolve("child.log");
                int exit = session.runCommand(command, ProcessBuilder.Redirect.to(log.toFile()));
                assertChildExit(status, exit, log);
                assertTrue(Files.exists(sessionPath.resolve("scores")));
            }
            assertFalse(Files.exists(sessionPath));
            List<String> observation = Files.readAllLines(evidence);
            assertTrue(pids.add(observation.get(0)), "Each run must use a new process");
            assertEquals(workingPath, observation.get(1));
            assertEquals(choice.toString(), observation.get(2));
        }
        for (Path file : normalFiles) assertEquals("normal-sentinel", Files.readString(file));
        try (var paths = Files.list(root)) { assertEquals(0, paths.count()); }
    }

    private static void assertChildExit(int expected, int actual, Path log) {
        assertEquals(expected, actual, () -> readChildLog(log));
    }

    private static String readChildLog(Path log) {
        try {
            byte[] bytes = Files.readAllBytes(log);
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException invalidUtf8) {
                return new String(bytes, Charset.forName("MS949"));
            }
        } catch (IOException | SecurityException error) {
            return "Could not read " + log + ": " + error;
        }
    }

    @Test
    void matchingExitAcceptsInvalidUtf8(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("child.log");
        Files.write(log, new byte[] {(byte) 0xff});
        assertChildExit(0, 0, log);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "MS949"})
    void exitMismatchIncludesDecodedLog(String encoding, @TempDir Path dir) throws IOException {
        Path log = dir.resolve("child.log");
        String message = "11월 전투 종료";
        Files.write(log, message.getBytes(Charset.forName(encoding)));
        AssertionFailedError failure = assertThrows(AssertionFailedError.class, () -> assertChildExit(0, 7, log));
        assertTrue(failure.getMessage().contains(message));
        assertEquals(0, failure.getExpected().getValue());
        assertEquals(7, failure.getActual().getValue());
    }

    @Test
    void exitMismatchSurvivesMissingLog(@TempDir Path dir) {
        Path log = dir.resolve("missing.log");
        AssertionFailedError failure = assertThrows(AssertionFailedError.class, () -> assertChildExit(7, 0, log));
        assertTrue(failure.getMessage().contains(log.toString()));
        assertEquals(7, failure.getExpected().getValue());
        assertEquals(0, failure.getActual().getValue());
    }

    @Test
    void failedProcessStartStillCleansItsOwnedDirectory(@TempDir Path dir) throws Exception {
        Path sessionPath;
        try (StoryDebugSession session = new StoryDebugSession(dir)) {
            sessionPath = session.directory();
            assertThrows(IOException.class, () -> session.runCommand(
                    List.of(dir.resolve("missing-java").toString()), ProcessBuilder.Redirect.INHERIT));
        }
        assertFalse(Files.exists(sessionPath));
    }

    @Test
    void relativeClasspathEntriesRemainRelativeWhenWorkingDirectoryChanges() {
        String before = System.getProperty("invaders.test.classpath");
        try {
            System.setProperty("invaders.test.classpath", "classes" + java.io.File.pathSeparator + "resources");
            String expected = Path.of("..", "classes") + java.io.File.pathSeparator + Path.of("..", "resources");
            assertEquals(expected, StoryDebugSession.childClasspath(Path.of("").toAbsolutePath().resolve("stage-test")));
        } finally {
            if (before == null) System.clearProperty("invaders.test.classpath");
            else System.setProperty("invaders.test.classpath", before);
        }
    }

    @Test
    void supervisorShutdownHookStopsItsRunningBattle(@TempDir Path dir) throws Exception {
        Path evidence = dir.resolve("shutdown.txt");
        try (StoryDebugSession supervisor = new StoryDebugSession(dir)) {
            assertEquals(0, supervisor.runCommand(fixtureCommand(supervisor, FIRST, evidence, "shutdown", 0),
                    ProcessBuilder.Redirect.to(dir.resolve("shutdown.log").toFile())));
            List<String> child = Files.readAllLines(evidence.resolveSibling("nested-child.txt"));
            assertFalse(ProcessHandle.of(Long.parseLong(child.get(0))).map(ProcessHandle::isAlive).orElse(false));
            assertFalse(Files.exists(Path.of(child.get(1))), "Shutdown hook must clean the battle directory");
        }
    }

    @Test
    void closingALiveSessionStopsItsChildBeforeDeletingFiles(@TempDir Path dir) throws Exception {
        Path evidence = dir.resolve("running.txt");
        StoryDebugSession session = new StoryDebugSession(dir);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread runner = new Thread(() -> {
            try {
                session.runCommand(fixtureCommand(session, FIRST, evidence, "wait", 0),
                        ProcessBuilder.Redirect.to(dir.resolve("child.log").toFile()));
            } catch (Throwable error) { failure.set(error); }
        });
        try {
            runner.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!Files.exists(evidence) && runner.isAlive() && System.nanoTime() < deadline) Thread.sleep(25);
            assertTrue(Files.exists(evidence), "Child did not start");
            long pid = Long.parseLong(Files.readAllLines(evidence).get(0));
            session.close();
            runner.join(5000);
            assertFalse(runner.isAlive());
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
            assertFalse(Files.exists(session.directory()));
            assertNull(failure.get());
        } finally { session.close(); runner.join(5000); }
    }

    private static List<String> fixtureCommand(StoryDebugSession session, StoryDebugSelection selection,
            Path evidence, String mode, int status) {
        List<String> command = session.command(selection);
        int entry = command.indexOf(StoryDebugLauncher.Battle.class.getName());
        assertTrue(entry > 0);
        command.set(entry, Fixture.class.getName());
        command.add(evidence.toString());
        command.add(mode);
        command.add(Integer.toString(status));
        return command;
    }

    public static class Fixture {
        // Exercise the same screen factory as the battle entry point, in the actual child JVM.
        private static void checkBattle(StoryDebugSelection choice) throws Exception {
            AudioManager.setMuted(true);
            var modifiers = choice.modifiers();
            GameScreen battle = StoryDebugLauncher.createScreen(choice, 448, 520);
            battle.initialize();
            assertEquals(0, battle.getGameState().getScore());
            assertEquals(modifiers.startingLives(), battle.getGameState().getLivesRemaining());
            assertEquals(modifiers.startingLives(), battle.getPlayerStatus().getMaxLives());
            var getBoss = GameScreen.class.getDeclaredMethod("getBossOnScreen");
            getBoss.setAccessible(true);
            Boss boss = (Boss) getBoss.invoke(battle);
            assertEquals(choice.progress().bossForStage(choice.stage()), boss.getType());
            assertEquals(Math.round(boss.getType().getMaxHealth() * modifiers.enemyHpMultiplier()), boss.getCurrentHealth());
            assertEquals(boss.getMaxHealth(), boss.getCurrentHealth());
            int startX = boss.getPositionX();
            for (int i = 0; i < 8; i++) boss.update(0, 448);
            assertEquals((int) (8 * boss.getType().getSpeed() * modifiers.enemySpeedMultiplier()),
                    boss.getPositionX() - startX);
            var hitBoss = GameScreen.class.getDeclaredMethod("damageBoss", int.class);
            hitBoss.setAccessible(true);
            hitBoss.invoke(battle, boss.getMaxHealth() * 10);
            assertTrue(boss.isDefeated());
            assertTrue(battle.getGameState().getScore() > 0);
            var damage = GameScreen.class.getDeclaredMethod("damagePlayer", int.class, String.class);
            damage.setAccessible(true);
            assertEquals(!modifiers.invincible(), damage.invoke(battle, 1, "debug child test"));
            int expectedLives = modifiers.startingLives() - (modifiers.invincible() ? 0 : 1);
            assertEquals(expectedLives, battle.getGameState().getLivesRemaining());
            AudioManager.stopBGM();
        }

        public static void main(String[] args) throws Exception {
            // Last three arguments belong to this fixture; all preceding arguments came from the session.
            int tail = args.length - 3;
            StoryDebugSelection choice = StoryDebugSelection.parse(java.util.Arrays.copyOf(args, tail));
            String mode = args[tail + 1];
            StoryDebugLauncher.requireDebugSession();
            Path cwd = Path.of("").toRealPath();
            for (String property : List.of("user.home", "invaders.storageDir", "java.io.tmpdir"))
                if (!cwd.equals(Path.of(System.getProperty(property)).toRealPath())) throw new AssertionError(property);
            if (!Preferences.userRoot().get("repeat-test", "fresh").equals("fresh")) throw new AssertionError("Old preferences");
            Preferences.userRoot().put("repeat-test", "used");
            int status = Integer.parseInt(args[tail + 2]);
            if (mode.equals("write")) checkBattle(choice);
            if (mode.equals("return")) {
                AudioManager.setMuted(true);
                GameScreen battle = StoryDebugLauncher.createScreen(choice, 448, 520, () -> true);
                battle.initialize();
                status = battle.run();
                assertEquals(0, battle.getGameState().getScore());
                assertEquals(choice.modifiers().startingLives(), battle.getGameState().getLivesRemaining());
                AudioManager.stopBGM();
            }
            Files.writeString(cwd.resolve("scores"), "stage-only");
            Path evidence = Path.of(args[tail]);
            Path pending = evidence.resolveSibling(evidence.getFileName() + ".tmp");
            Files.writeString(pending, ProcessHandle.current().pid() + "\n" + cwd + "\n" + choice + "\n");
            Files.move(pending, evidence);
            if (mode.equals("shutdown")) {
                Path nestedEvidence = evidence.resolveSibling("nested-child.txt");
                StoryDebugSession nested = new StoryDebugSession(cwd);
                Thread runner = new Thread(() -> {
                    try {
                        nested.runCommand(fixtureCommand(nested, FIRST, nestedEvidence, "wait", 0),
                                ProcessBuilder.Redirect.INHERIT);
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                });
                runner.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!Files.exists(nestedEvidence) && System.nanoTime() < deadline) Thread.sleep(25);
                if (!Files.exists(nestedEvidence)) throw new AssertionError("Nested child did not start");
                // Deliberately skip close(): this is the supervisor-shutdown path under test.
                System.exit(0);
            }
            if (mode.equals("wait")) Thread.sleep(60000);
            System.exit(status);
        }
    }
}
