package engine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import entity.ShipType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import story.StoryRoute;

@Timeout(45)
class StoryDebugControllerTest {
    private static final StoryDebugSelection FIRST = new StoryDebugSelection(
            StoryRoute.HYBRID, 1, StoryDebugSelection.Difficulty.NORMAL, ShipType.STANDARD);
    private static final StoryDebugSelection SECOND = new StoryDebugSelection(
            StoryRoute.TRUCE, 7, StoryDebugSelection.Difficulty.HARD, ShipType.SPEEDSTER);

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
    void repeatsAndChangesAllFieldsWhileKeepingTheLastSelection() throws Exception {
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
        List<Path> normalFiles = List.of(dir.resolve("scores"), dir.resolve(FileManager.ASSETS_FILENAME),
                dir.resolve(".space-invaders/records-achievements.properties"));
        for (Path file : normalFiles) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "normal-sentinel");
        }
        var directories = new HashSet<Path>();
        var pids = new HashSet<String>();
        for (int i = 0; i < 3; i++) {
            Path evidence = dir.resolve("child-" + i + ".txt");
            int status = i == 1 ? 7 : 0;
            Path sessionPath;
            String workingPath;
            try (StoryDebugSession session = new StoryDebugSession(root)) {
                sessionPath = session.directory();
                workingPath = sessionPath.toRealPath().toString();
                assertTrue(directories.add(sessionPath));
                List<String> command = fixtureCommand(session, i == 2 ? SECOND : FIRST, evidence, "write", status);
                assertEquals(status, session.runCommand(command, ProcessBuilder.Redirect.to(dir.resolve("child.log").toFile())));
                assertTrue(Files.exists(sessionPath.resolve("scores")));
            }
            assertFalse(Files.exists(sessionPath));
            List<String> observation = Files.readAllLines(evidence);
            assertTrue(pids.add(observation.get(0)), "Each run must use a new process");
            assertEquals(workingPath, observation.get(1));
        }
        for (Path file : normalFiles) assertEquals("normal-sentinel", Files.readString(file));
        try (var paths = Files.list(root)) { assertEquals(0, paths.count()); }
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
        int entry = command.indexOf(StoryDebugLauncher.class.getName());
        assertTrue(entry > 0);
        command.set(entry, Fixture.class.getName());
        command.add(evidence.toString());
        command.add(mode);
        command.add(Integer.toString(status));
        return command;
    }

    public static class Fixture {
        public static void main(String[] args) throws Exception {
            StoryDebugSelection.parse(java.util.Arrays.copyOf(args, 4));
            StoryDebugLauncher.requireDebugSession();
            Path cwd = Path.of("").toRealPath();
            for (String property : List.of("user.home", "invaders.storageDir", "java.io.tmpdir"))
                if (!cwd.equals(Path.of(System.getProperty(property)).toRealPath())) throw new AssertionError(property);
            if (!Preferences.userRoot().get("repeat-test", "fresh").equals("fresh")) throw new AssertionError("Old preferences");
            Preferences.userRoot().put("repeat-test", "used");
            Files.writeString(cwd.resolve("scores"), "stage-only");
            Path evidence = Path.of(args[4]);
            Path pending = evidence.resolveSibling(evidence.getFileName() + ".tmp");
            Files.writeString(pending, ProcessHandle.current().pid() + "\n" + cwd + "\n");
            Files.move(pending, evidence);
            if (args[5].equals("shutdown")) {
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
            if (args[5].equals("wait")) Thread.sleep(60000);
            System.exit(Integer.parseInt(args[6]));
        }
    }
}
